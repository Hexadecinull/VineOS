package com.hexadecinull.vineos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hexadecinull.vineos.data.repository.AppPreferences
import com.hexadecinull.vineos.ui.screens.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPreferences,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = combine(
        prefs.dynamicColor,
        prefs.keepScreenOn,
        prefs.defaultRamMb,
        prefs.defaultStorageMb,
        prefs.showTechInfo,
        // combine only supports up to 5 flows; chain a second combine for the rest
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
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings(),
    )

    fun update(newSettings: AppSettings) {
        val current = settings.value
        viewModelScope.launch {
            if (newSettings.dynamicColor     != current.dynamicColor)     prefs.setDynamicColor(newSettings.dynamicColor)
            if (newSettings.keepScreenOn     != current.keepScreenOn)     prefs.setKeepScreenOn(newSettings.keepScreenOn)
            if (newSettings.defaultRamMb     != current.defaultRamMb)     prefs.setDefaultRamMb(newSettings.defaultRamMb)
            if (newSettings.defaultStorageMb != current.defaultStorageMb) prefs.setDefaultStorageMb(newSettings.defaultStorageMb)
            if (newSettings.showTechnicalInfo != current.showTechnicalInfo) prefs.setShowTechInfo(newSettings.showTechnicalInfo)
            if (newSettings.allowRootInstances != current.allowRootInstances) prefs.setAllowRootInstances(newSettings.allowRootInstances)
        }
    }
}
