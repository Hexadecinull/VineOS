package com.hexadecinull.vineos.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.data.repository.InstanceRepository
import com.hexadecinull.vineos.domain.VineVMManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstanceDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var instanceRepo: InstanceRepository
    private lateinit var vmManager: VineVMManager
    private lateinit var viewModel: InstanceDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        instanceRepo = mockk(relaxed = true)
        vmManager = mockk(relaxed = true)
        every { instanceRepo.observeAll() } returns flowOf(listOf(buildInstance("id-1")))
        viewModel = InstanceDetailViewModel(instanceRepo, vmManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // uiState is a WhileSubscribed StateFlow: it only produces real combined
    // values once something collects it. backgroundScope keeps a collector
    // alive for the rest of the test; advanceUntilIdle lets its pending work
    // actually run before we read .value.
    private suspend fun TestScope.keepHot() {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun `initial uiState has no instance until load is called`() = runTest(testDispatcher) {
        keepHot()
        assertThat(viewModel.uiState.value.instance).isNull()
    }

    @Test
    fun `load surfaces the matching instance from the repository`() = runTest(testDispatcher) {
        keepHot()
        viewModel.load("id-1")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.instance?.id).isEqualTo("id-1")
    }

    @Test
    fun `load with an unknown id surfaces no instance`() = runTest(testDispatcher) {
        keepHot()
        viewModel.load("does-not-exist")
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.instance).isNull()
    }

    @Test
    fun `launch updates status to BOOTING and starts the vm`() = runTest(testDispatcher) {
        val instance = buildInstance("launch-id")
        viewModel.launch(instance)
        coVerify { instanceRepo.updateStatus("launch-id", VMStatus.BOOTING) }
        coVerify { instanceRepo.touchLastUsed("launch-id") }
        coVerify { vmManager.startInstance(instance) }
    }

    @Test
    fun `stop calls vmManager and resets status to STOPPED`() = runTest(testDispatcher) {
        val instance = buildInstance("stop-id", status = VMStatus.RUNNING)
        viewModel.stop(instance)
        coVerify { vmManager.stopInstance(instance) }
        coVerify { instanceRepo.updateStatus("stop-id", VMStatus.STOPPED) }
    }

    @Test
    fun `delete on a stopped instance does not force-kill first`() = runTest(testDispatcher) {
        val instance = buildInstance("del-id", status = VMStatus.STOPPED)
        viewModel.delete(instance)
        coVerify(exactly = 0) { vmManager.killInstance(any()) }
        coVerify { instanceRepo.delete(instance) }
    }

    @Test
    fun `delete on a running instance kills it first`() = runTest(testDispatcher) {
        val instance = buildInstance("del-running-id", status = VMStatus.RUNNING)
        viewModel.delete(instance)
        coVerify { vmManager.killInstance("del-running-id") }
        coVerify { instanceRepo.delete(instance) }
    }

    @Test
    fun `refreshDiagnostics surfaces vmManager diagnostics text`() = runTest(testDispatcher) {
        keepHot()
        viewModel.load("id-1")
        every { vmManager.getDiagnostics("id-1") } returns "mount table: ok"
        viewModel.refreshDiagnostics()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.diagnostics).isEqualTo("mount table: ok")
    }

    private fun buildInstance(id: String, status: VMStatus = VMStatus.STOPPED) = VMInstance(
        id = id,
        name = "Instance $id",
        romId = "vine-rom-7",
        romVersion = "7.1.2",
        storagePath = "/data/instances/$id",
        status = status,
        androidVersionDisplay = "Android 7.1 Nougat",
    )
}
