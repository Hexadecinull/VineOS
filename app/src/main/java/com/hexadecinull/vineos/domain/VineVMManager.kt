package com.hexadecinull.vineos.domain

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.native.VineRuntime
import com.hexadecinull.vineos.service.VineService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VineVMManager — the central domain-layer coordinator for VM lifecycle.
 *
 * Responsibilities:
 * - Bridging the Kotlin/Compose UI layer ↔ the native JNI runtime (VineRuntime)
 * - Starting / stopping / monitoring VM instances
 * - Tracking running handles (JNI long → VMInstance mapping)
 * - Starting/stopping VineService as the first/last VM starts/stops
 *
 * This class is a Hilt singleton injected into ViewModels.
 */
@Singleton
class VineVMManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Map from VMInstance.id → native handle returned by VineRuntime.startInstance()
    private val handles = mutableMapOf<String, Long>()

    // Flows exposing per-instance status updates to the UI
    private val _statusFlows = mutableMapOf<String, MutableStateFlow<VMStatus>>()

    // ── Initialization ────────────────────────────────────────────────────────

    init {
        val filesDir = context.filesDir.absolutePath
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val ok = VineRuntime.initialize(filesDir, nativeLibDir)
        if (!ok) {
            // Log but don't crash — runtime logs the specific failure reason
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a StateFlow tracking the VMStatus of a given instance.
     * Creates the flow on first access.
     */
    fun statusFlow(instanceId: String): StateFlow<VMStatus> {
        return _statusFlows.getOrPut(instanceId) {
            MutableStateFlow(VMStatus.STOPPED)
        }.asStateFlow()
    }

    /**
     * Whether the host CPU supports AArch32 (32-bit ARM) execution natively.
     * If false, QEMU user-mode will be used for 32-bit apps inside the VM.
     */
    val hostSupports32bit: Boolean by lazy {
        VineRuntime.hostSupportsAArch32()
    }

    /**
     * Start a VM instance.
     * Launches the native container, starts VineService, and polls for boot completion.
     */
    fun startInstance(instance: VMInstance) {
        scope.launch {
            val statusFlow = _statusFlows.getOrPut(instance.id) {
                MutableStateFlow(VMStatus.STOPPED)
            }
            statusFlow.value = VMStatus.BOOTING

            // Ensure VineService is running (keeps the process alive when backgrounded)
            ContextCompat.startForegroundService(
                context,
                Intent(context, VineService::class.java).apply {
                    action = VineService.ACTION_START
                }
            )

            val handle = VineRuntime.startInstance(
                instanceId = instance.id,
                instancePath = instance.storagePath,
                ramMb = instance.ramMb,
            )

            if (handle == 0L) {
                statusFlow.value = VMStatus.ERROR
                stopServiceIfIdle()
                return@launch
            }

            handles[instance.id] = handle
            statusFlow.value = VMStatus.RUNNING

            // Poll native status in the background to detect crashes
            monitorInstance(instance.id, handle, statusFlow)
        }
    }

    /**
     * Stop a VM instance gracefully.
     */
    fun stopInstance(instance: VMInstance) {
        scope.launch {
            val handle = handles[instance.id] ?: return@launch
            VineRuntime.stopInstance(handle)
            handles.remove(instance.id)
            _statusFlows[instance.id]?.value = VMStatus.STOPPED
            stopServiceIfIdle()
        }
    }

    /**
     * Force-kill a running instance.
     */
    fun killInstance(instanceId: String) {
        scope.launch {
            val handle = handles[instanceId] ?: return@launch
            VineRuntime.killInstance(handle)
            handles.remove(instanceId)
            _statusFlows[instanceId]?.value = VMStatus.STOPPED
            stopServiceIfIdle()
        }
    }

    /**
     * Returns the framebuffer FD for the given running instance,
     * or -1 if not yet available.
     */
    fun getFramebufferFd(instanceId: String): Int {
        val handle = handles[instanceId] ?: return -1
        return VineRuntime.getFramebufferFd(handle)
    }

    /**
     * Forward a touch event to the running VM instance.
     */
    fun sendTouch(instanceId: String, action: Int, x: Float, y: Float) {
        val handle = handles[instanceId] ?: return
        VineRuntime.sendTouchEvent(handle, action, x, y)
    }

    /**
     * Forward a key event to the running VM instance.
     */
    fun sendKey(instanceId: String, keycode: Int, down: Boolean) {
        val handle = handles[instanceId] ?: return
        VineRuntime.sendKeyEvent(handle, keycode, down)
    }

    /**
     * Get diagnostic string for a running instance.
     */
    fun getDiagnostics(instanceId: String): String {
        val handle = handles[instanceId] ?: return "Instance not running"
        return VineRuntime.getDiagnostics(handle)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Polls the native status of a running container and updates the Kotlin
     * StateFlow accordingly. Detects crashes.
     */
    private fun monitorInstance(
        instanceId: String,
        handle: Long,
        statusFlow: MutableStateFlow<VMStatus>,
    ) = scope.launch {
        while (handles.containsKey(instanceId)) {
            delay(2000L) // Poll every 2 seconds
            val nativeStatus = VineRuntime.getInstanceStatus(handle)
            val newStatus = when (nativeStatus) {
                0 -> VMStatus.STOPPED
                1 -> VMStatus.BOOTING
                2 -> VMStatus.RUNNING
                3 -> VMStatus.ERROR
                else -> VMStatus.STOPPED
            }
            if (newStatus != statusFlow.value) {
                statusFlow.value = newStatus
            }
            // If the container stopped unexpectedly, clean up
            if (newStatus == VMStatus.STOPPED || newStatus == VMStatus.ERROR) {
                handles.remove(instanceId)
                stopServiceIfIdle()
                break
            }
        }
    }

    /**
     * Stops VineService if no VM instances are currently running.
     */
    private fun stopServiceIfIdle() {
        if (handles.isEmpty()) {
            context.stopService(Intent(context, VineService::class.java))
        }
    }

    fun cleanup() {
        scope.cancel()
        VineRuntime.shutdown()
    }
}
