package com.hexadecinull.vineos.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vineos_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    // ── Keys ──────────────────────────────────────────────────────────────────
    private object Keys {
        val DYNAMIC_COLOR     = booleanPreferencesKey("dynamic_color")
        val KEEP_SCREEN_ON    = booleanPreferencesKey("keep_screen_on")
        val DEFAULT_RAM_MB    = intPreferencesKey("default_ram_mb")
        val DEFAULT_STORAGE_MB = intPreferencesKey("default_storage_mb")
        val SHOW_TECH_INFO    = booleanPreferencesKey("show_tech_info")
        val ALLOW_ROOT        = booleanPreferencesKey("allow_root_instances")
    }

    // ── Defaults ──────────────────────────────────────────────────────────────
    companion object {
        const val DEFAULT_RAM_MB     = 1024
        const val DEFAULT_STORAGE_MB = 4096
    }

    // ── Flows ─────────────────────────────────────────────────────────────────
    val dynamicColor: Flow<Boolean> = store.data
        .catchIO().map { it[Keys.DYNAMIC_COLOR] ?: true }

    val keepScreenOn: Flow<Boolean> = store.data
        .catchIO().map { it[Keys.KEEP_SCREEN_ON] ?: true }

    val defaultRamMb: Flow<Int> = store.data
        .catchIO().map { it[Keys.DEFAULT_RAM_MB] ?: DEFAULT_RAM_MB }

    val defaultStorageMb: Flow<Int> = store.data
        .catchIO().map { it[Keys.DEFAULT_STORAGE_MB] ?: DEFAULT_STORAGE_MB }

    val showTechInfo: Flow<Boolean> = store.data
        .catchIO().map { it[Keys.SHOW_TECH_INFO] ?: false }

    val allowRootInstances: Flow<Boolean> = store.data
        .catchIO().map { it[Keys.ALLOW_ROOT] ?: false }

    // ── Setters ───────────────────────────────────────────────────────────────
    suspend fun setDynamicColor(value: Boolean) =
        store.edit { it[Keys.DYNAMIC_COLOR] = value }

    suspend fun setKeepScreenOn(value: Boolean) =
        store.edit { it[Keys.KEEP_SCREEN_ON] = value }

    suspend fun setDefaultRamMb(value: Int) =
        store.edit { it[Keys.DEFAULT_RAM_MB] = value }

    suspend fun setDefaultStorageMb(value: Int) =
        store.edit { it[Keys.DEFAULT_STORAGE_MB] = value }

    suspend fun setShowTechInfo(value: Boolean) =
        store.edit { it[Keys.SHOW_TECH_INFO] = value }

    suspend fun setAllowRootInstances(value: Boolean) =
        store.edit { it[Keys.ALLOW_ROOT] = value }

    // ── Helper ────────────────────────────────────────────────────────────────
    private fun Flow<Preferences>.catchIO() =
        catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
}
