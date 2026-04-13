package com.hexadecinull.vineos.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

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
    val storageMb: Int = 4096,
    val androidVersionDisplay: String,
    val isRooted: Boolean = false,
    val iconEmoji: String = "🟢",
)

enum class VMStatus {
    STOPPED,
    BOOTING,
    RUNNING,
    PAUSED,
    ERROR,
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
