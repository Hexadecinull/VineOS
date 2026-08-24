package com.hexadecinull.vineos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hexadecinull.vineos.data.models.AbiCompat
import com.hexadecinull.vineos.data.models.DownloadProgress
import com.hexadecinull.vineos.data.models.ROMImage
import com.hexadecinull.vineos.data.repository.ROMRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ROMDetailUiState(
    val rom: ROMImage? = null,
    val progress: DownloadProgress? = null,
    val runMode: AbiCompat.RunMode = AbiCompat.RunMode.UNAVAILABLE,
)

@HiltViewModel
class ROMDetailViewModel @Inject constructor(private val romRepo: ROMRepository) : ViewModel() {
    private val romIdFlow = MutableStateFlow("")

    // No grace period on uiState: sources are already-cached hot flows, so a rebuild on resubscribe is cheap
    val uiState: StateFlow<ROMDetailUiState> = combine(
        romIdFlow,
        romRepo.roms,
        romRepo.downloadProgress,
    ) { romId, roms, progress ->
        val rom = roms.find { it.id == romId }
        ROMDetailUiState(
            rom = rom,
            progress = progress[romId],
            runMode = rom?.let { AbiCompat.romRunMode(it) } ?: AbiCompat.RunMode.UNAVAILABLE,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ROMDetailUiState())

    fun load(id: String) {
        romIdFlow.value = id
        if (romRepo.getRom(id) == null) {
            viewModelScope.launch { romRepo.fetchManifest() }
        }
    }

    fun download() {
        val rom = romRepo.getRom(romIdFlow.value) ?: return
        viewModelScope.launch { romRepo.download(rom, onProgress = {}) }
    }

    fun delete() {
        val rom = romRepo.getRom(romIdFlow.value) ?: return
        viewModelScope.launch { romRepo.delete(rom) }
    }
}
