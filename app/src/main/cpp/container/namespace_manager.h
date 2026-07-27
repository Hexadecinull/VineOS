#pragma once

#include <string>
#include <vector>
#include <optional>
#include <functional>
#include <unordered_map>
#include <sys/types.h>

namespace vine {

// Config for a single VineOS container instance.
struct ContainerConfig {
    std::string instance_id;
    std::string instance_path;      // Root of the instance data directory
    std::string rootfs_image_path;  // Path to the .vrom image file
    std::string rootfs_mount_path;  // Where the rootfs is loop-mounted
    int ram_mb = 1024;
    bool needs_qemu_32bit = false;  // True when host lacks AArch32 support
    std::string qemu_arm_path;      // Path to the static qemu-arm binary
};

enum class ContainerStatus {
    STOPPED = 0,
    BOOTING = 1,
    RUNNING = 2,
    ERROR   = 3,
    PAUSED  = 4,
};

// Manages the lifecycle of a single VineOS guest container: Linux namespace
// setup, rootfs mounting, QEMU binfmt_misc registration on arm64-only hosts,
// and launching Android init as PID 1. Not thread-safe; call from a single
// background thread (VineService's dispatcher).
class Container {
public:
    explicit Container(ContainerConfig config);
    ~Container();

    Container(const Container&) = delete;
    Container& operator=(const Container&) = delete;
    Container(Container&&) noexcept;
    Container& operator=(Container&&) noexcept;

    // Loop-mounts the rootfs, sets up bind mounts and namespaces, registers
    // binfmt_misc if needed, and execs Android init as PID 1. Returns true
    // once init has launched; does not block until the guest finishes boot.
    bool start();

    // Sends SIGTERM to init and blocks until it exits or the timeout hits.
    void stop(int timeout_ms = 10000);

    // Force-kills the container immediately.
    void kill_now();

    ContainerStatus status() const { return status_; }

    // PID of the guest init process in the host namespace, or -1 if stopped.
    pid_t init_pid() const { return init_pid_; }

    const std::string& instance_id() const { return config_.instance_id; }

    // FD of the guest virtual framebuffer, or -1 if not set up yet.
    int framebuffer_fd() const { return framebuffer_fd_; }

    // Multi-line diagnostic string: mount table, namespace info, QEMU status.
    std::string diagnostics() const;

private:
    ContainerConfig config_;
    ContainerStatus status_ = ContainerStatus::STOPPED;
    pid_t init_pid_ = -1;
    int framebuffer_fd_ = -1;

    bool mount_rootfs();
    bool setup_bind_mounts();
    bool setup_dev_nodes();
    bool setup_binfmt_misc();
    bool launch_init();
    void teardown_mounts();
};

// Global registry of active Container instances. Owns all Container objects
// and provides handle-based access for JNI.
class NamespaceManager {
public:
    static NamespaceManager& instance();

    // Must be called once from JNI_initialize().
    bool init(const std::string& data_dir, const std::string& native_lib_dir);

    void shutdown();

    // Creates and starts a new container. Returns a handle, or 0 on failure.
    int64_t start_container(const ContainerConfig& config);

    void stop_container(int64_t handle);
    void kill_container(int64_t handle);

    // Returns nullptr if the handle is invalid.
    Container* get_container(int64_t handle);

    // Removes a stopped container from the registry.
    void remove_container(int64_t handle);

    const std::string& data_dir() const { return data_dir_; }
    const std::string& native_lib_dir() const { return native_lib_dir_; }

private:
    NamespaceManager() = default;

    std::string data_dir_;
    std::string native_lib_dir_;

    // handle -> Container. handle is an incrementing int64_t, not a pointer,
    // so it can be passed safely as a JNI jlong.
    std::unordered_map<int64_t, Container> containers_;
    int64_t next_handle_ = 1;
    bool initialized_ = false;
};

} // namespace vine
