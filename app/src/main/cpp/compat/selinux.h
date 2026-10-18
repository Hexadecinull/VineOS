#pragma once

#include <string>

namespace ypsilon::compat {

// Toggle SELinux inside the guest. enforcing=true => setenforce 1,
// enforcing=false => setenforce 0 (permissive). Runs setenforce inside the
// guest rootfs via chroot when a rootfs is provided, otherwise on the host.
bool set_selinux(bool enforcing, const std::string* rootfs = nullptr);

// Returns current SELinux enforcement status, or nullopt if unknown.
bool is_selinux_enforcing();

} // namespace ypsilon::compat
