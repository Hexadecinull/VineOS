package com.hexadecinull.vineos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hexadecinull.vineos.data.repository.AppPreferences
import com.hexadecinull.vineos.ui.screens.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val prefs: AppPreferences) : ViewModel() {
    // No grace period on settings: sources are already-cached hot flows,
    // so a rebuild on resubscribe is cheap.
    val settings: StateFlow<AppSettings> = combine(
        prefs.dynamicColor,
        prefs.keepScreenOn,
        prefs.defaultRamMb,
        prefs.defaultStorageMb,
        prefs.showTechInfo,
    ) { dynamicColor, keepScreenOn, ramMb, storageMb, techInfo ->
        AppSettings(
            dynamicColor = dynamicColor,
            keepScreenOn = keepScreenOn,
            defaultRamMb = ramMb,
            defaultStorageMb = storageMb,
            showTechnicalInfo = techInfo,
        )
    }.combine(prefs.allowRootInstances) { partial, allowRoot ->
        partial.copy(allowRootInstances = allowRoot)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = AppSettings(),
    )

    fun update(newSettings: AppSettings) {
        val current = settings.value
        viewModelScope.launch {
            if (newSettings.dynamicColor != current.dynamicColor) prefs.setDynamicColor(newSettings.dynamicColor)
            if (newSettings.keepScreenOn != current.keepScreenOn) prefs.setKeepScreenOn(newSettings.keepScreenOn)
            if (newSettings.defaultRamMb != current.defaultRamMb) prefs.setDefaultRamMb(newSettings.defaultRamMb)
            if (newSettings.defaultStorageMb != current.defaultStorageMb) prefs.setDefaultStorageMb(newSettings.defaultStorageMb)
            if (newSettings.showTechnicalInfo != current.showTechnicalInfo) prefs.setShowTechInfo(newSettings.showTechnicalInfo)
            if (newSettings.allowRootInstances != current.allowRootInstances) prefs.setAllowRootInstances(newSettings.allowRootInstances)
        }
    }
}
