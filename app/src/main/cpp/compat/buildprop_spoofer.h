#pragma once

#include <string>
#include <unordered_map>

namespace ypsilon::compat {

// Spoofs / overrides build.prop keys inside a guest rootfs so that SafetyNet /
// Play Integrity and game safety checks (e.g. Pokémon GO) see a clean,
// unrooted, certification-passing device profile.
//
// The spoofer rewrites <rootfs>/system/build.prop in place: existing keys are
// replaced, new keys are appended. A backup (.ypsilon.bak) is kept so the
// original props can be restored.
class BuildPropSpoofer {
public:
    // Apply the given key/value overrides to build.prop inside rootfs.
    // Returns true on success. Call once after the rootfs is mounted (i.e.
    // after Container::start() completes) and before games probe props.
    static bool spoof(const std::string& rootfs,
                      const std::unordered_map<std::string, std::string>& overrides);

    // Restore build.prop from the .ypsilon.bak backup, removing any spoofing.
    static bool restore(const std::string& rootfs);

    // Sensible defaults for a "clean device" profile that passes basic
    // SafetyNet / Play Integrity device-integrity checks and hides root.
    static std::unordered_map<std::string, std::string> cleanDeviceDefaults(
            const std::string& model,
            const std::string& brand,
            const std::string& fingerprint);

private:
    static std::string buildPropPath(const std::string& rootfs);
    static bool loadProps(const std::string& path,
                          std::unordered_map<std::string, std::string>& out);
    static bool saveProps(const std::string& path,
                          const std::unordered_map<std::string, std::string>& props);
};

} // namespace ypsilon::compat
