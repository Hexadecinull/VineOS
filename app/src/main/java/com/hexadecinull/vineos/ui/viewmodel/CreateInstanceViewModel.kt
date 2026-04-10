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
import kotlinx.coroutines.flow.*
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

    private val _state = MutableStateFlow<CreateInstanceState>(CreateInstanceState.Idle)
    val state: StateFlow<CreateInstanceState> = _state.asStateFlow()

    // Form fields as state
    var instanceName = MutableStateFlow("")
    var selectedRamMb = MutableStateFlow(AppPreferences.DEFAULT_RAM_MB)
    var selectedStorageMb = MutableStateFlow(AppPreferences.DEFAULT_STORAGE_MB)
    var selectedEmoji = MutableStateFlow("🟢")

    init {
        // Seed defaults from user preferences
        viewModelScope.launch {
            prefs.defaultRamMb.first().let { selectedRamMb.value = it }
            prefs.defaultStorageMb.first().let { selectedStorageMb.value = it }
        }
    }

    val isFormValid: StateFlow<Boolean> = instanceName
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun createInstance(rom: ROMImage, dataDir: String) {
        if (_state.value is CreateInstanceState.Creating) return
        _state.value = CreateInstanceState.Creating

        viewModelScope.launch {
            val name = instanceName.value.trim()
            val ram = selectedRamMb.value
            val storage = selectedStorageMb.value

            // Ask the native runtime to create the instance directory + sparse storage image
            val instancePath = VineRuntime.createInstance(
                instanceId = "",          // Let runtime generate a UUID via temp placeholder
                romImagePath = rom.localPath ?: "",
                storageMb = storage,
            )

            if (instancePath == null) {
                _state.value = CreateInstanceState.Error(
                    "Failed to create instance storage. Check available disk space."
                )
                return@launch
            }

            // Extract the instance ID from the path (last path component)
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
            _state.value = CreateInstanceState.Success(instanceId)
        }
    }

    fun resetState() {
        _state.value = CreateInstanceState.Idle
    }
}
