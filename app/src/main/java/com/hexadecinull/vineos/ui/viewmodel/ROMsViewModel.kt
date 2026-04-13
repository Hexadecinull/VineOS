package com.hexadecinull.vineos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hexadecinull.vineos.data.models.DownloadProgress
import com.hexadecinull.vineos.data.models.ROMImage
import com.hexadecinull.vineos.data.repository.ROMRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ROMsUiState(
    val roms: List<ROMImage> = emptyList(),
    val downloadProgress: Map<String, DownloadProgress> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ROMsViewModel @Inject constructor(
    private val romRepo: ROMRepository,
) : ViewModel() {
    val uiState: StateFlow<ROMsUiState> = combine(
        romRepo.roms,
        romRepo.downloadProgress,
    ) { roms, progress ->
        ROMsUiState(roms = roms, downloadProgress = progress, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ROMsUiState(),
    )

    init {
        fetchManifest()
    }

    fun fetchManifest() {
        viewModelScope.launch {
            romRepo.fetchManifest()
        }
    }

    fun download(rom: ROMImage) {
        viewModelScope.launch {
            romRepo.download(rom, onProgress = {})
        }
    }

    fun delete(rom: ROMImage) {
        viewModelScope.launch {
            romRepo.delete(rom)
        }
    }
}
