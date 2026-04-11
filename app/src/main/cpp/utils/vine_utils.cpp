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

// ─── Filesystem helpers ───────────────────────────────────────────────────────

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

// ─── Process helpers ──────────────────────────────────────────────────────────

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
    VINE_LOGW("pid %d didn't exit in %dms, SIGKILL", pid, timeout_ms);
    kill(pid, SIGKILL);
    waitpid(pid, nullptr, 0);
    return false;
}

// ─── AArch32 detection ────────────────────────────────────────────────────────
//
// RESEARCH NOTES — AArch32 on arm64-only SoCs
// ============================================
//
// Background:
//   ARMv8-A (AArch64) CPUs are optionally required to support the AArch32
//   execution state. "arm64-only" devices have an ARMv8-A CPU that has the
//   AArch32 exception level (EL0) permanently disabled in hardware via a
//   CPU register bit (ID_AA64PFR0_EL1.EL0 == 0b0001 means 64-bit only;
//   0b0010 means both 32 and 64-bit supported).
//
// Known arm64-only SoCs (as of 2025):
//   - Google Tensor G3 (Pixel 8/8 Pro/8a, 2023) — first commercial Android SoC
//     to drop AArch32 (confirmed via /proc/cpuinfo lacking aarch32_el0 and
//     ro.product.cpu.abilist showing only arm64-v8a)
//   - MediaTek Dimensity 8400-Ultra (POCO X7 Pro, Redmi K80 Pro) — likewise
//   - MediaTek Dimensity 9300 (some variants) — AArch32 dropped
//   - Apple M-series, Samsung Exynos 2500 — not Android-relevant but same situation
//
// Detection strategy (4-layer, most to least reliable):
//
//   Layer 1: ro.product.cpu.abilist32
//     Android sets this property to the list of 32-bit ABIs the device supports.
//     On arm64-only devices, it is an EMPTY STRING. This is the gold standard.
//     Source: frameworks/base/core/jni/android_util_Process.cpp sets this from
//     the kernel's reported ABI capabilities.
//
//   Layer 2: ro.product.cpu.abilist
//     If "armeabi-v7a" or "armeabi" appears here, the device supports 32-bit.
//     On arm64-only devices, only "arm64-v8a" appears.
//
//   Layer 3: /proc/sys/abi/ directory
//     The Linux kernel, when compiled with CONFIG_COMPAT (32-bit compat layer),
//     exposes emulation control knobs under /proc/sys/abi/:
//       cp15_barrier_emulation  — emulates ARMv7 CP15 DMB/DSB barriers
//       setend_emulation        — emulates SETEND (endianness switch) instruction
//       swp_emulation           — emulates SWP/SWPB (swap) instructions
//     These files ONLY exist when the kernel has CONFIG_COMPAT=y AND the CPU
//     supports AArch32. On arm64-only systems, the kernel is built without
//     CONFIG_COMPAT, and these files are absent.
//     Source: arch/arm64/kernel/armv8_deprecated.c
//
//   Layer 4: /proc/cpuinfo Features field
//     Linux 4.7+ (kernel commit 40a8e71eecf0) added "aarch32_el0" to the
//     Features line in /proc/cpuinfo when the CPU supports AArch32 at EL0.
//     Older kernels don't report this, so absence is NOT conclusive, but
//     presence IS conclusive (it's present → 32-bit supported).
//     On arm64-only SoCs, this feature flag is absent.
//     Source: arch/arm64/kernel/cpuinfo.c
//
// Conclusion:
//   If any of the 4 layers says "supported", the device supports AArch32.
//   If ALL 4 layers say "not supported", we are on an arm64-only SoC.
//   In that case, VineOS activates QEMU user-mode emulation (qemu-arm) via
//   binfmt_misc registration to transparently execute ARMv7 binaries.
//
// QEMU binfmt_misc technical note:
//   The 'F' flag in the binfmt_misc registration is CRITICAL for container use.
//   Without 'F': kernel tries to open the interpreter path *after* pivot_root,
//                which fails because the path is relative to the new root.
//   With 'F': kernel opens the interpreter fd at registration time (before
//              pivot_root) and uses that fd for all future executions.
//              This makes it work transparently inside our namespace container.
//   Source: Documentation/admin-guide/binfmt-misc.rst, kernel commit 948b701a607f
//
// QEMU user-mode performance implications:
//   - QEMU's TCG (Tiny Code Generator) JIT compiles basic blocks of ARMv7
//     instructions to host (arm64) instructions at runtime.
//   - First execution of a code path pays the JIT cost; subsequent runs use
//     the translation cache (TB cache).
//   - Typical overhead: 2-4x for CPU-bound code, ~1.2x for I/O-bound code.
//   - The Android Zygote's fork model helps: Zygote itself pays JIT cost once,
//     and forked app processes inherit the warmed-up TB cache pages via COW.
//   - Performance is acceptable for UI-driven 32-bit apps (social media,
//     utilities, older games). Heavy 3D compute will be noticeably slower.

bool host_supports_aarch32() {
    // ── Layer 1: ro.product.cpu.abilist32 ────────────────────────────────────
    // Most reliable Android-specific check.
    // Empty string = arm64-only. Non-empty = has 32-bit ABIs.
    char abi32_list[PROP_VALUE_MAX] = {};
    int len = __system_property_get("ro.product.cpu.abilist32", abi32_list);
    if (len > 0 && abi32_list[0] != '\0') {
        VINE_LOGI("AArch32 check L1: ro.product.cpu.abilist32='%s' → SUPPORTED", abi32_list);
        return true;
    }
    VINE_LOGD("AArch32 check L1: abilist32 empty or absent → not supported (or unknown)");

    // ── Layer 2: ro.product.cpu.abilist ──────────────────────────────────────
    // Scan combined ABI list for 32-bit entries.
    char abi_list[PROP_VALUE_MAX] = {};
    __system_property_get("ro.product.cpu.abilist", abi_list);
    if (strstr(abi_list, "armeabi") != nullptr) {
        VINE_LOGI("AArch32 check L2: abilist='%s' contains armeabi → SUPPORTED", abi_list);
        return true;
    }
    VINE_LOGD("AArch32 check L2: abilist='%s' has no armeabi entry", abi_list);

    // ── Layer 3: /proc/sys/abi/ kernel compat knobs ───────────────────────────
    // These sysfs files only exist when kernel has CONFIG_COMPAT + CPU has AArch32.
    static const char* kAbiSysfsFiles[] = {
        "/proc/sys/abi/cp15_barrier_emulation",
        "/proc/sys/abi/setend_emulation",
        "/proc/sys/abi/swp_emulation",
    };
    for (const char* f : kAbiSysfsFiles) {
        if (path_exists(f)) {
            VINE_LOGI("AArch32 check L3: found kernel compat knob %s → SUPPORTED", f);
            return true;
        }
    }
    VINE_LOGD("AArch32 check L3: no /proc/sys/abi/ compat knobs found");

    // ── Layer 4: /proc/cpuinfo aarch32_el0 feature flag ─────────────────────
    // Present on Linux 4.7+ when CPU supports AArch32 at EL0.
    // Absence is NOT conclusive (older kernels don't report it), but
    // presence IS conclusive.
    auto cpuinfo = read_file("/proc/cpuinfo");
    if (cpuinfo.has_value() && cpuinfo->find("aarch32_el0") != std::string::npos) {
        VINE_LOGI("AArch32 check L4: found aarch32_el0 in /proc/cpuinfo → SUPPORTED");
        return true;
    }
    VINE_LOGD("AArch32 check L4: aarch32_el0 absent from /proc/cpuinfo");

    // All 4 layers returned negative → arm64-only SoC confirmed.
    // VineOS will activate QEMU user-mode emulation for 32-bit apps in the guest.
    VINE_LOGI("AArch32 detection: ALL layers negative → arm64-only SoC. "
              "QEMU user-mode required for armeabi-v7a guest apps.");
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
