package com.hexadecinull.vineos.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

// ─── VM Instance ─────────────────────────────────────────────────────────────

/**
 * Represents a single VineOS virtual machine instance.
 * Stored in Room DB. Each instance has its own isolated filesystem image.
 */
@Entity(tableName = "vm_instances")
data class VMInstance(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val romId: String,                  // FK to the ROM this instance is based on
    val romVersion: String,             // e.g. "7.1.2", "9.0"
    val storagePath: String,            // Absolute path to this instance's data directory
    val status: VMStatus = VMStatus.STOPPED,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long = System.currentTimeMillis(),
    val ramMb: Int = 1024,              // RAM allocated to this instance in MB
    val storageMb: Int = 4096,          // Virtual storage size in MB
    val androidVersionDisplay: String,  // Human-readable, e.g. "Android 7.1 Nougat"
    val isRooted: Boolean = false,
    val iconEmoji: String = "🟢",       // User-customisable instance icon
)

/**
 * Runtime state of a VM instance. Persisted to DB so the UI can show last-known
 * state correctly after an app restart.
 */
enum class VMStatus {
    STOPPED,    // Not running
    BOOTING,    // init → Zygote startup in progress
    RUNNING,    // Fully booted and interactive
    PAUSED,     // Suspended (future feature)
    ERROR,      // Crashed or failed to start
}

// ─── ROM Image ────────────────────────────────────────────────────────────────

/**
 * Metadata for a downloadable ROM image.
 * Serializable so it can be parsed from the remote ROM manifest JSON.
 */
@Serializable
data class ROMImage(
    val id: String,                     // e.g. "vine-rom-7"
    val displayName: String,            // e.g. "Android 7.1 Nougat"
    val androidVersion: String,         // e.g. "7.1.2"
    val apiLevel: Int,                  // e.g. 25
    val description: String,
    val downloadUrl: String,
    val sha256: String,                 // Integrity check
    val sizeBytes: Long,
    val minHostApiLevel: Int = 26,      // This ROM requires host to be at least API X
    val supportedAbis: List<String>,    // e.g. ["arm64-v8a", "armeabi-v7a"]
    val has32BitSupport: Boolean,       // Whether the ROM supports armeabi-v7a
    val releaseDate: String,            // ISO 8601 date string
    val localPath: String? = null,      // Set when downloaded; path to .vrom file
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

// ─── Download Progress ────────────────────────────────────────────────────────

data class DownloadProgress(
    val romId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: ROMDownloadState,
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
    val progressPercent: Int
        get() = (progressFraction * 100).toInt()
}
