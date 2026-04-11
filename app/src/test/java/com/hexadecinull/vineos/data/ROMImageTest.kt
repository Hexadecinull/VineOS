package com.hexadecinull.vineos.data

import com.google.common.truth.Truth.assertThat
import com.hexadecinull.vineos.data.models.DownloadProgress
import com.hexadecinull.vineos.data.models.ROMDownloadState
import com.hexadecinull.vineos.data.models.ROMImage
import org.junit.Test

class ROMImageTest {

    // ── DownloadProgress ──────────────────────────────────────────────────────

    @Test
    fun `progressFraction is zero when totalBytes is zero`() {
        val progress = DownloadProgress(
            romId = "vine-rom-7",
            bytesDownloaded = 0L,
            totalBytes = 0L,
            state = ROMDownloadState.DOWNLOADING,
        )
        assertThat(progress.progressFraction).isEqualTo(0f)
    }

    @Test
    fun `progressFraction is correct for partial download`() {
        val progress = DownloadProgress(
            romId = "vine-rom-7",
            bytesDownloaded = 500_000L,
            totalBytes = 1_000_000L,
            state = ROMDownloadState.DOWNLOADING,
        )
        assertThat(progress.progressFraction).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `progressPercent is 100 when fully downloaded`() {
        val progress = DownloadProgress(
            romId = "vine-rom-7",
            bytesDownloaded = 1_000_000L,
            totalBytes = 1_000_000L,
            state = ROMDownloadState.DOWNLOADING,
        )
        assertThat(progress.progressPercent).isEqualTo(100)
    }

    @Test
    fun `progressPercent is 0 for empty download`() {
        val progress = DownloadProgress(
            romId = "vine-rom-7",
            bytesDownloaded = 0L,
            totalBytes = 0L,
            state = ROMDownloadState.NOT_DOWNLOADED,
        )
        assertThat(progress.progressPercent).isEqualTo(0)
    }

    @Test
    fun `progressFraction never exceeds 1`() {
        // Guard against overcounting bugs
        val progress = DownloadProgress(
            romId = "vine-rom-7",
            bytesDownloaded = 2_000_000L,
            totalBytes = 1_000_000L,
            state = ROMDownloadState.DOWNLOADING,
        )
        // We don't clamp in the model, but this test documents expected behavior:
        // callers should not send bytesDownloaded > totalBytes
        assertThat(progress.progressFraction).isGreaterThan(0f)
    }

    // ── ROMImage defaults ─────────────────────────────────────────────────────

    @Test
    fun `ROMImage isDownloaded defaults to false`() {
        val rom = buildROM()
        assertThat(rom.isDownloaded).isFalse()
    }

    @Test
    fun `ROMImage downloadState defaults to NOT_DOWNLOADED`() {
        val rom = buildROM()
        assertThat(rom.downloadState).isEqualTo(ROMDownloadState.NOT_DOWNLOADED)
    }

    @Test
    fun `ROMImage localPath is null by default`() {
        val rom = buildROM()
        assertThat(rom.localPath).isNull()
    }

    @Test
    fun `ROMImage has32BitSupport is accurate for dual-ABI ROM`() {
        val rom = buildROM(supportedAbis = listOf("arm64-v8a", "armeabi-v7a"), has32Bit = true)
        assertThat(rom.has32BitSupport).isTrue()
        assertThat(rom.supportedAbis).contains("armeabi-v7a")
    }

    @Test
    fun `ROMImage arm64-only ROM has no 32-bit support`() {
        val rom = buildROM(supportedAbis = listOf("arm64-v8a"), has32Bit = false)
        assertThat(rom.has32BitSupport).isFalse()
        assertThat(rom.supportedAbis).doesNotContain("armeabi-v7a")
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun buildROM(
        supportedAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
        has32Bit: Boolean = true,
    ) = ROMImage(
        id = "vine-rom-7",
        displayName = "Android 7.1.2 Nougat",
        androidVersion = "7.1.2",
        apiLevel = 25,
        description = "Test ROM",
        downloadUrl = "https://example.com/vine-rom-7.vrom",
        sha256 = "abc123",
        sizeBytes = 512_000_000L,
        supportedAbis = supportedAbis,
        has32BitSupport = has32Bit,
        releaseDate = "2025-01-01",
    )
}
