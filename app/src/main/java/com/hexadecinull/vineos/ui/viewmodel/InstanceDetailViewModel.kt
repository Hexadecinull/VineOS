package com.hexadecinull.vineos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.data.repository.InstanceRepository
import com.hexadecinull.vineos.domain.VineVMManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InstanceDetailUiState(val instance: VMInstance? = null, val diagnostics: String = "")

@HiltViewModel
class InstanceDetailViewModel @Inject constructor(private val instanceRepo: InstanceRepository, private val vmManager: VineVMManager) :
    ViewModel() {
    private val instanceIdFlow = MutableStateFlow("")
    private val diagnosticsFlow = MutableStateFlow("")

    // No grace period on uiState: sources are already-cached hot flows, so a rebuild on resubscribe is cheap
    val uiState: StateFlow<InstanceDetailUiState> = combine(
        instanceIdFlow,
        instanceRepo.observeAll(),
        diagnosticsFlow,
    ) { id, all, diagnostics ->
        InstanceDetailUiState(instance = all.find { it.id == id }, diagnostics = diagnostics)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), InstanceDetailUiState())

    fun load(id: String) {
        instanceIdFlow.value = id
    }

    fun launch(instance: VMInstance) {
        viewModelScope.launch {
            instanceRepo.updateStatus(instance.id, VMStatus.BOOTING)
            instanceRepo.touchLastUsed(instance.id)
            vmManager.startInstance(instance)
        }
    }

    fun stop(instance: VMInstance) {
        viewModelScope.launch {
            vmManager.stopInstance(instance)
            instanceRepo.updateStatus(instance.id, VMStatus.STOPPED)
        }
    }

    fun delete(instance: VMInstance) {
        viewModelScope.launch {
            if (instance.status != VMStatus.STOPPED) vmManager.killInstance(instance.id)
            instanceRepo.delete(instance)
        }
    }

    fun refreshDiagnostics() {
        diagnosticsFlow.value = vmManager.getDiagnostics(instanceIdFlow.value)
    }
}
