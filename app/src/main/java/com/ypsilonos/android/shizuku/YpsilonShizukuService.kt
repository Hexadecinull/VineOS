package com.ypsilonos.android.shizuku

import android.content.Context
import androidx.annotation.Keep

// Instantiated by Shizuku's server inside the shell (ADB) or root process, not YpsilonOS's own app process; both constructors and System.loadLibrary must stay reachable after ProGuard, hence @Keep
class YpsilonShizukuService : IYpsilonShizukuService.Stub {
    constructor() : super()

    @Keep
    constructor(context: Context) : this()

    override fun destroy() {
        System.exit(0)
    }

    override fun exit() {
        destroy()
    }

    override fun probeNamespaces(): String = nativeProbeNamespaces()

    private external fun nativeProbeNamespaces(): String

    companion object {
        init {
            System.loadLibrary("ypsilon_runtime")
        }
    }
}
