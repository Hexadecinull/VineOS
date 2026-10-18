#include "root_hide.h"
#include "../utils/ypsilon_log.h"
#include "../utils/ypsilon_utils.h"

#include <cerrno>
#include <cstring>
#include <sys/stat.h>

namespace ypsilon::compat {

// Common root artifacts to hide inside a guest rootfs.
static const char* kRootArtifacts[] = {
    "/system/xbin/su",
    "/system/bin/su",
    "/system/xbin/daemonsu",
    "/system/bin/daemonsu",
    "/system/etc/init.d/99SuperSuDaemon",
    "/system/app/Superuser.apk",
    "/system/app/MagiskManager.apk",
    "/data/adb/magisk",
    "/data/adb/modules",
    "/data/adb/magisk.apk",
    "/cache/su",
    "/data/su",
    "/data/local/su",
    "/data/local/bin/su",
    "/data/local/xbin/su",
};

std::vector<std::string> hidden_root_paths(const std::string& rootfs) {
    std::vector<std::string> out;
    for (const char* rel : kRootArtifacts) {
        out.push_back(rootfs + rel);
    }
    return out;
}

static std::string hidden_suffix() { return ".ypsilon_hidden"; }

bool hide_root_artifacts(const std::string& rootfs) {
    bool any = false;
    for (const char* rel : kRootArtifacts) {
        std::string full = rootfs + rel;
        if (!ypsilon::path_exists(full)) continue;
        std::string hidden = full + hidden_suffix();
        // If already hidden, skip.
        if (ypsilon::path_exists(hidden)) continue;
        if (rename(full.c_str(), hidden.c_str()) != 0) {
            YPSILON_LOGW("root-hide: could not hide %s (%s)", full.c_str(),
                         strerror(errno));
            continue;
        }
        YPSILON_LOGI("root-hide: hidden %s", full.c_str());
        any = true;
    }
    return any;
}

bool restore_root_artifacts(const std::string& rootfs) {
    bool any = false;
    for (const char* rel : kRootArtifacts) {
        std::string hidden = rootfs + rel + hidden_suffix();
        if (!ypsilon::path_exists(hidden)) continue;
        std::string full = rootfs + rel;
        if (rename(hidden.c_str(), full.c_str()) != 0) {
            YPSILON_LOGW("root-hide: could not restore %s (%s)", hidden.c_str(),
                         strerror(errno));
            continue;
        }
        YPSILON_LOGI("root-hide: restored %s", full.c_str());
        any = true;
    }
    return any;
}

} // namespace ypsilon::compat
