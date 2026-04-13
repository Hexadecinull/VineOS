package com.hexadecinull.vineos.native

import android.view.Surface

object VineRuntime {
    init {
        System.loadLibrary("vine_runtime")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    external fun initialize(dataDir: String, nativeLibDir: String): Boolean

    external fun shutdown()

    // ── Instance management ───────────────────────────────────────────────────

    external fun createInstance(
        instanceId: String,
        romImagePath: String,
        storageMb: Int,
    ): String?

    external fun startInstance(
        instanceId: String,
        instancePath: String,
        ramMb: Int,
    ): Long

    external fun stopInstance(instanceHandle: Long)

    external fun killInstance(instanceHandle: Long)

    external fun getInstanceStatus(instanceHandle: Long): Int

    external fun deleteInstance(instanceId: String, instancePath: String): Boolean

    // ── 32-bit / QEMU ─────────────────────────────────────────────────────────

    external fun hostSupportsAArch32(): Boolean

    external fun registerQemuBinfmt(instanceHandle: Long, qemuArmPath: String): Boolean

    // ── Display ───────────────────────────────────────────────────────────────

    /**
     * Returns the file descriptor of the guest's virtual framebuffer device.
     * -1 if the framebuffer is not yet open.
     */
    external fun getFramebufferFd(instanceHandle: Long): Int

    /**
     * Attach an Android Surface to the framebuffer bridge so the native render
     * loop can blit guest frames into it. Call after the SurfaceView is ready.
     */
    external fun attachSurface(instanceHandle: Long, surface: Surface)

    /**
     * Detach the Surface (e.g. when the SurfaceView is destroyed). Stops the
     * render loop and releases the ANativeWindow reference.
     */
    external fun detachSurface(instanceHandle: Long)

    /**
     * Start the 60fps render loop for this instance. Requires a Surface to have
     * been attached via attachSurface() first.
     */
    external fun startRendering(instanceHandle: Long): Boolean

    /**
     * Stop the render loop (e.g. when the user minimizes the VM display).
     */
    external fun stopRendering(instanceHandle: Long)

    // ── Input ─────────────────────────────────────────────────────────────────

    external fun sendTouchEvent(instanceHandle: Long, action: Int, x: Float, y: Float)

    external fun sendKeyEvent(instanceHandle: Long, keycode: Int, down: Boolean)

    // ── Diagnostics ───────────────────────────────────────────────────────────

    external fun getDiagnostics(instanceHandle: Long): String

    external fun getRuntimeVersion(): String
}
