package com.hexadecinull.vineos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hexadecinull.vineos.data.models.ROMImage
import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.data.repository.AppPreferences
import com.hexadecinull.vineos.data.repository.InstanceRepository
import com.hexadecinull.vineos.native.VineRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class CreateInstanceState {
    data object Idle : CreateInstanceState()

    data object Creating : CreateInstanceState()

    data class Success(val instanceId: String) : CreateInstanceState()

    data class Error(val message: String) : CreateInstanceState()
}

@HiltViewModel
class CreateInstanceViewModel @Inject constructor(
    private val instanceRepo: InstanceRepository,
    private val prefs: AppPreferences,
) : ViewModel() {
    private val stateFlow = MutableStateFlow<CreateInstanceState>(CreateInstanceState.Idle)
    val state: StateFlow<CreateInstanceState> = stateFlow.asStateFlow()

    val instanceName = MutableStateFlow("")
    val selectedRamMb = MutableStateFlow(AppPreferences.DEFAULT_RAM_MB)
    val selectedStorageMb = MutableStateFlow(AppPreferences.DEFAULT_STORAGE_MB)
    val selectedEmoji = MutableStateFlow("🟢")

    val isFormValid: StateFlow<Boolean> = instanceName
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            selectedRamMb.value = prefs.defaultRamMb.first()
            selectedStorageMb.value = prefs.defaultStorageMb.first()
        }
    }

    fun createInstance(rom: ROMImage) {
        if (stateFlow.value is CreateInstanceState.Creating) return
        stateFlow.value = CreateInstanceState.Creating

        viewModelScope.launch {
            val name = instanceName.value.trim()
            val ram = selectedRamMb.value
            val storage = selectedStorageMb.value

            val instancePath = VineRuntime.createInstance(
                instanceId = "",
                romImagePath = rom.localPath ?: "",
                storageMb = storage,
            )

            if (instancePath == null) {
                stateFlow.value = CreateInstanceState.Error(
                    "Failed to create instance storage. Check available disk space.",
                )
                return@launch
            }

            val instanceId = File(instancePath).name
            val instance = VMInstance(
                id = instanceId,
                name = name,
                romId = rom.id,
                romVersion = rom.androidVersion,
                storagePath = instancePath,
                status = VMStatus.STOPPED,
                ramMb = ram,
                storageMb = storage,
                androidVersionDisplay = rom.displayName,
                iconEmoji = selectedEmoji.value,
            )
            instanceRepo.save(instance)
            stateFlow.value = CreateInstanceState.Success(instanceId)
        }
    }

    fun resetState() {
        stateFlow.value = CreateInstanceState.Idle
    }
}
