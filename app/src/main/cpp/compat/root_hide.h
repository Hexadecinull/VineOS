#pragma once

#include <string>
#include <vector>

namespace ypsilon::compat {

// Attempts to hide common root artifacts (su, magisk, etc.) inside a guest
// rootfs so that SafetyNet / Play Integrity and game root-detection do not
// trivially find them. Best-effort: logs and continues if files are absent or
// not writable.
//
// Also returns a set of build.prop overrides that make the device appear
// unrooted (ro.debuggable=0, ro.secure=1, ro.build.type=user, ...).
std::vector<std::string> hidden_root_paths(const std::string& rootfs);
bool hide_root_artifacts(const std::string& rootfs);
bool restore_root_artifacts(const std::string& rootfs);

} // namespace ypsilon::compat
