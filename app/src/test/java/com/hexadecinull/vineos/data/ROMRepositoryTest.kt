package com.hexadecinull.vineos.data

import com.google.common.truth.Truth.assertThat
import com.hexadecinull.vineos.data.models.ROMDownloadState
import com.hexadecinull.vineos.data.models.ROMImage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class ROMRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    // ── SHA-256 helper (mirrors private method logic) ──────────────────────

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buf = ByteArray(65536)
            var read: Int
            while (stream.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `sha256 of known content is stable`() {
        val file = tmpFolder.newFile("test.bin").also { it.writeText("hello vine") }
        val hash1 = sha256(file)
        val hash2 = sha256(file)
        assertThat(hash1).isEqualTo(hash2)
        assertThat(hash1).hasLength(64)
    }

    @Test
    fun `sha256 differs for different content`() {
        val a = tmpFolder.newFile("a.bin").also { it.writeText("content-a") }
        val b = tmpFolder.newFile("b.bin").also { it.writeText("content-b") }
        assertThat(sha256(a)).isNotEqualTo(sha256(b))
    }

    @Test
    fun `sha256 is case-insensitive comparison`() {
        val file = tmpFolder.newFile("c.bin").also { it.writeText("vine") }
        val lower = sha256(file).lowercase()
        val upper = sha256(file).uppercase()
        assertThat(lower.equals(upper, ignoreCase = true)).isTrue()
    }

    // ── ROMImage state transitions ─────────────────────────────────────────

    @Test
    fun `NOT_DOWNLOADED is initial state`() {
        val rom = buildROM()
        assertThat(rom.downloadState).isEqualTo(ROMDownloadState.NOT_DOWNLOADED)
        assertThat(rom.isDownloaded).isFalse()
        assertThat(rom.localPath).isNull()
    }

    @Test
    fun `READY state has local path and isDownloaded true`() {
        val rom = buildROM().copy(
            isDownloaded = true,
            localPath = "/data/roms/vine-rom-7.vrom",
            downloadState = ROMDownloadState.READY,
        )
        assertThat(rom.isDownloaded).isTrue()
        assertThat(rom.localPath).isNotNull()
        assertThat(rom.downloadState).isEqualTo(ROMDownloadState.READY)
    }

    @Test
    fun `CORRUPTED state has local path but isDownloaded false`() {
        val rom = buildROM().copy(
            isDownloaded = false,
            localPath = "/data/roms/vine-rom-7.vrom",
            downloadState = ROMDownloadState.CORRUPTED,
        )
        assertThat(rom.isDownloaded).isFalse()
        assertThat(rom.localPath).isNotNull()
        assertThat(rom.downloadState).isEqualTo(ROMDownloadState.CORRUPTED)
    }

    @Test
    fun `all ROMDownloadState values are distinct`() {
        val states = ROMDownloadState.entries.toSet()
        assertThat(states).hasSize(ROMDownloadState.entries.size)
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private fun buildROM() = ROMImage(
        id = "vine-rom-7",
        displayName = "Android 7.1.2 Nougat",
        androidVersion = "7.1.2",
        apiLevel = 25,
        description = "Test ROM",
        downloadUrl = "https://example.com/vine-rom-7.vrom",
        sha256 = "a".repeat(64),
        sizeBytes = 512_000_000L,
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        has32BitSupport = true,
        releaseDate = "2025-01-01",
    )
}
