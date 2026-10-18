package com.ypsilonos.android.native

import android.view.Surface

object YpsilonRuntime {
    init {
        System.loadLibrary("ypsilon_runtime")
    }

    external fun initialize(dataDir: String, nativeLibDir: String): Boolean

    external fun shutdown()

    external fun createInstance(instanceId: String, romImagePath: String, storageMb: Int): String?

    external fun startInstance(instanceId: String, instancePath: String, ramMb: Int, cpuCores: Int): Long

    external fun stopInstance(instanceHandle: Long)

    external fun killInstance(instanceHandle: Long)

    external fun getInstanceStatus(instanceHandle: Long): Int

    external fun deleteInstance(instanceId: String, instancePath: String): Boolean

    external fun hostSupportsAArch32(): Boolean

    // Returns -1 if the framebuffer is not yet open.
    external fun getFramebufferFd(instanceHandle: Long): Int

    // Attach an Android Surface to receive guest frames. Call after SurfaceView is ready.
    external fun attachSurface(instanceHandle: Long, surface: Surface)

    // Detach the Surface (e.g. when the SurfaceView is destroyed).
    external fun detachSurface(instanceHandle: Long)

    // Start the render loop. Requires attachSurface() to have been called first.
    external fun startRendering(instanceHandle: Long): Boolean

    external fun stopRendering(instanceHandle: Long)

    external fun sendTouchEvent(instanceHandle: Long, action: Int, x: Float, y: Float)

    external fun sendKeyEvent(instanceHandle: Long, keycode: Int, down: Boolean)

    external fun getDiagnostics(instanceHandle: Long): String

    external fun getRuntimeVersion(): String

    // --- YpsilonOS compatibility / low-end controls ---

    external fun spoofBuildProps(instanceHandle: Long, props: Map<String, String>): Boolean

    external fun restoreBuildProps(instanceHandle: Long): Boolean

    external fun setSELinux(enforcing: Boolean): Boolean

    external fun hideRoot(instanceHandle: Long): Boolean

    external fun restoreRoot(instanceHandle: Long): Boolean

    external fun setRenderScale(instanceHandle: Long, scale: Float): Boolean

    external fun getAvailableRamMb(): Long

    external fun getDeviceFingerprint(): String

    // --- Safety / lifecycle ---

    external fun setNetworkIsolated(instanceHandle: Long, isolated: Boolean): Boolean

    external fun suspendInstance(instanceHandle: Long): Boolean

    external fun resumeInstance(instanceHandle: Long): Boolean
}
