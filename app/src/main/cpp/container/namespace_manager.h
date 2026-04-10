#pragma once

#include <string>
#include <vector>
#include <optional>
#include <functional>
#include <sys/types.h>

namespace vine {

// ─── Container Config ─────────────────────────────────────────────────────────

/**
 * Configuration for a single VineOS container instance.
 */
struct ContainerConfig {
    std::string instance_id;
    std::string instance_path;      // Root of the instance data directory
    std::string rootfs_image_path;  // Path to the .vrom image file
    std::string rootfs_mount_path;  // Where the rootfs is loop-mounted (inside instance_path)
    int ram_mb = 1024;
    bool needs_qemu_32bit = false;  // Set true when host lacks AArch32 support
    std::string qemu_arm_path;      // Path to the static qemu-arm binary
};

// ─── Container Status ─────────────────────────────────────────────────────────

enum class ContainerStatus {
    STOPPED  = 0,
    BOOTING  = 1,
    RUNNING  = 2,
    ERROR    = 3,
    PAUSED   = 4,
};

// ─── Container ────────────────────────────────────────────────────────────────

/**
 * Manages the lifecycle of a single VineOS guest container.
 *
 * Implementation notes:
 * - Uses Linux namespaces (PID, mount, network, UTS) for isolation.
 * - On arm64-only hosts, sets up QEMU user-mode binfmt_misc for ARMv7 support.
 * - Android guest init is launched as PID 1 inside the PID namespace.
 * - Display is exposed via a virtual framebuffer (vfb) device.
 *
 * Thread safety: Not thread-safe. All public methods must be called from a
 * single background thread (VineService's coroutine dispatcher).
 */
class Container {
public:
    explicit Container(ContainerConfig config);
    ~Container();

    // Prevent copy (containers are move-only resources)
    Container(const Container&) = delete;
    Container& operator=(const Container&) = delete;
    Container(Container&&) noexcept;
    Container& operator=(Container&&) noexcept;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Start the container:
     * 1. Loop-mount the rootfs image
     * 2. Set up bind mounts (/proc, /sys, /dev, /dev/pts, etc.)
     * 3. Enter PID + mount namespaces via unshare()
     * 4. Set up binfmt_misc for ARMv7 if needed
     * 5. Exec Android init as PID 1 in the new namespace
     *
     * Returns true on success (init process launched). Does not block until
     * the guest is fully booted — use wait_for_boot() or poll status().
     */
    bool start();

    /**
     * Gracefully stop the container (sends SIGTERM to init PID 1).
     * Blocks until the init process exits or timeout expires.
     */
    void stop(int timeout_ms = 10000);

    /**
     * Force-kill the container immediately.
     */
    void kill_now();

    /**
     * Current container status.
     */
    ContainerStatus status() const { return status_; }

    /**
     * PID of the guest init process (in host PID namespace), or -1 if not running.
     */
    pid_t init_pid() const { return init_pid_; }

    const std::string& instance_id() const { return config_.instance_id; }

    // ── Display ──────────────────────────────────────────────────────────────

    /**
     * File descriptor of the guest virtual framebuffer.
     * -1 if the guest hasn't set up its display yet.
     */
    int framebuffer_fd() const { return framebuffer_fd_; }

    /**
     * Send a touch event into the guest's virtual input device.
     */
    void send_touch(int action, float x, float y);

    /**
     * Send a key event into the guest.
     */
    void send_key(int keycode, bool down);

    // ── Diagnostics ──────────────────────────────────────────────────────────

    /**
     * Returns a multi-line diagnostic string with mount table, namespace info,
     * QEMU registration status, and memory usage.
     */
    std::string diagnostics() const;

private:
    ContainerConfig config_;
    ContainerStatus status_ = ContainerStatus::STOPPED;
    pid_t init_pid_ = -1;
    int framebuffer_fd_ = -1;

    // ── Private implementation ────────────────────────────────────────────────

    bool mount_rootfs();
    bool setup_bind_mounts();
    bool setup_dev_nodes();
    bool setup_binfmt_misc();
    bool launch_init();
    void teardown_mounts();
};

// ─── NamespaceManager ─────────────────────────────────────────────────────────

/**
 * Global registry of all active Container instances.
 * Owns all Container objects and provides handle-based access for JNI.
 */
class NamespaceManager {
public:
    static NamespaceManager& instance();

    /**
     * Initialize the manager. Must be called once from JNI_initialize().
     */
    bool init(const std::string& data_dir, const std::string& native_lib_dir);

    /**
     * Shut down all containers and clean up.
     */
    void shutdown();

    /**
     * Create and start a new container. Returns a handle (opaque ID) or 0 on failure.
     */
    int64_t start_container(const ContainerConfig& config);

    /**
     * Stop a container by handle.
     */
    void stop_container(int64_t handle);

    /**
     * Force-kill a container by handle.
     */
    void kill_container(int64_t handle);

    /**
     * Look up a container by handle.
     * Returns nullptr if the handle is invalid.
     */
    Container* get_container(int64_t handle);

    /**
     * Remove a stopped container from the registry.
     */
    void remove_container(int64_t handle);

    const std::string& data_dir() const { return data_dir_; }
    const std::string& native_lib_dir() const { return native_lib_dir_; }

private:
    NamespaceManager() = default;

    std::string data_dir_;
    std::string native_lib_dir_;

    // handle → Container. handle is an incrementing int64_t (not a pointer,
    // for safe JNI passing as jlong).
    std::unordered_map<int64_t, Container> containers_;
    int64_t next_handle_ = 1;
    bool initialized_ = false;
};

} // namespace vine
