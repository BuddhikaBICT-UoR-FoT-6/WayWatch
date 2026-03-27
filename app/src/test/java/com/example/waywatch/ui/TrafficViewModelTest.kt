package com.example.waywatch.ui

import com.example.waywatch.data.location.MockLocationTracker
import com.example.waywatch.data.repository.AppError
import com.example.waywatch.data.repository.AppResult
import com.example.waywatch.data.repository.TrafficAggregationRepository
import com.example.waywatch.data.repository.TrafficRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: TrafficRepository
    private lateinit var aggregationRepository: TrafficAggregationRepository
    private lateinit var locationTracker: MockLocationTracker
    private lateinit var vm: TrafficViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        aggregationRepository = mockk(relaxed = true)
        locationTracker = MockLocationTracker()

        coEvery { repository.reports } returns flowOf(emptyList())
        coEvery { aggregationRepository.observeAggregatedTraffic(any()) } returns flowOf(emptyList())

        vm = TrafficViewModel(repository, aggregationRepository, locationTracker)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has no error and not loading`() = runTest {
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val state = vm.uiState.value
        assertNull(state.errorMessage)
        assertEquals(false, state.isLoading)
        job.cancel()
    }

    @Test
    fun `selectRoute updates selectedRouteId`() = runTest {
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.selectRoute("120")
        advanceUntilIdle()
        assertEquals("120", vm.uiState.value.selectedRouteId)
        job.cancel()
    }

    @Test
    fun `submitUserLocation calls repository submitUserUpdate`() = runTest {
        val job = launch { vm.uiState.collect {} }
        coEvery { repository.submitUserUpdate(any()) } returns AppResult.Ok(Unit)
        vm.submitUserLocation(6.9, 79.8, "138")
        advanceUntilIdle()
        coVerify { repository.submitUserUpdate(any()) }
        job.cancel()
    }

    @Test
    fun `submitUserLocation with blank routeId sets error`() = runTest {
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.submitUserLocation(6.9, 79.8, "")
        advanceUntilIdle()
        assertEquals("Route ID is required", vm.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun `refresh on error sets errorMessage`() = runTest {
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        coEvery { aggregationRepository.getAggregates(any()) } returns
            AppResult.Err(AppError.Server("Network error"))
        vm.refresh()
        advanceUntilIdle()
        assertEquals("Network error", vm.uiState.value.errorMessage)
        job.cancel()
    }

    @Test
    fun `clearError removes errorMessage`() = runTest {
        val job = launch { vm.uiState.collect {} }
        advanceUntilIdle()
        vm.submitUserLocation(6.9, 79.8, "")
        advanceUntilIdle()
        vm.clearError()
        advanceUntilIdle()
        assertNull(vm.uiState.value.errorMessage)
        job.cancel()
    }
}
