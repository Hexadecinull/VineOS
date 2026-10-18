package com.ypsilonos.android.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

@Entity(tableName = "vm_instances")
data class VMInstance(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val romId: String,
    val romVersion: String,
    val storagePath: String,
    val status: VMStatus = VMStatus.STOPPED,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val ramMb: Int = 1024,
    val cpuCores: Int = 0,
    val storageMb: Int = 4096,
    val androidVersionDisplay: String,
    val isRooted: Boolean = false,
    val iconEmoji: String = "🟢",
    // --- YpsilonOS compatibility / low-end features ---
    val gameMode: String = GameMode.BALANCED.entryName,
    val buildPropsJson: String = "{}",
    val rootHidden: Boolean = false,
    val selinuxEnforcing: Boolean = true,
    val renderScale: Float = 1.0f,
    // --- Safety / lifecycle features ---
    val autoSuspend: Boolean = true,
    val networkIsolated: Boolean = false,
    val lastBackupAt: Long = 0,
)

enum class VMStatus {
    STOPPED,
    BOOTING,
    RUNNING,
    PAUSED,
    ERROR,
}

// Game / instance preset profiles. Each profile maps to sensible defaults for
// RAM, CPU cores, render scale and compatibility tweaks so the VM runs well
// on low-end Android devices and can run ARM games such as Pokémon GO.
enum class GameMode {
    LOW_END("Low-end device"),
    BALANCED("Balanced"),
    HIGH_END("High-end device"),
    POKEMON_GO("Pokémon GO (ARM games)");

    val label: String

    GameMode(label: String) {
        this.label = label
    }

    // Recommended instance defaults for this profile.
    fun defaults(): GameDefaults = when (this) {
        LOW_END -> GameDefaults(ramMb = 512, cpuCores = 2, renderScale = 0.6f,
            rootHidden = true, selinuxEnforcing = false, spoofProps = true)
        BALANCED -> GameDefaults(ramMb = 1024, cpuCores = 4, renderScale = 1.0f,
            rootHidden = false, selinuxEnforcing = true, spoofProps = false)
        HIGH_END -> GameDefaults(ramMb = 2048, cpuCores = 0, renderScale = 1.0f,
            rootHidden = false, selinuxEnforcing = true, spoofProps = false)
        POKEMON_GO -> GameDefaults(ramMb = 1024, cpuCores = 4, renderScale = 0.75f,
            rootHidden = true, selinuxEnforcing = false, spoofProps = true)
    }
}

data class GameDefaults(
    val ramMb: Int,
    val cpuCores: Int, // 0 = unlimited
    val renderScale: Float,
    val rootHidden: Boolean,
    val selinuxEnforcing: Boolean,
    val spoofProps: Boolean,
)

// Helper to read/write the spoofed build props as a JSON map stored in buildPropsJson.
object BuildProps {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }

    private val mapSerializer = kotlinx.serialization.serializers.Map.serializer(
        kotlinx.serialization.String.serializer(),
        kotlinx.serialization.String.serializer(),
    )

    fun toMap(jsonStr: String): Map<String, String> {
        return try {
            json.decodeFromString(mapSerializer, jsonStr)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun fromMap(map: Map<String, String>): String =
        json.encodeToString(mapSerializer, map)

    // Defaults for a clean, unrooted device profile that helps Play Integrity /
    // SafetyNet and game root-detection pass on ARM-game instances.
    fun cleanDevice(model: String, brand: String, fingerprint: String): Map<String, String> {
        return mapOf(
            "ro.product.model" to model,
            "ro.product.brand" to brand,
            "ro.product.manufacturer" to brand,
            "ro.product.name" to model,
            "ro.product.device" to model,
            "ro.build.fingerprint" to fingerprint,
            "ro.bootimage.build.fingerprint" to fingerprint,
            "ro.vendor.build.fingerprint" to fingerprint,
            "ro.system.build.fingerprint" to fingerprint,
            "ro.build.version.security_patch" to "2026-10-01",
            "ro.build.type" to "user",
            "ro.build.tags" to "release-keys",
            "ro.debuggable" to "0",
            "ro.secure" to "1",
            "ro.build.selinux" to "1",
            "ro.rooted" to "0",
            "ro.init.selinux" to "1",
            "ro.adb.secure" to "1",
            "persist.sys.root_access" to "0"
        )
    }
}

@Serializable
data class ROMImage(
    val id: String,
    val displayName: String,
    val androidVersion: String,
    val apiLevel: Int,
    val description: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val minHostApiLevel: Int = 26,
    val supportedAbis: List<String>,
    val has32BitSupport: Boolean,
    val releaseDate: String,
    val isLocal: Boolean = false,
    val localPath: String? = null,
    val isDownloaded: Boolean = false,
    val downloadState: ROMDownloadState = ROMDownloadState.NOT_DOWNLOADED,
)

enum class ROMDownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    VERIFYING,
    READY,
    CORRUPTED,
}

data class DownloadProgress(val romId: String, val bytesDownloaded: Long, val totalBytes: Long, val state: ROMDownloadState) {
    val progressFraction: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}
