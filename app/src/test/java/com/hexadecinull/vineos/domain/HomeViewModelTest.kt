package com.hexadecinull.vineos.domain

import com.google.common.truth.Truth.assertThat
import com.hexadecinull.vineos.data.models.VMInstance
import com.hexadecinull.vineos.data.models.VMStatus
import com.hexadecinull.vineos.data.repository.InstanceRepository
import com.hexadecinull.vineos.ui.viewmodel.HomeViewModel
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
class HomeViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var instanceRepo: InstanceRepository
    private lateinit var vmManager: VineVMManager
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        instanceRepo = mockk(relaxed = true)
        vmManager = mockk(relaxed = true)
        every { instanceRepo.observeAll() } returns flowOf(emptyList())
        every { vmManager.hostSupports32bit } returns true
        viewModel = HomeViewModel(instanceRepo, vmManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // uiState.first { predicate } subscribes and suspends until the flow
    // actually produces a value matching the predicate, rather than reading
    // .value after a fixed advanceUntilIdle() and hoping enough dispatcher
    // hops have happened.

    @Test
    fun `initial uiState has empty instances and isLoading false`() = runTest(testDispatcher) {
        val state = viewModel.uiState.first { !it.isLoading }
        assertThat(state.instances).isEmpty()
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
    }

    @Test
    fun `uiState reflects instances from repository`() = runTest(testDispatcher) {
        val instances = listOf(buildInstance("1"), buildInstance("2"))
        every { instanceRepo.observeAll() } returns flowOf(instances)
        val vm = HomeViewModel(instanceRepo, vmManager)
        val state = vm.uiState.first { it.instances.isNotEmpty() }
        assertThat(state.instances).hasSize(2)
    }

    @Test
    fun `launchInstance updates status to BOOTING and calls vmManager`() = runTest(testDispatcher) {
        val instance = buildInstance("test-id")
        viewModel.launchInstance(instance)
        coVerify { instanceRepo.updateStatus("test-id", VMStatus.BOOTING) }
        coVerify { instanceRepo.touchLastUsed("test-id") }
        coVerify { vmManager.startInstance(instance) }
    }

    @Test
    fun `stopInstance calls vmManager and updates status to STOPPED`() = runTest(testDispatcher) {
        val instance = buildInstance("stop-id")
        viewModel.stopInstance(instance)
        coVerify { vmManager.stopInstance(instance) }
        coVerify { instanceRepo.updateStatus("stop-id", VMStatus.STOPPED) }
    }

    @Test
    fun `deleteInstance calls repo delete`() = runTest(testDispatcher) {
        val instance = buildInstance("del-id", status = VMStatus.STOPPED)
        viewModel.deleteInstance(instance)
        coVerify { instanceRepo.delete(instance) }
    }

    @Test
    fun `deleteInstance stops running instance before deleting`() = runTest(testDispatcher) {
        val instance = buildInstance("del-run-id", status = VMStatus.RUNNING)
        viewModel.deleteInstance(instance)
        coVerify { vmManager.killInstance("del-run-id") }
        coVerify { instanceRepo.delete(instance) }
    }

    @Test
    fun `hostSupports32bit delegates to vmManager`() {
        every { vmManager.hostSupports32bit } returns false
        val vm = HomeViewModel(instanceRepo, vmManager)
        assertThat(vm.hostSupports32bit).isFalse()
    }

    @Test
    fun `clearError resets error state`() = runTest(testDispatcher) {
        viewModel.clearError()
        val state = viewModel.uiState.first { !it.isLoading }
        assertThat(state.error).isNull()
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
