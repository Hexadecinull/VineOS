package com.hexadecinull.vineos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.data.repository.InstanceRepository
import com.hexadecinull.vineos.domain.VineVMManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val instances: List<VMInstance> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val instanceRepo: InstanceRepository,
    private val vmManager: VineVMManager,
) : ViewModel() {

    private val errorFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = instanceRepo.observeAll()
        .combine(errorFlow) { instances, error ->
            HomeUiState(instances = instances, isLoading = false, error = error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    val hostSupports32bit: Boolean get() = vmManager.hostSupports32bit

    fun launchInstance(instance: VMInstance) {
        viewModelScope.launch {
            instanceRepo.updateStatus(instance.id, VMStatus.BOOTING)
            instanceRepo.touchLastUsed(instance.id)
            vmManager.startInstance(instance)
        }
    }

    fun stopInstance(instance: VMInstance) {
        viewModelScope.launch {
            vmManager.stopInstance(instance)
            instanceRepo.updateStatus(instance.id, VMStatus.STOPPED)
        }
    }

    fun deleteInstance(instance: VMInstance) {
        viewModelScope.launch {
            if (instance.status != VMStatus.STOPPED) {
                vmManager.killInstance(instance.id)
            }
            instanceRepo.delete(instance)
        }
    }

    fun clearError() {
        errorFlow.value = null
    }
}
