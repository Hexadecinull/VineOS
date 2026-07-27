#include "qemu_launcher.h"
#include "../utils/vine_log.h"
#include "../utils/vine_utils.h"
#include <unistd.h>
#include <sys/stat.h>

namespace vine::qemu {

// NDK packages native binaries as .so files; libqemu_arm.so is a raw executable
// extracted to the native lib dir by Android at install time.
std::string qemu_arm_path(const std::string& native_lib_dir) {
    return native_lib_dir + "/libqemu_arm.so";
}

bool verify_qemu_binary(const std::string& path) {
    if (!path_exists(path)) {
        VINE_LOGE("qemu-arm binary not found at: %s", path.c_str());
        return false;
    }

    struct stat st{};
    if (stat(path.c_str(), &st) != 0) {
        VINE_LOGE_ERRNO("stat(qemu-arm)");
        return false;
    }

    if (!(st.st_mode & S_IXUSR)) {
        VINE_LOGW("qemu-arm binary not executable, attempting chmod");
        if (chmod(path.c_str(), 0755) != 0) {
            VINE_LOGE_ERRNO("chmod(qemu-arm)");
            return false;
        }
    }

    auto content = read_file(path);
    if (!content.has_value() || content->size() < 20) {
        VINE_LOGE("qemu-arm binary too small or unreadable");
        return false;
    }

    const uint8_t* elf = reinterpret_cast<const uint8_t*>(content->data());
    if (elf[0] != 0x7f || elf[1] != 'E' || elf[2] != 'L' || elf[3] != 'F') {
        VINE_LOGE("qemu-arm binary is not an ELF file");
        return false;
    }
    // EI_CLASS=2 → ELFCLASS64
    if (elf[4] != 2) {
        VINE_LOGE("qemu-arm binary is 32-bit, it must be compiled as arm64-v8a!");
        return false;
    }
    // e_machine at offset 18 (LE): 0xB7 0x00 = EM_AARCH64
    if (elf[18] != 0xB7 || elf[19] != 0x00) {
        VINE_LOGW("qemu-arm binary machine type mismatch (expected EM_AARCH64)");
        // Non-fatal, may still work on x86_64 dev emulators.
    }

    VINE_LOGI("qemu-arm binary verified: %s (%zu bytes)", path.c_str(), content->size());
    return true;
}

} // namespace vine::qemu
