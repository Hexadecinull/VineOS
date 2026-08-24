package com.hexadecinull.vineos.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hexadecinull.vineos.data.models.DownloadProgress
import com.hexadecinull.vineos.data.models.ROMImage
import com.hexadecinull.vineos.data.repository.ROMRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ROMsUiState(
    val roms: List<ROMImage> = emptyList(),
    val downloadProgress: Map<String, DownloadProgress> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ROMsViewModel @Inject constructor(private val romRepo: ROMRepository) : ViewModel() {
    private val _importError = MutableStateFlow<String?>(null)
    val importError: StateFlow<String?> = _importError.asStateFlow()

    // No grace period on uiState: sources are already-cached hot flows, so a rebuild on resubscribe is cheap
    val uiState: StateFlow<ROMsUiState> = combine(
        romRepo.roms,
        romRepo.downloadProgress,
    ) { roms, progress ->
        ROMsUiState(roms = roms, downloadProgress = progress, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
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

    fun importLocalRom(uri: Uri) {
        viewModelScope.launch {
            romRepo.importLocalRom(uri).onFailure { e ->
                _importError.value = e.message ?: "Could not import that file"
            }
        }
    }

    fun clearImportError() {
        _importError.value = null
    }
}
