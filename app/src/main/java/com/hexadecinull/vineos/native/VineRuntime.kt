package com.hexadecinull.vineos.native

/**
 * VineRuntime — Kotlin-side JNI bridge to the native C++ container runtime.
 *
 * All calls that require Linux syscalls (unshare, mount, binfmt_misc, etc.)
 * go through here into vine_runtime.cpp.
 *
 * This class is a singleton loaded once per process lifetime.
 */
object VineRuntime {

    init {
        System.loadLibrary("vine_runtime")
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Initialize the native runtime. Must be called once before any VM operation.
     * Returns true on success.
     *
     * @param dataDir App's internal data directory (e.g. /data/data/com.hexadecinull.vineos/files)
     * @param nativeLibDir Path where bundled native binaries (qemu-arm, etc.) live
     */
    external fun initialize(dataDir: String, nativeLibDir: String): Boolean

    /**
     * Clean up the runtime. Call on application exit.
     */
    external fun shutdown()

    // ── Instance Management ────────────────────────────────────────────────────

    /**
     * Create a new VM instance directory structure.
     * Allocates a sparse filesystem image of [storageMb] MB.
     *
     * @return The path to the created instance directory, or null on failure.
     */
    external fun createInstance(
        instanceId: String,
        romImagePath: String,
        storageMb: Int,
    ): String?

    /**
     * Start a VM instance. Creates namespaces, mounts the rootfs, registers
     * binfmt_misc for armeabi-v7a if the host CPU lacks AArch32 support,
     * then kicks off Android init.
     *
     * @return A handle (native pointer as Long) for this running instance, or 0 on failure.
     */
    external fun startInstance(
        instanceId: String,
        instancePath: String,
        ramMb: Int,
    ): Long

    /**
     * Stop a running instance gracefully (sends SIGTERM to guest init, waits).
     * If the instance doesn't stop within the timeout, force-kills it.
     */
    external fun stopInstance(instanceHandle: Long)

    /**
     * Force-kill a running instance immediately. Use only as a last resort.
     */
    external fun killInstance(instanceHandle: Long)

    /**
     * Query the status of a running instance.
     * Returns one of: 0=stopped, 1=booting, 2=running, 3=error
     */
    external fun getInstanceStatus(instanceHandle: Long): Int

    /**
     * Delete an instance's data directory. Instance must be stopped first.
     * Returns true on success.
     */
    external fun deleteInstance(instanceId: String, instancePath: String): Boolean

    // ── 32-bit Support ────────────────────────────────────────────────────────

    /**
     * Check whether the host CPU natively supports AArch32 execution.
     * Returns false on Tensor G3, Dimensity 8400-Ultra, etc.
     * When false, VineRuntime will automatically set up QEMU user-mode emulation
     * for armeabi-v7a binaries inside the guest.
     */
    external fun hostSupportsAArch32(): Boolean

    /**
     * Register qemu-arm with binfmt_misc inside the guest namespace.
     * Called automatically by startInstance() when hostSupportsAArch32() is false.
     * Can also be called manually for testing.
     *
     * @param instanceHandle Handle from startInstance()
     * @param qemuArmPath Path to the static qemu-arm binary (in nativeLibDir)
     */
    external fun registerQemuBinfmt(instanceHandle: Long, qemuArmPath: String): Boolean

    // ── Display ────────────────────────────────────────────────────────────────

    /**
     * Get the file descriptor of the guest's virtual framebuffer.
     * The Kotlin display layer uses this to render the guest screen into a SurfaceView.
     *
     * Returns -1 if not available yet (instance still booting).
     */
    external fun getFramebufferFd(instanceHandle: Long): Int

    /**
     * Send a touch event to the guest's virtual input device.
     *
     * @param action 0=DOWN, 1=MOVE, 2=UP
     * @param x      X coordinate in guest screen space
     * @param y      Y coordinate in guest screen space
     */
    external fun sendTouchEvent(instanceHandle: Long, action: Int, x: Float, y: Float)

    /**
     * Send a key event to the guest.
     * @param keycode Android KeyEvent keycode
     * @param down    true=pressed, false=released
     */
    external fun sendKeyEvent(instanceHandle: Long, keycode: Int, down: Boolean)

    // ── Diagnostics ───────────────────────────────────────────────────────────

    /**
     * Returns a diagnostic string with namespace, mount, and QEMU state info.
     * Used by the "Show Technical Info" setting.
     */
    external fun getDiagnostics(instanceHandle: Long): String

    /**
     * Returns the VineRuntime native library version string.
     */
    external fun getRuntimeVersion(): String
}
