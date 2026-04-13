package com.hexadecinull.vineos.domain

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.data.repository.InstanceRepository
import com.hexadecinull.vineos.native.VineRuntime
import com.hexadecinull.vineos.service.VineService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VineVMManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val handles = mutableMapOf<String, Long>()
    private val statusFlows = mutableMapOf<String, MutableStateFlow<VMStatus>>()

    val hostSupports32bit: Boolean by lazy { VineRuntime.hostSupportsAArch32() }

    init {
        VineRuntime.initialize(
            context.filesDir.absolutePath,
            context.applicationInfo.nativeLibraryDir,
        )
    }

    fun statusFlow(instanceId: String): StateFlow<VMStatus> =
        statusFlows.getOrPut(instanceId) { MutableStateFlow(VMStatus.STOPPED) }.asStateFlow()

    fun getHandle(instanceId: String): Long? = handles[instanceId]

    fun startInstance(instance: VMInstance) {
        scope.launch {
            val flow = statusFlows.getOrPut(instance.id) { MutableStateFlow(VMStatus.STOPPED) }
            flow.value = VMStatus.BOOTING

            ContextCompat.startForegroundService(
                context,
                Intent(context, VineService::class.java).apply { action = VineService.ACTION_START },
            )

            val handle = VineRuntime.startInstance(
                instanceId = instance.id,
                instancePath = instance.storagePath,
                ramMb = instance.ramMb,
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
            VineRuntime.stopInstance(handle)
            handles.remove(instance.id)
            statusFlows[instance.id]?.value = VMStatus.STOPPED
            stopServiceIfIdle()
        }
    }

    fun killInstance(instanceId: String) {
        scope.launch {
            val handle = handles[instanceId] ?: return@launch
            VineRuntime.killInstance(handle)
            handles.remove(instanceId)
            statusFlows[instanceId]?.value = VMStatus.STOPPED
            stopServiceIfIdle()
        }
    }

    fun getFramebufferFd(instanceId: String): Int {
        val handle = handles[instanceId] ?: return -1
        return VineRuntime.getFramebufferFd(handle)
    }

    fun sendTouch(instanceId: String, action: Int, x: Float, y: Float) {
        val handle = handles[instanceId] ?: return
        VineRuntime.sendTouchEvent(handle, action, x, y)
    }

    fun sendKey(instanceId: String, keycode: Int, down: Boolean) {
        val handle = handles[instanceId] ?: return
        VineRuntime.sendKeyEvent(handle, keycode, down)
    }

    fun getDiagnostics(instanceId: String): String {
        val handle = handles[instanceId] ?: return "Instance not running"
        return VineRuntime.getDiagnostics(handle)
    }

    private fun monitorInstance(
        instanceId: String,
        handle: Long,
        flow: MutableStateFlow<VMStatus>,
    ) = scope.launch {
        while (handles.containsKey(instanceId)) {
            delay(2000L)
            val newStatus = when (VineRuntime.getInstanceStatus(handle)) {
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
        if (handles.isEmpty()) context.stopService(Intent(context, VineService::class.java))
    }

    fun cleanup() {
        scope.cancel()
        VineRuntime.shutdown()
    }
}
