#include "namespace_manager.h"
#include "../utils/vine_log.h"
#include "../utils/vine_utils.h"

#include <cerrno>
#include <cstring>
#include <sched.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mount.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <sys/sysmacros.h>
#include <linux/loop.h>
#include <unordered_map>

// ─── Android-specific syscall numbers (arm64) ─────────────────────────────────
// unshare() is available via sched.h on NDK, but pivot_root is not always wrapped.
#include <sys/syscall.h>
#define vine_pivot_root(new_root, put_old) syscall(SYS_pivot_root, new_root, put_old)

namespace vine {

// ─────────────────────────────────────────────────────────────────────────────
// Container
// ─────────────────────────────────────────────────────────────────────────────

Container::Container(ContainerConfig config)
    : config_(std::move(config)) {}

Container::~Container() {
    if (status_ == ContainerStatus::RUNNING || status_ == ContainerStatus::BOOTING) {
        VINE_LOGW("Container %s destroyed while still running — force-killing",
                  config_.instance_id.c_str());
        kill_now();
    }
}

Container::Container(Container&& o) noexcept
    : config_(std::move(o.config_)),
      status_(o.status_),
      init_pid_(o.init_pid_),
      framebuffer_fd_(o.framebuffer_fd_) {
    o.status_ = ContainerStatus::STOPPED;
    o.init_pid_ = -1;
    o.framebuffer_fd_ = -1;
}

Container& Container::operator=(Container&& o) noexcept {
    if (this != &o) {
        config_ = std::move(o.config_);
        status_ = o.status_;
        init_pid_ = o.init_pid_;
        framebuffer_fd_ = o.framebuffer_fd_;
        o.status_ = ContainerStatus::STOPPED;
        o.init_pid_ = -1;
        o.framebuffer_fd_ = -1;
    }
    return *this;
}

// ─── start() ─────────────────────────────────────────────────────────────────

bool Container::start() {
    VINE_LOGI("Container::start() — instance %s", config_.instance_id.c_str());
    status_ = ContainerStatus::BOOTING;

    // 1. Ensure instance directories exist
    if (!mkdirs(config_.rootfs_mount_path)) {
        VINE_LOGE("Failed to create rootfs mount point: %s", config_.rootfs_mount_path.c_str());
        status_ = ContainerStatus::ERROR;
        return false;
    }

    // 2. Loop-mount the ROM image
    if (!mount_rootfs()) {
        status_ = ContainerStatus::ERROR;
        return false;
    }

    // 3. Bind-mount virtual filesystems (/proc, /sys, /dev, etc.)
    if (!setup_bind_mounts()) {
        teardown_mounts();
        status_ = ContainerStatus::ERROR;
        return false;
    }

    // 4. Create device nodes
    if (!setup_dev_nodes()) {
        teardown_mounts();
        status_ = ContainerStatus::ERROR;
        return false;
    }

    // 5. Register QEMU binfmt_misc for ARMv7 if host lacks AArch32
    if (config_.needs_qemu_32bit) {
        if (!setup_binfmt_misc()) {
            VINE_LOGW("binfmt_misc setup failed — 32-bit apps will not work in this instance");
            // Non-fatal: 64-bit apps will still work
        }
    }

    // 6. Launch Android init
    if (!launch_init()) {
        teardown_mounts();
        status_ = ContainerStatus::ERROR;
        return false;
    }

    VINE_LOGI("Container %s started, init PID = %d", config_.instance_id.c_str(), init_pid_);
    status_ = ContainerStatus::RUNNING;
    return true;
}

// ─── stop() / kill_now() ─────────────────────────────────────────────────────

void Container::stop(int timeout_ms) {
    VINE_LOGI("Container::stop() — instance %s, init PID = %d",
              config_.instance_id.c_str(), init_pid_);

    if (init_pid_ > 0) {
        terminate_gracefully(init_pid_, timeout_ms);
        init_pid_ = -1;
    }

    teardown_mounts();

    if (framebuffer_fd_ >= 0) {
        close(framebuffer_fd_);
        framebuffer_fd_ = -1;
    }

    status_ = ContainerStatus::STOPPED;
}

void Container::kill_now() {
    if (init_pid_ > 0) {
        kill(init_pid_, SIGKILL);
        waitpid(init_pid_, nullptr, 0);
        init_pid_ = -1;
    }
    teardown_mounts();
    status_ = ContainerStatus::STOPPED;
}

// ─── mount_rootfs() ──────────────────────────────────────────────────────────

bool Container::mount_rootfs() {
    VINE_LOGD("Mounting rootfs image %s → %s",
              config_.rootfs_image_path.c_str(),
              config_.rootfs_mount_path.c_str());

    // Find a free loop device
    int loop_ctl_fd = open("/dev/loop-control", O_RDWR | O_CLOEXEC);
    if (loop_ctl_fd < 0) {
        VINE_LOGE_ERRNO("open(/dev/loop-control)");
        return false;
    }

    int loop_num = ioctl(loop_ctl_fd, LOOP_CTL_GET_FREE);
    close(loop_ctl_fd);

    if (loop_num < 0) {
        VINE_LOGE("LOOP_CTL_GET_FREE failed: %s", strerror(errno));
        return false;
    }

    std::string loop_dev = "/dev/loop" + std::to_string(loop_num);
    VINE_LOGD("Using loop device %s", loop_dev.c_str());

    // Open the ROM image
    int img_fd = open(config_.rootfs_image_path.c_str(), O_RDWR | O_CLOEXEC);
    if (img_fd < 0) {
        // Try read-only fallback (stock ROM images shouldn't be modified)
        img_fd = open(config_.rootfs_image_path.c_str(), O_RDONLY | O_CLOEXEC);
        if (img_fd < 0) {
            VINE_LOGE_ERRNO(("open(rootfs_image: " + config_.rootfs_image_path + ")").c_str());
            return false;
        }
    }

    // Attach image to loop device
    int loop_fd = open(loop_dev.c_str(), O_RDWR | O_CLOEXEC);
    if (loop_fd < 0) {
        VINE_LOGE_ERRNO(("open(loop_dev: " + loop_dev + ")").c_str());
        close(img_fd);
        return false;
    }

    if (ioctl(loop_fd, LOOP_SET_FD, img_fd) < 0) {
        VINE_LOGE_ERRNO("LOOP_SET_FD");
        close(loop_fd);
        close(img_fd);
        return false;
    }
    close(img_fd); // loop device holds a reference

    // Set loop device flags — make it auto-clear when the fd is closed
    struct loop_info64 li{};
    li.lo_flags = LO_FLAGS_AUTOCLEAR;
    ioctl(loop_fd, LOOP_SET_STATUS64, &li); // Non-fatal if this fails
    close(loop_fd);

    // Mount the loop device as ext4 read-write
    // NOTE: The ROM's system partition is typically read-only; the overlay strategy
    // for writable /data will be implemented in Phase 2 (overlay filesystem).
    if (mount(loop_dev.c_str(), config_.rootfs_mount_path.c_str(),
               "ext4", MS_RELATIME, "errors=remount-ro") != 0) {
        // Try squashfs (some ROM images use it)
        if (mount(loop_dev.c_str(), config_.rootfs_mount_path.c_str(),
                   "squashfs", MS_RDONLY, nullptr) != 0) {
            VINE_LOGE_ERRNO("mount(rootfs)");
            return false;
        }
        VINE_LOGI("Mounted rootfs as squashfs (read-only)");
    }

    VINE_LOGI("Rootfs mounted successfully at %s", config_.rootfs_mount_path.c_str());
    return true;
}

// ─── setup_bind_mounts() ─────────────────────────────────────────────────────

bool Container::setup_bind_mounts() {
    VINE_LOGD("Setting up bind mounts for %s", config_.instance_id.c_str());

    const std::string root = config_.rootfs_mount_path;

    // Standard Android virtual filesystem mounts
    struct MountEntry {
        const char* source;
        const char* target_suffix; // relative to rootfs mount
        const char* fstype;
        unsigned long flags;
        const char* data;
    };

    const MountEntry entries[] = {
        // proc
        { "proc",     "/proc",        "proc",    MS_NODEV | MS_NOEXEC | MS_NOSUID, nullptr },
        // sysfs
        { "sysfs",    "/sys",         "sysfs",   MS_NODEV | MS_NOEXEC | MS_NOSUID, nullptr },
        // tmpfs for /dev (we populate devices manually)
        { "tmpfs",    "/dev",         "tmpfs",   MS_NOSUID,                         "size=65536k,mode=755" },
        // devpts for pseudo-terminals
        { "devpts",   "/dev/pts",     "devpts",  MS_NOEXEC | MS_NOSUID,             "newinstance,ptmxmode=0666,mode=0620" },
        // tmpfs for /dev/shm
        { "tmpfs",    "/dev/shm",     "tmpfs",   MS_NOSUID | MS_NODEV,              "size=65536k" },
        // tmpfs for /tmp
        { "tmpfs",    "/tmp",         "tmpfs",   MS_NOSUID | MS_NODEV,              "size=32768k,mode=1777" },
        // cgroup (needed by Android's init for process management)
        { "cgroup",   "/sys/fs/cgroup", "cgroup", MS_NODEV | MS_NOEXEC | MS_NOSUID, "all" },
    };

    for (const auto& e : entries) {
        std::string target = root + e.target_suffix;
        // Create mount point if needed
        mkdirs(target);
        if (mount(e.source, target.c_str(), e.fstype, e.flags, e.data) != 0) {
            VINE_LOGE("mount(%s → %s, %s) failed: %s",
                      e.source, target.c_str(), e.fstype, strerror(errno));
            // Non-fatal for some mounts (e.g. cgroup might be pre-mounted on host)
        }
    }

    // Bind-mount the host's /dev/binder into the guest (needed for Android IPC)
    {
        std::string guest_binder = root + "/dev/binder";
        mkdirs(guest_binder); // Will be a file, not dir — create empty file
        // Actually binder is a character device, so touch it
        int bfd = open(guest_binder.c_str(), O_CREAT | O_WRONLY | O_CLOEXEC, 0600);
        if (bfd >= 0) close(bfd);
        if (mount("/dev/binder", guest_binder.c_str(), nullptr, MS_BIND, nullptr) != 0) {
            VINE_LOGW("Could not bind-mount /dev/binder: %s — Android IPC may not work", strerror(errno));
        }
    }

    return true;
}

// ─── setup_dev_nodes() ───────────────────────────────────────────────────────

bool Container::setup_dev_nodes() {
    const std::string dev = config_.rootfs_mount_path + "/dev";

    // Create essential device nodes
    // (major:minor values from Linux documentation)
    struct DevNode {
        const char* name;
        mode_t mode;
        dev_t rdev;
    };

    const DevNode nodes[] = {
        { "null",    S_IFCHR | 0666, makedev(1, 3) },
        { "zero",    S_IFCHR | 0666, makedev(1, 5) },
        { "full",    S_IFCHR | 0666, makedev(1, 7) },
        { "random",  S_IFCHR | 0666, makedev(1, 8) },
        { "urandom", S_IFCHR | 0666, makedev(1, 9) },
        { "tty",     S_IFCHR | 0666, makedev(5, 0) },
        { "console", S_IFCHR | 0600, makedev(5, 1) },
        { "ptmx",    S_IFCHR | 0666, makedev(5, 2) },
    };

    for (const auto& n : nodes) {
        std::string path = dev + "/" + n.name;
        if (mknod(path.c_str(), n.mode, n.rdev) != 0 && errno != EEXIST) {
            VINE_LOGW("mknod(%s) failed: %s", path.c_str(), strerror(errno));
            // Non-fatal — the host device might already exist via bind mount
        }
    }

    // Symlinks expected by Android
    symlink("/proc/self/fd",   (dev + "/fd").c_str());
    symlink("/proc/self/fd/0", (dev + "/stdin").c_str());
    symlink("/proc/self/fd/1", (dev + "/stdout").c_str());
    symlink("/proc/self/fd/2", (dev + "/stderr").c_str());

    return true;
}

// ─── setup_binfmt_misc() ─────────────────────────────────────────────────────

bool Container::setup_binfmt_misc() {
    VINE_LOGI("Setting up QEMU binfmt_misc for ARMv7 in instance %s",
              config_.instance_id.c_str());

    if (config_.qemu_arm_path.empty() || !path_exists(config_.qemu_arm_path)) {
        VINE_LOGE("qemu-arm binary not found at: %s", config_.qemu_arm_path.c_str());
        return false;
    }

    // Copy qemu-arm into the guest /system/bin so it's accessible inside the namespace
    std::string guest_qemu_path = config_.rootfs_mount_path + "/system/bin/qemu-arm";
    // We can't copy a binary here without extra work; instead we bind-mount it.
    // First create an empty file as the mount target
    {
        int fd = open(guest_qemu_path.c_str(), O_CREAT | O_WRONLY | O_CLOEXEC, 0755);
        if (fd >= 0) close(fd);
    }
    if (mount(config_.qemu_arm_path.c_str(), guest_qemu_path.c_str(),
               nullptr, MS_BIND | MS_RDONLY, nullptr) != 0) {
        VINE_LOGE_ERRNO("bind-mount qemu-arm into guest");
        return false;
    }

    // Mount binfmt_misc if not already mounted
    const std::string binfmt_path = "/proc/sys/fs/binfmt_misc";
    if (!path_exists(binfmt_path + "/register")) {
        if (mount("binfmt_misc", binfmt_path.c_str(), "binfmt_misc",
                   MS_NODEV | MS_NOEXEC | MS_NOSUID, nullptr) != 0) {
            VINE_LOGE_ERRNO("mount(binfmt_misc)");
            return false;
        }
    }

    // Register the ARMv7 ELF magic with qemu-arm.
    // The 'F' flag at the end means "fix the interpreter binary path" —
    // the kernel opens the interpreter at registration time and uses it even
    // after a chroot/namespace change. This is the critical flag for container use.
    //
    // Magic bytes: ELF32 LE ARM header (\x7fELF\x01\x01\x01 ... \x02\x00\x28\x00)
    const std::string binfmt_rule =
        ":arm:M::"
        "\\x7fELF\\x01\\x01\\x01\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x02\\x00\\x28\\x00"
        ":"
        "\\xff\\xff\\xff\\xff\\xff\\xff\\xff\\x00\\xff\\xff\\xff\\xff\\xff\\xff\\xff\\xff\\xfe\\xff\\xff\\xff"
        ":"
        + config_.qemu_arm_path +  // Use host-absolute path (F flag resolves it at register time)
        ":F";

    const std::string register_path = binfmt_path + "/register";
    if (!write_file(register_path, binfmt_rule)) {
        // May already be registered — check
        if (path_exists(binfmt_path + "/arm")) {
            VINE_LOGI("binfmt_misc arm already registered");
            return true;
        }
        VINE_LOGE("Failed to register ARMv7 binfmt_misc rule");
        return false;
    }

    VINE_LOGI("ARMv7 binfmt_misc registered successfully → qemu-arm: %s",
              config_.qemu_arm_path.c_str());
    return true;
}

// ─── launch_init() ───────────────────────────────────────────────────────────

bool Container::launch_init() {
    const std::string rootfs = config_.rootfs_mount_path;
    const std::string init_path = rootfs + "/init";

    if (!path_exists(init_path)) {
        VINE_LOGE("Android init not found at %s", init_path.c_str());
        return false;
    }

    VINE_LOGI("Launching Android init in new namespaces...");

    // We use clone() with CLONE_NEWPID | CLONE_NEWNS | CLONE_NEWUTS | CLONE_NEWNET
    // to create a fully isolated container.
    //
    // NOTE: CLONE_NEWUSER (user namespace) would allow unprivileged container creation,
    // but Android's kernel configurations often restrict nested user namespaces.
    // For now, we require the host process to have CAP_SYS_ADMIN (i.e. root or
    // a privileged foreground service with the right selinux context).
    //
    // Phase 4 TODO: Implement proot-based fallback for no-root devices.

    pid_t pid = fork();
    if (pid < 0) {
        VINE_LOGE_ERRNO("fork before clone");
        return false;
    }

    if (pid == 0) {
        // ── Child process: set up namespaces and exec init ────────────────

        // Create new namespaces
        int ns_flags = CLONE_NEWPID  // New PID namespace — init becomes PID 1
                     | CLONE_NEWNS   // New mount namespace — our mounts are private
                     | CLONE_NEWUTS  // New UTS namespace — can set our own hostname
                     | CLONE_NEWIPC; // New IPC namespace — isolated shared memory

        // Optionally: CLONE_NEWNET for network isolation
        // (disabled for Phase 1 — we want internet access in the guest)
        // ns_flags |= CLONE_NEWNET;

        if (unshare(ns_flags) != 0) {
            VINE_LOGE_ERRNO("unshare(namespaces)");
            _exit(1);
        }

        // Set hostname for the guest
        const std::string hostname = "vine-" + config_.instance_id.substr(0, 8);
        if (sethostname(hostname.c_str(), hostname.size()) != 0) {
            VINE_LOGW("sethostname failed: %s", strerror(errno));
        }

        // Make all existing mounts private (don't propagate to host)
        if (mount("none", "/", nullptr, MS_REC | MS_PRIVATE, nullptr) != 0) {
            VINE_LOGE_ERRNO("mount(MS_PRIVATE)");
            _exit(1);
        }

        // Change root to the guest rootfs
        // pivot_root is preferred over chroot because it properly moves the old root
        // under the new root, allowing us to unmount it cleanly.
        const std::string put_old = rootfs + "/.old_root";
        mkdirs(put_old);

        if (vine_pivot_root(rootfs.c_str(), put_old.c_str()) != 0) {
            VINE_LOGE_ERRNO("pivot_root");
            // Fallback to chroot
            if (chroot(rootfs.c_str()) != 0) {
                VINE_LOGE_ERRNO("chroot fallback");
                _exit(1);
            }
        } else {
            // Unmount the old root
            if (umount2("/.old_root", MNT_DETACH) != 0) {
                VINE_LOGW("umount2(.old_root) failed: %s", strerror(errno));
            }
            rmdir("/.old_root");
        }

        chdir("/");

        // Exec Android init
        // Android init typically lives at /init and expects to run as PID 1.
        // We pass "androidboot.hardware=vine" so init.rc can conditionally
        // run VineOS-specific init scripts.
        execl("/init", "/init",
              "androidboot.hardware=vine",
              "androidboot.selinux=permissive",   // Required until we have SELinux policy
              (char*)nullptr);

        VINE_LOGE_ERRNO("execl(/init)");
        _exit(1);
    }

    // Parent stores child PID
    init_pid_ = pid;
    VINE_LOGI("Guest init launched as host PID %d", init_pid_);
    return true;
}

// ─── teardown_mounts() ───────────────────────────────────────────────────────

void Container::teardown_mounts() {
    VINE_LOGD("Tearing down mounts for instance %s", config_.instance_id.c_str());

    const std::string root = config_.rootfs_mount_path;

    // Unmount in reverse order
    const char* umount_targets[] = {
        "/sys/fs/cgroup", "/dev/pts", "/dev/shm", "/dev",
        "/sys", "/proc", "/system/bin/qemu-arm",
    };

    for (const auto* suffix : umount_targets) {
        std::string path = root + suffix;
        if (umount2(path.c_str(), MNT_DETACH) != 0 && errno != EINVAL && errno != ENOENT) {
            VINE_LOGW("umount2(%s) failed: %s", path.c_str(), strerror(errno));
        }
    }

    // Finally unmount the rootfs itself
    if (umount2(root.c_str(), MNT_DETACH) != 0) {
        VINE_LOGW("umount2(rootfs) failed: %s", strerror(errno));
    }
}

// ─── I/O ─────────────────────────────────────────────────────────────────────

void Container::send_touch(int action, float x, float y) {
    // TODO: Phase 2 — write to /dev/input/event* (uinput virtual device)
    VINE_LOGD("send_touch: action=%d x=%.0f y=%.0f", action, x, y);
}

void Container::send_key(int keycode, bool down) {
    // TODO: Phase 2 — write to /dev/input/event* (uinput virtual device)
    VINE_LOGD("send_key: keycode=%d down=%s", keycode, down ? "true" : "false");
}

std::string Container::diagnostics() const {
    std::string out;
    out += "=== VineOS Container Diagnostics ===\n";
    out += "Instance ID  : " + config_.instance_id + "\n";
    out += "Status       : " + std::to_string(static_cast<int>(status_)) + "\n";
    out += "Init PID     : " + std::to_string(init_pid_) + "\n";
    out += "Rootfs       : " + config_.rootfs_mount_path + "\n";
    out += "QEMU mode    : " + std::string(config_.needs_qemu_32bit ? "YES" : "NO") + "\n";
    if (config_.needs_qemu_32bit) {
        out += "QEMU binary  : " + config_.qemu_arm_path + "\n";
    }

    // Read /proc/{init_pid}/status for memory info
    if (init_pid_ > 0) {
        auto status = read_file("/proc/" + std::to_string(init_pid_) + "/status");
        if (status.has_value()) {
            out += "\n--- Guest init /proc/status ---\n";
            out += *status;
        }
    }

    return out;
}

// ─────────────────────────────────────────────────────────────────────────────
// NamespaceManager
// ─────────────────────────────────────────────────────────────────────────────

NamespaceManager& NamespaceManager::instance() {
    static NamespaceManager mgr;
    return mgr;
}

bool NamespaceManager::init(const std::string& data_dir, const std::string& native_lib_dir) {
    data_dir_ = data_dir;
    native_lib_dir_ = native_lib_dir;
    initialized_ = true;
    VINE_LOGI("NamespaceManager initialized. data=%s libs=%s",
              data_dir.c_str(), native_lib_dir.c_str());
    return true;
}

void NamespaceManager::shutdown() {
    VINE_LOGI("NamespaceManager::shutdown() — stopping %zu containers", containers_.size());
    for (auto& [handle, container] : containers_) {
        container.stop(5000);
    }
    containers_.clear();
    initialized_ = false;
}

int64_t NamespaceManager::start_container(const ContainerConfig& config) {
    if (!initialized_) {
        VINE_LOGE("NamespaceManager not initialized");
        return 0;
    }
    int64_t handle = next_handle_++;
    auto [it, ok] = containers_.emplace(handle, Container(config));
    if (!ok) {
        VINE_LOGE("Failed to insert container into map");
        return 0;
    }
    if (!it->second.start()) {
        containers_.erase(it);
        return 0;
    }
    return handle;
}

void NamespaceManager::stop_container(int64_t handle) {
    auto it = containers_.find(handle);
    if (it != containers_.end()) {
        it->second.stop();
    }
}

void NamespaceManager::kill_container(int64_t handle) {
    auto it = containers_.find(handle);
    if (it != containers_.end()) {
        it->second.kill_now();
    }
}

Container* NamespaceManager::get_container(int64_t handle) {
    auto it = containers_.find(handle);
    return it != containers_.end() ? &it->second : nullptr;
}

void NamespaceManager::remove_container(int64_t handle) {
    containers_.erase(handle);
}

} // namespace vine
