#include "buildprop_spoofer.h"
#include "../utils/ypsilon_log.h"
#include "../utils/ypsilon_utils.h"

#include <cerrno>
#include <cstring>
#include <fstream>
#include <sstream>

namespace ypsilon::compat {

std::string BuildPropSpoofer::buildPropPath(const std::string& rootfs) {
    return rootfs + "/system/build.prop";
}

bool BuildPropSpoofer::loadProps(const std::string& path,
                                 std::unordered_map<std::string, std::string>& out) {
    out.clear();
    auto contents = ypsilon::read_file(path);
    if (!contents.has_value()) {
        // No build.prop yet; that's fine, we'll create one.
        return true;
    }
    std::istringstream ss(*contents);
    std::string line;
    while (std::getline(ss, line)) {
        // Strip CR and trailing whitespace.
        while (!line.empty() && (line.back() == '\r' || line.back() == ' ' ||
                                 line.back() == '\t')) {
            line.pop_back();
        }
        if (line.empty() || line[0] == '#') continue;
        auto eq = line.find('=');
        if (eq == std::string::npos) continue;
        std::string key = line.substr(0, eq);
        std::string val = line.substr(eq + 1);
        // Trim whitespace around key/val.
        while (!key.empty() && (key.back() == ' ' || key.back() == '\t')) key.pop_back();
        while (!val.empty() && (val.front() == ' ' || val.front() == '\t')) val.erase(val.begin());
        out[std::move(key)] = std::move(val);
    }
    return true;
}

bool BuildPropSpoofer::saveProps(const std::string& path,
                                 const std::unordered_map<std::string, std::string>& props) {
    std::ostringstream ss;
    for (const auto& [key, val] : props) {
        ss << key << "=" << val << "\n";
    }
    return ypsilon::write_file(path, ss.str());
}

bool BuildPropSpoofer::spoof(const std::string& rootfs,
                             const std::unordered_map<std::string, std::string>& overrides) {
    const std::string path = buildPropPath(rootfs);
    const std::string bak = path + ".ypsilon.bak";

    // Keep a backup of the original so we can restore cleanly.
    if (ypsilon::path_exists(path) && !ypsilon::path_exists(bak)) {
        if (auto c = ypsilon::read_file(path); c.has_value()) {
            ypsilon::write_file(bak, *c);
        }
    }

    std::unordered_map<std::string, std::string> props;
    if (!loadProps(path, props)) {
        YPSILON_LOGE("buildprop: failed to read %s", path.c_str());
        return false;
    }

    for (const auto& [key, val] : overrides) {
        props[key] = val;
        YPSILON_LOGI("buildprop: spoof %s=%s", key.c_str(), val.c_str());
    }

    if (!saveProps(path, props)) {
        YPSILON_LOGE("buildprop: failed to write %s", path.c_str());
        return false;
    }
    return true;
}

bool BuildPropSpoofer::restore(const std::string& rootfs) {
    const std::string path = buildPropPath(rootfs);
    const std::string bak = path + ".ypsilon.bak";
    if (!ypsilon::path_exists(bak)) return true; // nothing to restore
    if (auto c = ypsilon::read_file(bak); c.has_value()) {
        if (ypsilon::write_file(path, *c)) {
            ypsilon::write_file(bak, ""); // clear backup
            return true;
        }
    }
    return false;
}

std::unordered_map<std::string, std::string> BuildPropSpoofer::cleanDeviceDefaults(
        const std::string& model,
        const std::string& brand,
        const std::string& fingerprint) {
    std::unordered_map<std::string, std::string> props;
    // Core identity.
    props["ro.product.model"] = model;
    props["ro.product.brand"] = brand;
    props["ro.product.manufacturer"] = brand;
    props["ro.product.name"] = model;
    props["ro.product.device"] = model;
    // Fingerprint is the big one for SafetyNet / Play Integrity.
    props["ro.build.fingerprint"] = fingerprint;
    props["ro.bootimage.build.fingerprint"] = fingerprint;
    props["ro.vendor.build.fingerprint"] = fingerprint;
    props["ro.system.build.fingerprint"] = fingerprint;
    props["ro.build.version.security_patch"] = "2026-10-01";
    // Appear unrooted, non-debuggable, release-keys, locked bootloader.
    props["ro.build.type"] = "user";
    props["ro.build.tags"] = "release-keys";
    props["ro.debuggable"] = "0";
    props["ro.secure"] = "1";
    props["ro.build.selinux"] = "1";
    props["ro.rooted"] = "0";
    props["ro.init.selinux"] = "1";
    props["ro.adb.secure"] = "1";
    props["persist.sys.root_access"] = "0";
    props["ro.odm.build.fingerprint"] = fingerprint;
    return props;
}

} // namespace ypsilon::compat
