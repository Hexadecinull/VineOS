#include "vine_utils.h"
#include "vine_log.h"

#include <cstring>
#include <cerrno>
#include <fstream>
#include <sstream>
#include <sys/stat.h>
#include <sys/wait.h>
#include <sys/sysinfo.h>
#include <unistd.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/system_properties.h>
#include <dirent.h>

namespace vine {

// ─── Filesystem helpers ───────────────────────────────────────────────────────

bool mkdirs(const std::string& path, mode_t mode) {
    if (path.empty()) return false;
    if (path_exists(path)) return is_directory(path);

    // Walk and create each component
    std::string current;
    for (char ch : path) {
        current += ch;
        if (ch == '/') {
            if (current.size() > 1) {
                if (mkdir(current.c_str(), mode) != 0 && errno != EEXIST) {
                    VINE_LOGE_ERRNO(("mkdirs: mkdir(" + current + ")").c_str());
                    return false;
                }
            }
        }
    }
    // Create the final component
    if (mkdir(path.c_str(), mode) != 0 && errno != EEXIST) {
        VINE_LOGE_ERRNO(("mkdirs: final mkdir(" + path + ")").c_str());
        return false;
    }
    return true;
}

bool path_exists(const std::string& path) {
    struct stat st{};
    return stat(path.c_str(), &st) == 0;
}

bool is_directory(const std::string& path) {
    struct stat st{};
    return stat(path.c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

std::optional<std::string> read_file(const std::string& path) {
    std::ifstream f(path, std::ios::in | std::ios::binary);
    if (!f.is_open()) {
        VINE_LOGE("read_file: failed to open %s", path.c_str());
        return std::nullopt;
    }
    std::ostringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

bool write_file(const std::string& path, const std::string& contents) {
    // Use low-level write so we can write to /proc/sys files (O_TRUNC breaks on those)
    int fd = open(path.c_str(), O_WRONLY | O_CREAT, 0644);
    if (fd < 0) {
        VINE_LOGE_ERRNO(("write_file: open(" + path + ")").c_str());
        return false;
    }
    ssize_t written = write(fd, contents.data(), contents.size());
    close(fd);
    if (written != (ssize_t)contents.size()) {
        VINE_LOGE("write_file: short write to %s (%zd/%zu bytes)",
                  path.c_str(), written, contents.size());
        return false;
    }
    return true;
}

ssize_t file_size(const std::string& path) {
    struct stat st{};
    if (stat(path.c_str(), &st) != 0) return -1;
    return (ssize_t)st.st_size;
}

// ─── Process helpers ──────────────────────────────────────────────────────────

static pid_t do_fork_exec(const std::vector<std::string>& args) {
    if (args.empty()) return -1;

    // Build argv array
    std::vector<const char*> argv;
    argv.reserve(args.size() + 1);
    for (const auto& a : args) argv.push_back(a.c_str());
    argv.push_back(nullptr);

    pid_t pid = fork();
    if (pid < 0) {
        VINE_LOGE_ERRNO("fork");
        return -1;
    }
    if (pid == 0) {
        // Child
        execv(argv[0], const_cast<char* const*>(argv.data()));
        // execv only returns on failure
        VINE_LOGE_ERRNO(("execv(" + args[0] + ")").c_str());
        _exit(127);
    }
    return pid; // Parent returns child PID
}

int exec_wait(const std::vector<std::string>& args) {
    pid_t pid = do_fork_exec(args);
    if (pid < 0) return -1;

    int status = 0;
    if (waitpid(pid, &status, 0) < 0) {
        VINE_LOGE_ERRNO("waitpid");
        return -1;
    }
    return WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

pid_t exec_async(const std::vector<std::string>& args) {
    return do_fork_exec(args);
}

bool terminate_gracefully(pid_t pid, int timeout_ms) {
    if (pid <= 0) return false;

    // Check if already dead
    if (kill(pid, 0) != 0) return true;

    // Send SIGTERM
    if (kill(pid, SIGTERM) != 0) {
        VINE_LOGE_ERRNO("terminate_gracefully: kill(SIGTERM)");
        return false;
    }

    // Poll until dead or timeout
    const int poll_interval_ms = 100;
    int elapsed = 0;
    while (elapsed < timeout_ms) {
        usleep(poll_interval_ms * 1000);
        elapsed += poll_interval_ms;
        int status = 0;
        pid_t result = waitpid(pid, &status, WNOHANG);
        if (result == pid) return true;  // Exited cleanly
        if (result < 0) break;
    }

    // Timed out — send SIGKILL
    VINE_LOGW("terminate_gracefully: pid %d didn't exit in %dms, sending SIGKILL", pid, timeout_ms);
    kill(pid, SIGKILL);
    waitpid(pid, nullptr, 0);
    return false;
}

// ─── CPU / ABI detection ─────────────────────────────────────────────────────

bool host_supports_aarch32() {
    // Method 1: Check /proc/cpuinfo for "aarch32_el0" CPU feature
    // This is the most reliable indicator on Linux 4.7+
    auto cpuinfo = read_file("/proc/cpuinfo");
    if (cpuinfo.has_value()) {
        if (cpuinfo->find("aarch32_el0") != std::string::npos) {
            VINE_LOGI("host_supports_aarch32: found aarch32_el0 in cpuinfo → supported");
            return true;
        }
    }

    // Method 2: Check /proc/sys/abi/cp15_barrier_emulation
    // This file only exists when the kernel was built with 32-bit compat layer.
    // Not definitive alone (could be missing for other reasons), but useful signal.
    if (path_exists("/proc/sys/abi/cp15_barrier_emulation")) {
        VINE_LOGI("host_supports_aarch32: cp15_barrier_emulation exists → supported");
        return true;
    }

    // Method 3: Try to spawn a minimal ARMv7 ELF and see if the kernel executes it.
    // TODO: bundle a tiny static test binary and execute it here.
    // For now, fall back to reading ro.product.cpu.abilist via system property.

    // Method 4: Check ro.product.cpu.abilist (available without root)
    char abi_list[PROP_VALUE_MAX] = {};
    __system_property_get("ro.product.cpu.abilist", abi_list);
    std::string abi_str(abi_list);
    VINE_LOGI("host_supports_aarch32: ro.product.cpu.abilist = %s", abi_list);

    if (abi_str.find("armeabi-v7a") != std::string::npos ||
        abi_str.find("armeabi") != std::string::npos) {
        return true;
    }

    VINE_LOGI("host_supports_aarch32: no AArch32 support detected → QEMU mode required");
    return false;
}

std::vector<std::string> host_abi_list() {
    char abi_list[PROP_VALUE_MAX] = {};
    __system_property_get("ro.product.cpu.abilist", abi_list);
    std::vector<std::string> result;
    std::istringstream ss(abi_list);
    std::string token;
    while (std::getline(ss, token, ',')) {
        if (!token.empty()) result.push_back(token);
    }
    return result;
}

long available_ram_mb() {
    struct sysinfo info{};
    if (sysinfo(&info) != 0) {
        VINE_LOGE_ERRNO("sysinfo");
        return -1;
    }
    // sysinfo.freeram is in bytes; multiply by unit for total bytes
    long free_bytes = (long)info.freeram * (long)info.mem_unit;
    return free_bytes / (1024 * 1024);
}

} // namespace vine
