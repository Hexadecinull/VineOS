package com.ypsilonos.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ypsilonos.android.data.models.BuildProps
import com.ypsilonos.android.data.models.GameMode
import com.ypsilonos.android.data.models.VMInstance
import com.ypsilonos.android.data.repository.InstanceBackup
import com.ypsilonos.android.data.repository.InstanceRepository
import com.ypsilonos.android.data.repository.StorageCleaner
import com.ypsilonos.android.domain.YpsilonVMManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompatUiState(
    val instance: VMInstance? = null,
    val availableRamMb: Long = 0,
    val deviceFingerprint: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val ramGuardWarning: String? = null,
)

@HiltViewModel
class CompatibilityViewModel @Inject constructor(
    private val instanceRepo: InstanceRepository,
    private val vmManager: YpsilonVMManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompatUiState())
    val uiState: StateFlow<CompatUiState> = _uiState.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(),
        initialValue = CompatUiState(),
    )

    val instance: StateFlow<VMInstance?> = _uiState.map { it.instance }.stateIn(
        scope = viewModelScope,
        started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(),
        initialValue = null,
    )

    val gameModes: List<GameMode> = GameMode.values().toList()

    fun load(instance: VMInstance) {
        viewModelScope.launch {
            _uiState.update { it.copy(instance = instance, message = null, error = null) }
            vmManager.getAvailableRamMb().let { ram ->
                _uiState.update { it.copy(availableRamMb = ram) }
            }
            vmManager.getDeviceFingerprint().let { fp ->
                _uiState.update { it.copy(deviceFingerprint = fp) }
            }
            // RAM guard: warn if the instance asks for more than ~70% of available RAM
            val warned = if (ram > 0 && instance.ramMb > ram * 0.7f) {
                "Instance RAM (${instance.ramMb} MB) exceeds 70% of available host RAM ($ram MB). Lower it for stability."
            } else null
            _uiState.update { it.copy(ramGuardWarning = warned) }
        }
    }

    fun loadById(instanceId: String) {
        viewModelScope.launch {
            instanceRepo.getById(instanceId).let { instance ->
                if (instance != null) load(instance)
            }
        }
    }

    fun applyPreset(mode: GameMode) {
        val defaults = mode.defaults()
        val inst = _uiState.value.instance ?: return

        // RAM guard: cap preset RAM at 70% of available host RAM
        var safeRam = defaults.ramMb
        val available = _uiState.value.availableRamMb
        if (available > 0 && safeRam > available * 0.7f) {
            safeRam = (available * 0.7f).toInt().coerceAtLeast(256)
        }

        val updated = inst.copy(
            ramMb = safeRam,
            cpuCores = defaults.cpuCores,
            renderScale = defaults.renderScale,
            gameMode = mode.entryName,
            rootHidden = defaults.rootHidden,
            selinuxEnforcing = defaults.selinuxEnforcing,
        )
        _uiState.update { it.copy(instance = updated, message = "Preset applied: ${mode.label}") }
    }

    fun setRootHidden(hidden: Boolean) {
        val inst = _uiState.value.instance ?: return
        _uiState.update { it.copy(instance = inst.copy(rootHidden = hidden)) }
    }

    fun setSELinux(enforcing: Boolean) {
        val inst = _uiState.value.instance ?: return
        _uiState.update { it.copy(instance = inst.copy(selinuxEnforcing = enforcing)) }
    }

    fun setRenderScale(scale: Float) {
        val inst = _uiState.value.instance ?: return
        _uiState.update { it.copy(instance = inst.copy(renderScale = scale.coerceIn(0.25f, 1.0f))) }
    }

    fun setNetworkIsolated(isolated: Boolean) {
        val inst = _uiState.value.instance ?: return
        _uiState.update { it.copy(instance = inst.copy(networkIsolated = isolated)) }
    }

    fun setAutoSuspend(autoSuspend: Boolean) {
        val inst = _uiState.value.instance ?: return
        _uiState.update { it.copy(instance = inst.copy(autoSuspend = autoSuspend)) }
    }

    // --- Backup / storage cleanup ---

    suspend fun backupInstance(context: android.content.Context): String? {
        val inst = _uiState.value.instance ?: return null
        return try {
            val file = InstanceBackup.exportInstance(context, inst)
            _uiState.update {
                it.copy(instance = inst.copy(lastBackupAt = System.currentTimeMillis()))
            }
            instanceRepo.update(inst.copy(lastBackupAt = System.currentTimeMillis()))
            file.absolutePath
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Backup failed: ${e.message}") }
            null
        }
    }

    suspend fun cleanStorage(context: android.content.Context): Long {
        val inst = _uiState.value.instance ?: return 0L
        return try {
            val freed = StorageCleaner.cleanInstance(context, inst)
            _uiState.update { it.copy(message = "Cleaned ${freed / 1024} KB of temp files") }
            freed
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Cleanup failed: ${e.message}") }
            0L
        }
    }

    fun setBuildProp(key: String, value: String) {
        val inst = _uiState.value.instance ?: return
        val props = BuildProps.toMap(inst.buildPropsJson).toMutableMap()
        if (value.isBlank()) props.remove(key) else props[key] = value
        _uiState.update { it.copy(instance = inst.copy(buildPropsJson = BuildProps.fromMap(props))) }
    }

    fun clearBuildProps() {
        val inst = _uiState.value.instance ?: return
        _uiState.update { it.copy(instance = inst.copy(buildPropsJson = "{}")) }
    }

    fun applyToInstance() {
        val inst = _uiState.value.instance ?: return
        _uiState.update { it.copy(isSaving = true, message = null, error = null) }
        viewModelScope.launch {
            var failed = false
            var errorMsg = ""

            // Persist instance settings.
            try {
                instanceRepo.update(inst)
            } catch (e: Exception) {
                failed = true
                errorMsg = "Failed to save instance: ${e.message}"
            }

            // Apply runtime toggles if the instance is running.
            if (!failed) {
                val handleRunning = vmManager.getHandle(inst.id) != null

                if (handleRunning) {
                    if (!vmManager.setRenderScale(inst.id, inst.renderScale)) {
                        failed = true
                        errorMsg = "Failed to apply render scale."
                    }
                    if (!failed && !vmManager.setSELinux(inst.selinuxEnforcing)) {
                        failed = true
                        errorMsg = "Failed to apply SELinux setting."
                    }
                    if (!failed) {
                        if (inst.rootHidden) {
                            if (!vmManager.hideRoot(inst.id)) {
                                failed = true
                                errorMsg = "Failed to hide root artifacts."
                            }
                        } else {
                            if (!vmManager.restoreRoot(inst.id)) {
                                failed = true
                                errorMsg = "Failed to restore root artifacts."
                            }
                        }
                    }
                    if (!failed) {
                        if (inst.buildPropsJson != "{}") {
                            if (!vmManager.spoofBuildProps(inst.id, BuildProps.toMap(inst.buildPropsJson))) {
                                failed = true
                                errorMsg = "Failed to spoof build.prop."
                            }
                        } else {
                            if (!vmManager.restoreBuildProps(inst.id)) {
                                failed = true
                                errorMsg = "Failed to restore build.prop."
                            }
                        }
                    }
                    if (!failed && !vmManager.setNetworkIsolated(inst.id, inst.networkIsolated)) {
                        failed = true
                        errorMsg = "Failed to apply network isolation."
                    }
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = if (failed) null else "Compatibility settings saved${if (handleRunning) " and applied live" else ""}",
                        error = if (failed) errorMsg else null,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isSaving = false, error = errorMsg)
                }
            }
        }
    }
}
