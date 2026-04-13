package com.hexadecinull.vineos.ui.viewmodel

import android.view.KeyEvent
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.data.repository.InstanceRepository
import com.hexadecinull.vineos.domain.VineVMManager
import com.hexadecinull.vineos.native.VineRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VMDisplayViewModel @Inject constructor(
    private val vmManager: VineVMManager,
    private val instanceRepo: InstanceRepository,
) : ViewModel() {

    var isBooting by mutableStateOf(false)
        private set

    private var instanceId: String? = null
    private var instanceHandle by mutableLongStateOf(0L)
    private var statusJob: Job? = null

    fun attach(id: String) {
        instanceId = id
        statusJob = viewModelScope.launch {
            vmManager.statusFlow(id).collect { status ->
                isBooting = status == VMStatus.BOOTING
            }
        }
    }

    fun detach() {
        statusJob?.cancel()
        statusJob = null
        instanceId = null
        instanceHandle = 0L
    }

    fun onSurfaceReady(surface: Surface) {
        val handle = resolveHandle() ?: return
        VineRuntime.attachSurface(handle, surface)
        VineRuntime.startRendering(handle)
    }

    fun onSurfaceDestroyed() {
        val handle = resolveHandle() ?: return
        VineRuntime.stopRendering(handle)
        VineRuntime.detachSurface(handle)
    }

    fun sendTouch(action: Int, x: Float, y: Float) {
        val id = instanceId ?: return
        vmManager.sendTouch(id, action, x, y)
    }

    fun sendBack() = sendAndroidKey(KeyEvent.KEYCODE_BACK)

    fun sendHome() = sendAndroidKey(KeyEvent.KEYCODE_HOME)

    fun sendRecents() = sendAndroidKey(KeyEvent.KEYCODE_APP_SWITCH)

    private fun sendAndroidKey(keycode: Int) {
        val id = instanceId ?: return
        vmManager.sendKey(id, keycode, true)
        vmManager.sendKey(id, keycode, false)
    }

    private fun resolveHandle(): Long? {
        if (instanceHandle != 0L) return instanceHandle
        val id = instanceId ?: return null
        val fd = vmManager.getFramebufferFd(id)
        if (fd < 0) return null
        // The handle is the one stored in VineVMManager's handles map;
        // we retrieve it indirectly via getFramebufferFd (which opens the bridge
        // and returns the fd only when a valid handle exists). We cache the fd's
        // associated handle via a new accessor exposed on VineVMManager.
        instanceHandle = vmManager.getHandle(id) ?: return null
        return instanceHandle
    }
}
