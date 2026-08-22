package com.hexadecinull.vineos.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.hexadecinull.vineos.data.models.AbiCompat
import com.hexadecinull.vineos.data.models.DownloadProgress
import com.hexadecinull.vineos.data.models.ROMDownloadState
import com.hexadecinull.vineos.data.models.ROMImage
import com.hexadecinull.vineos.data.repository.ROMRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ROMDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var romRepo: ROMRepository
    private lateinit var viewModel: ROMDetailViewModel

    private val rom1 = buildRom("id-1")
    private val rom2 = buildRom("id-2")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        romRepo = mockk(relaxed = true)
        every { romRepo.roms } returns flowOf(listOf(rom1, rom2))
        every { romRepo.downloadProgress } returns flowOf(emptyMap())
        every { romRepo.getRom("id-1") } returns rom1
        every { romRepo.getRom("id-2") } returns rom2
        every { romRepo.getRom("missing") } returns null
        every { romRepo.getRom("") } returns null
        viewModel = ROMDetailViewModel(romRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // AbiCompat.romRunMode falls back to Build.SUPPORTED_ABIS when no host
    // list is passed in; that field isn't populated under the JVM unit test
    // stub, so every rom here resolves to UNAVAILABLE regardless of its own
    // supportedAbis. See AbiCompatTest for the actual compatibility matrix.

    @Test
    fun `initial uiState has no rom until load is called`() = runTest(testDispatcher) {
        val state = viewModel.uiState.first()
        assertThat(state.rom).isNull()
    }

    @Test
    fun `load surfaces the matching rom from the repository`() = runTest(testDispatcher) {
        viewModel.load("id-1")
        val state = viewModel.uiState.first { it.rom != null }
        assertThat(state.rom?.id).isEqualTo("id-1")
    }

    @Test
    fun `load with an unknown id surfaces no rom`() = runTest(testDispatcher) {
        viewModel.load("missing")
        val state = viewModel.uiState.first()
        assertThat(state.rom).isNull()
    }

    @Test
    fun `load fetches the manifest when the rom is not cached yet`() = runTest(testDispatcher) {
        viewModel.load("missing")
        coVerify { romRepo.fetchManifest() }
    }

    @Test
    fun `load does not re-fetch the manifest when the rom is already cached`() = runTest(testDispatcher) {
        viewModel.load("id-1")
        coVerify(exactly = 0) { romRepo.fetchManifest() }
    }

    @Test
    fun `uiState surfaces download progress for the selected rom`() = runTest(testDispatcher) {
        val progress = DownloadProgress("id-1", 50L, 100L, ROMDownloadState.DOWNLOADING)
        every { romRepo.downloadProgress } returns flowOf(mapOf("id-1" to progress))
        viewModel = ROMDetailViewModel(romRepo)

        viewModel.load("id-1")
        val state = viewModel.uiState.first { it.progress != null }
        assertThat(state.progress?.bytesDownloaded).isEqualTo(50L)
    }

    @Test
    fun `uiState resolves a runMode once a rom is loaded`() = runTest(testDispatcher) {
        viewModel.load("id-1")
        val state = viewModel.uiState.first { it.rom != null }
        assertThat(state.runMode).isEqualTo(AbiCompat.RunMode.UNAVAILABLE)
    }

    @Test
    fun `download does nothing when no rom is selected`() = runTest(testDispatcher) {
        viewModel.download()
        coVerify(exactly = 0) { romRepo.download(any(), any()) }
    }

    @Test
    fun `download sends the selected rom to the repository`() = runTest(testDispatcher) {
        viewModel.load("id-1")
        viewModel.download()
        coVerify { romRepo.download(rom1, any()) }
    }

    @Test
    fun `delete does nothing when no rom is selected`() = runTest(testDispatcher) {
        viewModel.delete()
        coVerify(exactly = 0) { romRepo.delete(any()) }
    }

    @Test
    fun `delete removes the selected rom from the repository`() = runTest(testDispatcher) {
        viewModel.load("id-2")
        viewModel.delete()
        coVerify { romRepo.delete(rom2) }
    }

    private fun buildRom(id: String) = ROMImage(
        id = id,
        displayName = "ROM $id",
        androidVersion = "7.1.2",
        apiLevel = 25,
        description = "",
        downloadUrl = "https://example.com/$id.vrom",
        sha256 = "0".repeat(64),
        sizeBytes = 1_000_000L,
        supportedAbis = listOf(AbiCompat.ARM64_V8A),
        has32BitSupport = true,
        releaseDate = "2026-01-01",
    )
}
