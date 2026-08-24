#pragma once
#include <string>

namespace vine::diagnostics {

struct NamespaceProbeResult {
    bool unshare_ok = false;
    int unshare_errno = 0;
    bool mount_ok = false;
    int mount_errno = 0;
    int caller_uid = -1;
};

// Attempts unshare(CLONE_NEWNS|CLONE_NEWPID|CLONE_NEWUTS|CLONE_NEWIPC) and a private-mount remount, both from a disposable forked child, to check whether the calling process's privilege level (root, shell/ADB, or plain app) can do what the container engine needs
NamespaceProbeResult probe_namespace_support();

} // namespace vine::diagnostics
