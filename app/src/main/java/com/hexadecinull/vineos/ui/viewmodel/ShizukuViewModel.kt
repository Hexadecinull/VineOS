package com.hexadecinull.vineos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hexadecinull.vineos.shizuku.NamespaceProbeResult
import com.hexadecinull.vineos.shizuku.ShizukuManager
import com.hexadecinull.vineos.shizuku.ShizukuStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProbeState {
    data object Idle : ProbeState
    data object Running : ProbeState
    data class Done(val result: NamespaceProbeResult) : ProbeState
    data class Failed(val message: String) : ProbeState
}

@HiltViewModel
class ShizukuViewModel @Inject constructor(private val shizukuManager: ShizukuManager) : ViewModel() {
    val status: StateFlow<ShizukuStatus> = shizukuManager.status.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = ShizukuStatus(isInstalled = false, isRunning = false, isGranted = false, serverUid = -1),
    )

    private val _probeState = MutableStateFlow<ProbeState>(ProbeState.Idle)
    val probeState: StateFlow<ProbeState> = _probeState.asStateFlow()

    fun requestPermission() = shizukuManager.requestPermission()

    fun runProbe() {
        _probeState.value = ProbeState.Running
        viewModelScope.launch {
            shizukuManager.probeNamespaceSupport().first().fold(
                onSuccess = { _probeState.value = ProbeState.Done(it) },
                onFailure = { _probeState.value = ProbeState.Failed(it.message ?: "Probe failed") },
            )
        }
    }
}
