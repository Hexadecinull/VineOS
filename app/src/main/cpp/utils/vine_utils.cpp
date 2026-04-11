#include "vine_utils.h"
#include "vine_log.h"

#include <cerrno>
#include <cstring>
#include <cstdlib>
#include <fstream>
#include <sstream>
#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#include <signal.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <sys/sysinfo.h>
#include <sys/system_properties.h>

namespace vine {

bool mkdirs(const std::string& path, mode_t mode) {
    if (path.empty()) return false;
    if (path_exists(path)) return is_directory(path);

    std::string current;
    for (char ch : path) {
        current += ch;
        if (ch == '/' && current.size() > 1) {
            if (mkdir(current.c_str(), mode) != 0 && errno != EEXIST) {
                VINE_LOGE_ERRNO(("mkdirs: mkdir(" + current + ")").c_str());
                return false;
            }
        }
    }
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
    if (!f.is_open()) return std::nullopt;
    std::ostringstream ss;
    ss << f.rdbuf();
    return ss.str();
}

bool write_file(const std::string& path, const std::string& contents) {
    int fd = open(path.c_str(), O_WRONLY | O_CREAT, 0644);
    if (fd < 0) {
        VINE_LOGE_ERRNO(("write_file: open(" + path + ")").c_str());
        return false;
    }
    ssize_t written = write(fd, contents.data(), contents.size());
    close(fd);
    return written == (ssize_t)contents.size();
}

ssize_t file_size(const std::string& path) {
    struct stat st{};
    return stat(path.c_str(), &st) == 0 ? (ssize_t)st.st_size : -1;
}

static pid_t do_fork_exec(const std::vector<std::string>& args) {
    if (args.empty()) return -1;
    std::vector<const char*> argv;
    argv.reserve(args.size() + 1);
    for (const auto& a : args) argv.push_back(a.c_str());
    argv.push_back(nullptr);

    pid_t pid = fork();
    if (pid < 0) { VINE_LOGE_ERRNO("fork"); return -1; }
    if (pid == 0) {
        execv(argv[0], const_cast<char* const*>(argv.data()));
        VINE_LOGE_ERRNO(("execv(" + args[0] + ")").c_str());
        _exit(127);
    }
    return pid;
}

int exec_wait(const std::vector<std::string>& args) {
    pid_t pid = do_fork_exec(args);
    if (pid < 0) return -1;
    int status = 0;
    return waitpid(pid, &status, 0) < 0 ? -1
         : WIFEXITED(status) ? WEXITSTATUS(status) : -1;
}

pid_t exec_async(const std::vector<std::string>& args) {
    return do_fork_exec(args);
}

bool terminate_gracefully(pid_t pid, int timeout_ms) {
    if (pid <= 0) return false;
    if (kill(pid, 0) != 0) return true;
    if (kill(pid, SIGTERM) != 0) { VINE_LOGE_ERRNO("SIGTERM"); return false; }

    const int poll_ms = 100;
    for (int elapsed = 0; elapsed < timeout_ms; elapsed += poll_ms) {
        usleep(poll_ms * 1000);
        int st = 0;
        if (waitpid(pid, &st, WNOHANG) == pid) return true;
    }
    VINE_LOGW("pid %d didn't exit in %dms, sending SIGKILL", pid, timeout_ms);
    kill(pid, SIGKILL);
    waitpid(pid, nullptr, 0);
    return false;
}

// AArch32 detection uses 4 layers from most to least reliable:
//   L1: ro.product.cpu.abilist32 — empty on arm64-only SoCs (Tensor G3, Dimensity 8400-Ultra)
//   L2: ro.product.cpu.abilist   — contains "armeabi" only if CPU supports AArch32
//   L3: /proc/sys/abi/           — kernel compat knobs only exist when CONFIG_COMPAT=y
//   L4: /proc/cpuinfo aarch32_el0 — CPU feature flag, present on Linux 4.7+ with AArch32
// If all four return negative, the device is arm64-only and QEMU mode is activated.
bool host_supports_aarch32() {
    char buf[PROP_VALUE_MAX] = {};

    __system_property_get("ro.product.cpu.abilist32", buf);
    if (buf[0] != '\0') {
        VINE_LOGI("AArch32: L1 abilist32='%s' → supported", buf);
        return true;
    }

    __system_property_get("ro.product.cpu.abilist", buf);
    if (strstr(buf, "armeabi") != nullptr) {
        VINE_LOGI("AArch32: L2 abilist='%s' → supported", buf);
        return true;
    }

    static const char* kCompatKnobs[] = {
        "/proc/sys/abi/cp15_barrier_emulation",
        "/proc/sys/abi/setend_emulation",
        "/proc/sys/abi/swp_emulation",
    };
    for (const char* f : kCompatKnobs) {
        if (path_exists(f)) {
            VINE_LOGI("AArch32: L3 found %s → supported", f);
            return true;
        }
    }

    auto cpuinfo = read_file("/proc/cpuinfo");
    if (cpuinfo.has_value() && cpuinfo->find("aarch32_el0") != std::string::npos) {
        VINE_LOGI("AArch32: L4 aarch32_el0 in cpuinfo → supported");
        return true;
    }

    VINE_LOGI("AArch32: all layers negative → arm64-only SoC, QEMU required");
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
    if (sysinfo(&info) != 0) { VINE_LOGE_ERRNO("sysinfo"); return -1; }
    return (long)(info.freeram * (unsigned long)info.mem_unit) / (1024 * 1024);
}

} // namespace vine
