#pragma once
#include <string>

namespace ypsilon::qemu {

// Returns the expected path of the bundled static qemu-arm binary within the app's native library directory
std::string qemu_arm_path(const std::string& native_lib_dir);

// Verifies the qemu-arm binary exists and is executable
bool verify_qemu_binary(const std::string& path);

} // namespace ypsilon::qemu
