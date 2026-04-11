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
#include <sys/syscall.h>

#define vine_pivot_root(new_root, put_old) syscall(SYS_pivot_root, new_root, put_old)

namespace vine {

Container::Container(ContainerConfig config) : config_(std::move(config)) {}

Container::~Container() {
    if (status_ == ContainerStatus::RUNNING || status_ == ContainerStatus::BOOTING) {
        VINE_LOGW("Container %s destroyed while running — force-killing", config_.instance_id.c_str());
        kill_now();
    }
}

Container::Container(Container&& o) noexcept
    : config_(std::move(o.config_)), status_(o.status_),
      init_pid_(o.init_pid_), framebuffer_fd_(o.framebuffer_fd_) {
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

bool Container::start() {
    VINE_LOGI("Container::start() — %s", config_.instance_id.c_str());
    status_ = ContainerStatus::BOOTING;

    if (!mkdirs(config_.rootfs_mount_path)) {
        VINE_LOGE("Failed to create rootfs mount point: %s", config_.rootfs_mount_path.c_str());
        status_ = ContainerStatus::ERROR;
        return false;
    }
    if (!mount_rootfs()) { status_ = ContainerStatus::ERROR; return false; }
    if (!setup_bind_mounts()) { teardown_mounts(); status_ = ContainerStatus::ERROR; return false; }
    if (!setup_dev_nodes()) { teardown_mounts(); status_ = ContainerStatus::ERROR; return false; }

    if (config_.needs_qemu_32bit && !setup_binfmt_misc()) {
        VINE_LOGW("binfmt_misc setup failed — 32-bit apps will not work");
    }

    if (!launch_init()) { teardown_mounts(); status_ = ContainerStatus::ERROR; return false; }

    VINE_LOGI("Container %s started, init PID=%d", config_.instance_id.c_str(), init_pid_);
    status_ = ContainerStatus::RUNNING;
    return true;
}

void Container::stop(int timeout_ms) {
    if (init_pid_ > 0) {
        terminate_gracefully(init_pid_, timeout_ms);
        init_pid_ = -1;
    }
    teardown_mounts();
    if (framebuffer_fd_ >= 0) { close(framebuffer_fd_); framebuffer_fd_ = -1; }
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

bool Container::mount_rootfs() {
    int loop_ctl_fd = open("/dev/loop-control", O_RDWR | O_CLOEXEC);
    if (loop_ctl_fd < 0) { VINE_LOGE_ERRNO("open(/dev/loop-control)"); return false; }

    int loop_num = ioctl(loop_ctl_fd, LOOP_CTL_GET_FREE);
    close(loop_ctl_fd);
    if (loop_num < 0) { VINE_LOGE("LOOP_CTL_GET_FREE failed: %s", strerror(errno)); return false; }

    std::string loop_dev = "/dev/loop" + std::to_string(loop_num);

    int img_fd = open(config_.rootfs_image_path.c_str(), O_RDWR | O_CLOEXEC);
    if (img_fd < 0) img_fd = open(config_.rootfs_image_path.c_str(), O_RDONLY | O_CLOEXEC);
    if (img_fd < 0) { VINE_LOGE_ERRNO("open(rootfs_image)"); return false; }

    int loop_fd = open(loop_dev.c_str(), O_RDWR | O_CLOEXEC);
    if (loop_fd < 0) { VINE_LOGE_ERRNO("open(loop_dev)"); close(img_fd); return false; }

    if (ioctl(loop_fd, LOOP_SET_FD, img_fd) < 0) {
        VINE_LOGE_ERRNO("LOOP_SET_FD");
        close(loop_fd); close(img_fd);
        return false;
    }
    close(img_fd);

    struct loop_info64 li{};
    li.lo_flags = LO_FLAGS_AUTOCLEAR;
    ioctl(loop_fd, LOOP_SET_STATUS64, &li);
    close(loop_fd);

    if (mount(loop_dev.c_str(), config_.rootfs_mount_path.c_str(), "ext4", MS_RELATIME, "errors=remount-ro") != 0) {
        if (mount(loop_dev.c_str(), config_.rootfs_mount_path.c_str(), "squashfs", MS_RDONLY, nullptr) != 0) {
            VINE_LOGE_ERRNO("mount(rootfs)");
            return false;
        }
    }
    return true;
}

bool Container::setup_bind_mounts() {
    const std::string root = config_.rootfs_mount_path;

    struct MountEntry { const char* src; const char* suffix; const char* fs; unsigned long flags; const char* data; };
    const MountEntry entries[] = {
        { "proc",    "/proc",        "proc",    MS_NODEV|MS_NOEXEC|MS_NOSUID, nullptr },
        { "sysfs",   "/sys",         "sysfs",   MS_NODEV|MS_NOEXEC|MS_NOSUID, nullptr },
        { "tmpfs",   "/dev",         "tmpfs",   MS_NOSUID,                     "size=65536k,mode=755" },
        { "devpts",  "/dev/pts",     "devpts",  MS_NOEXEC|MS_NOSUID,           "newinstance,ptmxmode=0666,mode=0620" },
        { "tmpfs",   "/dev/shm",     "tmpfs",   MS_NOSUID|MS_NODEV,            "size=65536k" },
        { "tmpfs",   "/tmp",         "tmpfs",   MS_NOSUID|MS_NODEV,            "size=32768k,mode=1777" },
        { "cgroup",  "/sys/fs/cgroup", "cgroup", MS_NODEV|MS_NOEXEC|MS_NOSUID, "all" },
    };

    for (const auto& e : entries) {
        std::string target = root + e.suffix;
        mkdirs(target);
        if (mount(e.src, target.c_str(), e.fs, e.flags, e.data) != 0) {
            VINE_LOGW("mount(%s → %s): %s", e.src, target.c_str(), strerror(errno));
        }
    }

    std::string guest_binder = root + "/dev/binder";
    int bfd = open(guest_binder.c_str(), O_CREAT | O_WRONLY | O_CLOEXEC, 0600);
    if (bfd >= 0) close(bfd);
    if (mount("/dev/binder", guest_binder.c_str(), nullptr, MS_BIND, nullptr) != 0) {
        VINE_LOGW("bind-mount /dev/binder: %s", strerror(errno));
    }
    return true;
}

bool Container::setup_dev_nodes() {
    const std::string dev = config_.rootfs_mount_path + "/dev";
    struct DevNode { const char* name; mode_t mode; dev_t rdev; };
    const DevNode nodes[] = {
        { "null",    S_IFCHR|0666, makedev(1,3) },
        { "zero",    S_IFCHR|0666, makedev(1,5) },
        { "full",    S_IFCHR|0666, makedev(1,7) },
        { "random",  S_IFCHR|0666, makedev(1,8) },
        { "urandom", S_IFCHR|0666, makedev(1,9) },
        { "tty",     S_IFCHR|0666, makedev(5,0) },
        { "console", S_IFCHR|0600, makedev(5,1) },
        { "ptmx",    S_IFCHR|0666, makedev(5,2) },
    };
    for (const auto& n : nodes) {
        std::string path = dev + "/" + n.name;
        if (mknod(path.c_str(), n.mode, n.rdev) != 0 && errno != EEXIST) {
            VINE_LOGW("mknod(%s): %s", path.c_str(), strerror(errno));
        }
    }
    symlink("/proc/self/fd",   (dev + "/fd").c_str());
    symlink("/proc/self/fd/0", (dev + "/stdin").c_str());
    symlink("/proc/self/fd/1", (dev + "/stdout").c_str());
    symlink("/proc/self/fd/2", (dev + "/stderr").c_str());
    return true;
}

bool Container::setup_binfmt_misc() {
    if (config_.qemu_arm_path.empty() || !path_exists(config_.qemu_arm_path)) {
        VINE_LOGE("qemu-arm not found at: %s", config_.qemu_arm_path.c_str());
        return false;
    }

    std::string guest_qemu = config_.rootfs_mount_path + "/system/bin/qemu-arm";
    int fd = open(guest_qemu.c_str(), O_CREAT | O_WRONLY | O_CLOEXEC, 0755);
    if (fd >= 0) close(fd);
    if (mount(config_.qemu_arm_path.c_str(), guest_qemu.c_str(), nullptr, MS_BIND | MS_RDONLY, nullptr) != 0) {
        VINE_LOGE_ERRNO("bind-mount qemu-arm");
        return false;
    }

    const std::string binfmt_path = "/proc/sys/fs/binfmt_misc";
    if (!path_exists(binfmt_path + "/register")) {
        if (mount("binfmt_misc", binfmt_path.c_str(), "binfmt_misc", MS_NODEV|MS_NOEXEC|MS_NOSUID, nullptr) != 0) {
            VINE_LOGE_ERRNO("mount(binfmt_misc)");
            return false;
        }
    }

    // 'F' flag: kernel opens the interpreter fd at registration time so it
    // works after pivot_root changes the filesystem root.
    const std::string rule =
        ":arm:M::"
        "\\x7fELF\\x01\\x01\\x01\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x00\\x02\\x00\\x28\\x00"
        ":"
        "\\xff\\xff\\xff\\xff\\xff\\xff\\xff\\x00\\xff\\xff\\xff\\xff\\xff\\xff\\xff\\xff\\xfe\\xff\\xff\\xff"
        ":" + config_.qemu_arm_path + ":F";

    if (!write_file(binfmt_path + "/register", rule)) {
        if (path_exists(binfmt_path + "/arm")) return true;
        VINE_LOGE("Failed to register ARMv7 binfmt_misc rule");
        return false;
    }
    VINE_LOGI("ARMv7 binfmt_misc registered → %s", config_.qemu_arm_path.c_str());
    return true;
}

bool Container::launch_init() {
    const std::string init_path = config_.rootfs_mount_path + "/init";
    if (!path_exists(init_path)) {
        VINE_LOGE("Android init not found at %s", init_path.c_str());
        return false;
    }

    pid_t pid = fork();
    if (pid < 0) { VINE_LOGE_ERRNO("fork"); return false; }

    if (pid == 0) {
        int ns_flags = CLONE_NEWPID | CLONE_NEWNS | CLONE_NEWUTS | CLONE_NEWIPC;
        if (unshare(ns_flags) != 0) { VINE_LOGE_ERRNO("unshare"); _exit(1); }

        const std::string hostname = "vine-" + config_.instance_id.substr(0, 8);
        sethostname(hostname.c_str(), hostname.size());

        if (mount("none", "/", nullptr, MS_REC | MS_PRIVATE, nullptr) != 0) {
            VINE_LOGE_ERRNO("MS_PRIVATE"); _exit(1);
        }

        const std::string put_old = config_.rootfs_mount_path + "/.old_root";
        mkdirs(put_old);
        if (vine_pivot_root(config_.rootfs_mount_path.c_str(), put_old.c_str()) != 0) {
            VINE_LOGE_ERRNO("pivot_root");
            if (chroot(config_.rootfs_mount_path.c_str()) != 0) {
                VINE_LOGE_ERRNO("chroot fallback"); _exit(1);
            }
        } else {
            umount2("/.old_root", MNT_DETACH);
            rmdir("/.old_root");
        }
        chdir("/");

        execl("/init", "/init",
              "androidboot.hardware=vine",
              "androidboot.selinux=permissive",
              (char*)nullptr);
        VINE_LOGE_ERRNO("execl(/init)");
        _exit(1);
    }

    init_pid_ = pid;
    VINE_LOGI("Guest init launched, host PID=%d", init_pid_);
    return true;
}

void Container::teardown_mounts() {
    const std::string root = config_.rootfs_mount_path;
    const char* suffixes[] = {
        "/sys/fs/cgroup", "/dev/pts", "/dev/shm", "/dev",
        "/sys", "/proc", "/system/bin/qemu-arm",
    };
    for (const char* s : suffixes) {
        umount2((root + s).c_str(), MNT_DETACH);
    }
    umount2(root.c_str(), MNT_DETACH);
}

void Container::send_touch(int action, float x, float y) {
    VINE_LOGD("send_touch: action=%d x=%.0f y=%.0f (Phase 2)", action, x, y);
}

void Container::send_key(int keycode, bool down) {
    VINE_LOGD("send_key: keycode=%d down=%d (Phase 2)", keycode, (int)down);
}

std::string Container::diagnostics() const {
    std::string out = "=== VineOS Diagnostics ===\n";
    out += "Instance : " + config_.instance_id + "\n";
    out += "Status   : " + std::to_string((int)status_) + "\n";
    out += "Init PID : " + std::to_string(init_pid_) + "\n";
    out += "QEMU     : " + std::string(config_.needs_qemu_32bit ? "yes" : "no") + "\n";
    if (init_pid_ > 0) {
        auto s = read_file("/proc/" + std::to_string(init_pid_) + "/status");
        if (s) out += "\n--- proc/status ---\n" + *s;
    }
    return out;
}

NamespaceManager& NamespaceManager::instance() {
    static NamespaceManager mgr;
    return mgr;
}

bool NamespaceManager::init(const std::string& data_dir, const std::string& native_lib_dir) {
    data_dir_ = data_dir;
    native_lib_dir_ = native_lib_dir;
    initialized_ = true;
    VINE_LOGI("NamespaceManager initialized. data=%s libs=%s", data_dir.c_str(), native_lib_dir.c_str());
    return true;
}

void NamespaceManager::shutdown() {
    for (auto& [handle, container] : containers_) container.stop(5000);
    containers_.clear();
    initialized_ = false;
}

int64_t NamespaceManager::start_container(const ContainerConfig& config) {
    if (!initialized_) { VINE_LOGE("NamespaceManager not initialized"); return 0; }
    int64_t handle = next_handle_++;
    auto [it, ok] = containers_.emplace(handle, Container(config));
    if (!ok) return 0;
    if (!it->second.start()) { containers_.erase(it); return 0; }
    return handle;
}

void NamespaceManager::stop_container(int64_t handle) {
    auto it = containers_.find(handle);
    if (it != containers_.end()) it->second.stop();
}

void NamespaceManager::kill_container(int64_t handle) {
    auto it = containers_.find(handle);
    if (it != containers_.end()) it->second.kill_now();
}

Container* NamespaceManager::get_container(int64_t handle) {
    auto it = containers_.find(handle);
    return it != containers_.end() ? &it->second : nullptr;
}

void NamespaceManager::remove_container(int64_t handle) {
    containers_.erase(handle);
}

} // namespace vine
