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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        romRepo = mockk(relaxed = true)
        every { romRepo.roms } returns flowOf(listOf(buildRom("rom-1")))
        every { romRepo.downloadProgress } returns flowOf(emptyMap())
        viewModel = ROMDetailViewModel(romRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // uiState.first { predicate } subscribes and suspends until the flow
    // actually produces a value matching the predicate. Unlike reading
    // .value after a fixed advanceUntilIdle(), this doesn't depend on
    // guessing how many dispatcher hops the combine/stateIn chain needs;
    // it waits for the real answer, however long that takes.

    @Test
    fun `initial uiState has no rom until load is called`() = runTest(testDispatcher) {
        val state = viewModel.uiState.first()
        assertThat(state.rom).isNull()
    }

    @Test
    fun `load surfaces the matching rom and its run mode`() = runTest(testDispatcher) {
        viewModel.load("rom-1")
        val state = viewModel.uiState.first { it.rom != null }
        assertThat(state.rom?.id).isEqualTo("rom-1")
        assertThat(state.runMode).isEqualTo(AbiCompat.RunMode.NATIVE)
    }

    @Test
    fun `load with an unknown id triggers a manifest refetch`() = runTest(testDispatcher) {
        every { romRepo.getRom("missing") } returns null
        viewModel.load("missing")
        coVerify { romRepo.fetchManifest() }
    }

    @Test
    fun `load with a known id does not refetch the manifest`() = runTest(testDispatcher) {
        every { romRepo.getRom("rom-1") } returns buildRom("rom-1")
        viewModel.load("rom-1")
        coVerify(exactly = 0) { romRepo.fetchManifest() }
    }

    @Test
    fun `download is a no-op when the rom is not found`() = runTest(testDispatcher) {
        every { romRepo.getRom("missing") } returns null
        viewModel.load("missing")
        viewModel.download()
        coVerify(exactly = 0) { romRepo.download(any(), any()) }
    }

    @Test
    fun `download delegates to the repository once the rom is loaded`() = runTest(testDispatcher) {
        val rom = buildRom("rom-1")
        every { romRepo.getRom("rom-1") } returns rom
        viewModel.load("rom-1")
        viewModel.download()
        coVerify { romRepo.download(rom, any()) }
    }

    @Test
    fun `delete delegates to the repository once the rom is loaded`() = runTest(testDispatcher) {
        val rom = buildRom("rom-1")
        every { romRepo.getRom("rom-1") } returns rom
        viewModel.load("rom-1")
        viewModel.delete()
        coVerify { romRepo.delete(rom) }
    }

    @Test
    fun `uiState reflects in-progress download from the repository`() = runTest(testDispatcher) {
        val progress = mapOf(
            "rom-1" to DownloadProgress(
                romId = "rom-1",
                bytesDownloaded = 50L,
                totalBytes = 100L,
                state = ROMDownloadState.DOWNLOADING,
            ),
        )
        every { romRepo.downloadProgress } returns flowOf(progress)
        val vm = ROMDetailViewModel(romRepo)
        vm.load("rom-1")
        val state = vm.uiState.first { it.progress != null }
        assertThat(state.progress?.state).isEqualTo(ROMDownloadState.DOWNLOADING)
    }

    private fun buildRom(id: String, abis: List<String> = listOf(AbiCompat.ARM64_V8A)) = ROMImage(
        id = id,
        displayName = "Android 7.1.2 Nougat",
        androidVersion = "7.1.2",
        apiLevel = 25,
        description = "Test ROM",
        downloadUrl = "https://example.com/$id.vrom",
        sha256 = "0".repeat(64),
        sizeBytes = 500_000_000L,
        supportedAbis = abis,
        has32BitSupport = true,
        releaseDate = "2026-01-01",
    )
}
