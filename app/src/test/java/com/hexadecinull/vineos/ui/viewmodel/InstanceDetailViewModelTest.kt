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

    @Test
    fun `initial uiState has no instance until load is called`() = runTest {
        assertThat(viewModel.uiState.first().instance).isNull()
    }

    @Test
    fun `load surfaces the matching instance from the repository`() = runTest {
        viewModel.load("id-1")
        assertThat(viewModel.uiState.first().instance?.id).isEqualTo("id-1")
    }

    @Test
    fun `load with an unknown id surfaces no instance`() = runTest {
        viewModel.load("does-not-exist")
        assertThat(viewModel.uiState.first().instance).isNull()
    }

    @Test
    fun `launch updates status to BOOTING and starts the vm`() = runTest {
        val instance = buildInstance("launch-id")
        viewModel.launch(instance)
        coVerify { instanceRepo.updateStatus("launch-id", VMStatus.BOOTING) }
        coVerify { instanceRepo.touchLastUsed("launch-id") }
        coVerify { vmManager.startInstance(instance) }
    }

    @Test
    fun `stop calls vmManager and resets status to STOPPED`() = runTest {
        val instance = buildInstance("stop-id", status = VMStatus.RUNNING)
        viewModel.stop(instance)
        coVerify { vmManager.stopInstance(instance) }
        coVerify { instanceRepo.updateStatus("stop-id", VMStatus.STOPPED) }
    }

    @Test
    fun `delete on a stopped instance does not force-kill first`() = runTest {
        val instance = buildInstance("del-id", status = VMStatus.STOPPED)
        viewModel.delete(instance)
        coVerify(exactly = 0) { vmManager.killInstance(any()) }
        coVerify { instanceRepo.delete(instance) }
    }

    @Test
    fun `delete on a running instance kills it first`() = runTest {
        val instance = buildInstance("del-running-id", status = VMStatus.RUNNING)
        viewModel.delete(instance)
        coVerify { vmManager.killInstance("del-running-id") }
        coVerify { instanceRepo.delete(instance) }
    }

    @Test
    fun `refreshDiagnostics surfaces vmManager diagnostics text`() = runTest {
        viewModel.load("id-1")
        every { vmManager.getDiagnostics("id-1") } returns "mount table: ok"
        viewModel.refreshDiagnostics()
        assertThat(viewModel.uiState.first().diagnostics).isEqualTo("mount table: ok")
    }

    private fun buildInstance(
        id: String,
        status: VMStatus = VMStatus.STOPPED,
    ) = VMInstance(
        id = id,
        name = "Instance $id",
        romId = "vine-rom-7",
        romVersion = "7.1.2",
        storagePath = "/data/instances/$id",
        status = status,
        androidVersionDisplay = "Android 7.1 Nougat",
    )
}
