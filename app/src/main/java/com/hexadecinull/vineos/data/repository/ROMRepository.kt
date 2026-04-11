package com.hexadecinull.vineos.data.repository

import android.content.Context
import com.hexadecinull.vineos.data.models.DownloadProgress
import com.hexadecinull.vineos.data.models.ROMDownloadState
import com.hexadecinull.vineos.data.models.ROMImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ROMRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val romsDir = File(context.filesDir, "roms").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    private val _roms = MutableStateFlow<List<ROMImage>>(emptyList())
    val roms: Flow<List<ROMImage>> = _roms.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: Flow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    suspend fun fetchManifest(): Result<List<ROMImage>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(MANIFEST_URL).build()
            val body = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.string() ?: error("Empty manifest response")
            }
            val manifest = json.decodeFromString<ROMManifest>(body)
            val enriched = manifest.roms.map { rom ->
                val localFile = File(romsDir, "${rom.id}.vrom")
                rom.copy(
                    localPath = if (localFile.exists()) localFile.absolutePath else null,
                    isDownloaded = localFile.exists() && verifyFile(localFile, rom.sha256),
                    downloadState = when {
                        localFile.exists() && verifyFile(localFile, rom.sha256) -> ROMDownloadState.READY
                        localFile.exists() -> ROMDownloadState.CORRUPTED
                        else -> ROMDownloadState.NOT_DOWNLOADED
                    },
                )
            }
            _roms.value = enriched
            enriched
        }
    }

    suspend fun download(rom: ROMImage, onProgress: (DownloadProgress) -> Unit): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dest = File(romsDir, "${rom.id}.vrom")
                updateProgress(rom.id, 0L, rom.sizeBytes, ROMDownloadState.DOWNLOADING)

                val request = Request.Builder().url(rom.downloadUrl).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Empty body")
                    val totalBytes = body.contentLength().takeIf { it > 0 } ?: rom.sizeBytes

                    dest.outputStream().buffered(BUFFER_SIZE).use { out ->
                        body.byteStream().buffered(BUFFER_SIZE).use { input ->
                            var downloaded = 0L
                            val buf = ByteArray(BUFFER_SIZE)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                out.write(buf, 0, read)
                                downloaded += read
                                val progress = DownloadProgress(rom.id, downloaded, totalBytes, ROMDownloadState.DOWNLOADING)
                                updateProgress(rom.id, downloaded, totalBytes, ROMDownloadState.DOWNLOADING)
                                onProgress(progress)
                            }
                        }
                    }
                }

                updateProgress(rom.id, rom.sizeBytes, rom.sizeBytes, ROMDownloadState.VERIFYING)
                if (!verifyFile(dest, rom.sha256)) {
                    dest.delete()
                    updateProgress(rom.id, 0L, rom.sizeBytes, ROMDownloadState.CORRUPTED)
                    error("SHA-256 verification failed for ${rom.id}")
                }

                updateProgress(rom.id, rom.sizeBytes, rom.sizeBytes, ROMDownloadState.READY)
                refreshROM(rom.id, dest)
                dest
            }
        }

    suspend fun delete(rom: ROMImage) = withContext(Dispatchers.IO) {
        File(romsDir, "${rom.id}.vrom").delete()
        refreshROM(rom.id, null)
    }

    fun getROMFile(romId: String): File? {
        val f = File(romsDir, "$romId.vrom")
        return if (f.exists()) f else null
    }

    private fun verifyFile(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER_SIZE).use { stream ->
            val buf = ByteArray(BUFFER_SIZE)
            var read: Int
            while (stream.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expectedSha256, ignoreCase = true)
    }

    private fun updateProgress(romId: String, downloaded: Long, total: Long, state: ROMDownloadState) {
        _downloadProgress.value = _downloadProgress.value + (romId to DownloadProgress(romId, downloaded, total, state))
    }

    private fun refreshROM(romId: String, localFile: File?) {
        _roms.value = _roms.value.map { rom ->
            if (rom.id == romId) rom.copy(
                localPath = localFile?.absolutePath,
                isDownloaded = localFile != null,
                downloadState = if (localFile != null) ROMDownloadState.READY else ROMDownloadState.NOT_DOWNLOADED,
            ) else rom
        }
    }

    companion object {
        private const val MANIFEST_URL = "https://vineos.hexadecinull.com/roms/manifest.json"
        private const val BUFFER_SIZE = 64 * 1024
    }
}
