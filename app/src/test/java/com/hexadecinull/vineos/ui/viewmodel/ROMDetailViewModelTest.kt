package com.hexadecinull.vineos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hexadecinull.vineos.data.models.AbiCompat
import com.hexadecinull.vineos.data.models.DownloadProgress
import com.hexadecinull.vineos.data.models.ROMImage
import com.hexadecinull.vineos.data.repository.ROMRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ROMDetailUiState(
    val rom: ROMImage? = null,
    val runMode: AbiCompat.RunMode? = null,
    val progress: DownloadProgress? = null,
)

class ROMDetailViewModel(
    private val repo: ROMRepository
) : ViewModel() {

    private val selectedRomId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ROMDetailUiState> =
        combine(
            selectedRomId,
            repo.roms,
            repo.downloadProgress
        ) { romId, roms, progressMap ->

            val rom = roms.find { it.id == romId }
            val progress = romId?.let { progressMap[it] }

            ROMDetailUiState(
                rom = rom,
                runMode = rom?.let { AbiCompat.getRunMode(it.supportedAbis) },
                progress = progress
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ROMDetailUiState()
        )

    fun load(id: String) {
        val rom = repo.getRom(id)

        if (rom == null) {
            viewModelScope.launch {
                repo.fetchManifest()
            }
        }

        selectedRomId.value = id
    }

    fun download() {
        val rom = uiState.value.rom ?: return
        viewModelScope.launch {
            repo.download(rom, {})
        }
    }

    fun delete() {
        val rom = uiState.value.rom ?: return
        viewModelScope.launch {
            repo.delete(rom)
        }
    }
}
