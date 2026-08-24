#include "namespace_probe.h"
#include <cerrno>
#include <cstring>
#include <jni.h>
#include <sched.h>
#include <sys/mount.h>
#include <sys/wait.h>
#include <unistd.h>
#include "../utils/vine_log.h"

namespace vine::diagnostics {

namespace {
struct ProbeWireResult { int unshare_ok; int unshare_errno; int mount_ok; int mount_errno; };
}

NamespaceProbeResult probe_namespace_support() {
    NamespaceProbeResult result;
    result.caller_uid = (int)getuid();

    int pipefd[2];
    if (pipe(pipefd) != 0) { VINE_LOGE_ERRNO("pipe"); return result; }

    pid_t pid = fork();
    if (pid < 0) {
        VINE_LOGE_ERRNO("fork");
        close(pipefd[0]);
        close(pipefd[1]);
        return result;
    }

    if (pid == 0) {
        close(pipefd[0]);
        ProbeWireResult wire{};
        if (unshare(CLONE_NEWNS | CLONE_NEWPID | CLONE_NEWUTS | CLONE_NEWIPC) == 0) {
            wire.unshare_ok = 1;
            if (mount("none", "/", nullptr, MS_REC | MS_PRIVATE, nullptr) == 0) {
                wire.mount_ok = 1;
            } else {
                wire.mount_errno = errno;
            }
        } else {
            wire.unshare_errno = errno;
        }
        ssize_t written = write(pipefd[1], &wire, sizeof(wire));
        (void)written;
        close(pipefd[1]);
        _exit(0);
    }

    close(pipefd[1]);
    ProbeWireResult wire{};
    ssize_t n = read(pipefd[0], &wire, sizeof(wire));
    close(pipefd[0]);
    waitpid(pid, nullptr, 0);

    if (n == (ssize_t)sizeof(wire)) {
        result.unshare_ok = wire.unshare_ok != 0;
        result.unshare_errno = wire.unshare_errno;
        result.mount_ok = wire.mount_ok != 0;
        result.mount_errno = wire.mount_errno;
    }
    return result;
}

} // namespace vine::diagnostics

extern "C" JNIEXPORT jstring JNICALL
Java_com_hexadecinull_vineos_shizuku_VineShizukuService_nativeProbeNamespaces(JNIEnv* env, jobject) {
    auto r = vine::diagnostics::probe_namespace_support();
    char buf[256];
    snprintf(buf, sizeof(buf), "unshare_ok=%d;unshare_errno=%d;mount_ok=%d;mount_errno=%d;uid=%d",
              r.unshare_ok ? 1 : 0, r.unshare_errno, r.mount_ok ? 1 : 0, r.mount_errno, r.caller_uid);
    return env->NewStringUTF(buf);
}
