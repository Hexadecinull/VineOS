#include "selinux.h"
#include "../utils/ypsilon_log.h"
#include "../utils/ypsilon_utils.h"

#include <cstring>
#include <fstream>
#include <unistd.h>

namespace ypsilon::compat {

static const char* kEnforcePath = "/sys/fs/selinux/enforce";

bool is_selinux_enforcing() {
    std::ifstream f(kEnforcePath);
    if (!f.is_open()) return false;
    int v = -1;
    f >> v;
    return v == 1;
}

bool set_selinux(bool enforcing, const std::string* rootfs) {
    const int value = enforcing ? 1 : 0;
    YPSILON_LOGI("selinux: requesting enforcing=%d (rootfs=%s)",
                 value, rootfs ? rootfs->c_str() : "(host)");

    // Preferred: write directly to the sysfs knob (this is what setenforce does
    // under the hood; the guest shares the bind-mounted /sys/fs/selinux).
    if (ypsilon::write_file(kEnforcePath, std::to_string(value))) {
        YPSILON_LOGI("selinux: set via %s → %d", kEnforcePath, value);
        return true;
    }
    YPSILON_LOGW("selinux: direct sysfs write failed (%s), trying setenforce binary",
                 strerror(errno));

    // Fallback: run the setenforce binary.
    std::vector<std::string> args;
    if (rootfs) {
        args.push_back("chroot");
        args.push_back(*rootfs);
    }
    args.push_back("setenforce");
    args.push_back(enforcing ? "1" : "0");
    int rc = ypsilon::exec_wait(args);
    if (rc == 0) {
        YPSILON_LOGI("selinux: setenforce → %d succeeded", value);
        return true;
    }
    YPSILON_LOGE("selinux: setenforce → %d failed (rc=%d)", value, rc);
    return false;
}

} // namespace ypsilon::compat
