package com.ypsilonos.android.domain

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.ypsilonos.android.data.models.AbiCompat
import com.ypsilonos.android.data.models.ROMImage
import com.ypsilonos.android.data.models.VMInstance
import com.ypsilonos.android.data.models.VMStatus
import com.ypsilonos.android.native.YpsilonRuntime
import com.ypsilonos.android.service.YpsilonService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class YpsilonVMManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val handles = mutableMapOf<String, Long>()
    private val statusFlows = mutableMapOf<String, MutableStateFlow<VMStatus>>()

    val hostAbis: List<String> = Build.SUPPORTED_ABIS.toList()
    val hostSupports32bit: Boolean by lazy { YpsilonRuntime.hostSupportsAArch32() }

    init {
        YpsilonRuntime.initialize(
            context.filesDir.absolutePath,
            context.applicationInfo.nativeLibraryDir,
        )
    }

    fun isRomCompatible(rom: ROMImage): Boolean = AbiCompat.romRunMode(rom, hostAbis) != AbiCompat.RunMode.UNAVAILABLE

    fun statusFlow(instanceId: String): StateFlow<VMStatus> =
        statusFlows.getOrPut(instanceId) { MutableStateFlow(VMStatus.STOPPED) }.asStateFlow()

    fun getHandle(instanceId: String): Long? = handles[instanceId]

    fun startInstance(instance: VMInstance) {
        scope.launch {
            val flow = statusFlows.getOrPut(instance.id) { MutableStateFlow(VMStatus.STOPPED) }
            flow.value = VMStatus.BOOTING

            ContextCompat.startForegroundService(
                context,
                Intent(context, YpsilonService::class.java).apply { action = YpsilonService.ACTION_START },
            )

            val handle = YpsilonRuntime.startInstance(
                instanceId = instance.id,
                instancePath = instance.storagePath,
                ramMb = instance.ramMb,
                cpuCores = instance.cpuCores,
            )

            if (handle == 0L) {
                flow.value = VMStatus.ERROR
                stopServiceIfIdle()
                return@launch
            }

            handles[instance.id] = handle
            flow.value = VMStatus.RUNNING
            monitorInstance(instance.id, handle, flow)
        }
    }

    fun stopInstance(instance: VMInstance) {
        scope.launch {
            val handle = handles[instance.id] ?: return@launch
            YpsilonRuntime.stopInstance(handle)
            handles.remove(instance.id)
            statusFlows[instance.id]?.value = VMStatus.STOPPED
            stopServiceIfIdle()
        }
    }

    fun killInstance(instanceId: String) {
        scope.launch {
            val handle = handles[instanceId] ?: return@launch
            YpsilonRuntime.killInstance(handle)
            handles.remove(instanceId)
            statusFlows[instanceId]?.value = VMStatus.STOPPED
            stopServiceIfIdle()
        }
    }

    fun getFramebufferFd(instanceId: String): Int {
        val handle = handles[instanceId] ?: return -1
        return YpsilonRuntime.getFramebufferFd(handle)
    }

    fun sendTouch(instanceId: String, action: Int, x: Float, y: Float) {
        val handle = handles[instanceId] ?: return
        YpsilonRuntime.sendTouchEvent(handle, action, x, y)
    }

    fun sendKey(instanceId: String, keycode: Int, down: Boolean) {
        val handle = handles[instanceId] ?: return
        YpsilonRuntime.sendKeyEvent(handle, keycode, down)
    }

    fun getDiagnostics(instanceId: String): String {
        val handle = handles[instanceId] ?: return "Instance not running"
        return YpsilonRuntime.getDiagnostics(handle)
    }

    // --- YpsilonOS compatibility / low-end controls ---

    fun spoofBuildProps(instanceId: String, props: Map<String, String>): Boolean {
        val handle = handles[instanceId] ?: return false
        return YpsilonRuntime.spoofBuildProps(handle, props)
    }

    fun restoreBuildProps(instanceId: String): Boolean {
        val handle = handles[instanceId] ?: return false
        return YpsilonRuntime.restoreBuildProps(handle)
    }

    fun setSELinux(enforcing: Boolean): Boolean = YpsilonRuntime.setSELinux(enforcing)

    fun hideRoot(instanceId: String): Boolean {
        val handle = handles[instanceId] ?: return false
        return YpsilonRuntime.hideRoot(handle)
    }

    fun restoreRoot(instanceId: String): Boolean {
        val handle = handles[instanceId] ?: return false
        return YpsilonRuntime.restoreRoot(handle)
    }

    fun setRenderScale(instanceId: String, scale: Float): Boolean {
        val handle = handles[instanceId] ?: return false
        return YpsilonRuntime.setRenderScale(handle, scale)
    }

    fun getAvailableRamMb(): Long = YpsilonRuntime.getAvailableRamMb()

    fun getDeviceFingerprint(): String = YpsilonRuntime.getDeviceFingerprint()

    // --- Safety / lifecycle features ---

    // Toggle network isolation for the guest (blocks guest network access).
    fun setNetworkIsolated(instanceId: String, isolated: Boolean): Boolean {
        val handle = handles[instanceId] ?: return false
        return YpsilonRuntime.setNetworkIsolated(handle, isolated)
    }

    // Suspend a running instance (freeze, keep memory). Returns false if not running.
    fun suspendInstance(instanceId: String): Boolean {
        val handle = handles[instanceId] ?: return false
        return YpsilonRuntime.suspendInstance(handle)
    }

    // Resume a suspended instance.
    fun resumeInstance(instanceId: String): Boolean {
        val handle = handles[instanceId] ?: return false
        return YpsilonRuntime.resumeInstance(handle)
    }

    private fun monitorInstance(instanceId: String, handle: Long, flow: MutableStateFlow<VMStatus>) = scope.launch {
        while (handles.containsKey(instanceId)) {
            delay(2000L)
            val newStatus = when (YpsilonRuntime.getInstanceStatus(handle)) {
                0 -> VMStatus.STOPPED
                1 -> VMStatus.BOOTING
                2 -> VMStatus.RUNNING
                else -> VMStatus.ERROR
            }
            if (newStatus != flow.value) flow.value = newStatus
            if (newStatus == VMStatus.STOPPED || newStatus == VMStatus.ERROR) {
                handles.remove(instanceId)
                stopServiceIfIdle()
                break
            }
        }
    }

    private fun stopServiceIfIdle() {
        if (handles.isEmpty()) context.stopService(Intent(context, YpsilonService::class.java))
    }

    fun cleanup() {
        scope.cancel()
        YpsilonRuntime.shutdown()
    }
}
