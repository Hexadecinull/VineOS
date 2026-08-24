package com.hexadecinull.vineos.native

import android.view.Surface

object VineRuntime {
    init {
        System.loadLibrary("vine_runtime")
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
}
