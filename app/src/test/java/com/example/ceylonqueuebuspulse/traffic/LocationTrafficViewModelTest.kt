package com.example.waywatch.traffic

import com.example.waywatch.data.network.DebugApi
import com.example.waywatch.data.network.ProviderPointResponse
import com.example.waywatch.data.repository.AppResult
import com.example.waywatch.data.repository.TrafficAggregationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationTrafficViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `selectLocation calls debug provider`() = scope.runTest {
        val debugApi = mockk<DebugApi>()
        coEvery { debugApi.providerPoint(any(), any()) } returns ProviderPointResponse(ok = true, mapped = emptyMap(), provider = emptyMap())

        val repo = mockk<TrafficAggregationRepository>(relaxed = true)
        val vm = LocationTrafficViewModel(debugApi, repo)

        vm.selectLocation(10.0, 20.0)
        advanceUntilIdle()

        coVerify { debugApi.providerPoint(10.0, 20.0) }
    }

    @Test
    fun `selectLocation success sets provider and status`() = scope.runTest {
        val debugApi = mockk<DebugApi>()
        val payload = mapOf<String, Any>("foo" to "bar")
        coEvery { debugApi.providerPoint(10.0, 20.0) } returns ProviderPointResponse(ok = true, mapped = payload, provider = payload)

        val repo = mockk<TrafficAggregationRepository>(relaxed = true)
        val vm = LocationTrafficViewModel(debugApi, repo)

        vm.selectLocation(10.0, 20.0)
        advanceUntilIdle()

        assertEquals("Provider lookup complete", vm.status.value)
        assertEquals(payload, vm.provider.value?.mapped)
    }

    @Test
    fun `selectLocation error sets status`() = scope.runTest {
        val debugApi = mockk<DebugApi>()
        coEvery { debugApi.providerPoint(10.0, 20.0) } returns ProviderPointResponse(ok = false, mapped = null, provider = null)

        val repo = mockk<TrafficAggregationRepository>(relaxed = true)
        val vm = LocationTrafficViewModel(debugApi, repo)

        vm.selectLocation(10.0, 20.0)
        advanceUntilIdle()

        // ok=false triggers applyMockData which sets mock status
        assertEquals("Using mock alternative data (API offline)", vm.status.value)
    }

    @Test
    fun `selectLocation exception sets status`() = scope.runTest {
        val debugApi = mockk<DebugApi>()
        coEvery { debugApi.providerPoint(10.0, 20.0) } throws RuntimeException("network")

        val repo = mockk<TrafficAggregationRepository>(relaxed = true)
        val vm = LocationTrafficViewModel(debugApi, repo)

        vm.selectLocation(10.0, 20.0)
        advanceUntilIdle()

        // exception triggers applyMockData
        assertEquals("Using mock alternative data (API offline)", vm.status.value)
    }

    @Test
    fun `submitSample delegates to repository and uses aligned windowStartMs`() = scope.runTest {
        val debugApi = mockk<DebugApi>(relaxed = true)
        val repo = mockk<TrafficAggregationRepository>()

        val windowStartSlot = slot<Long>()
        val severitySlot = slot<Double>()

        coEvery {
            repo.submitUserSample(
                any(),
                capture(windowStartSlot),
                any(),
                capture(severitySlot),
                any(),
                any()
            )
        } returns AppResult.Ok(Unit)

        val vm = LocationTrafficViewModel(debugApi, repo)

        vm.submitSample(null, 4, 10.0, 20.0) { _, _ -> }
        advanceUntilIdle()

        coVerify { repo.submitUserSample(any(), any(), any(), any(), any(), any()) }

        val windowSizeMs = 15 * 60 * 1000L
        assertEquals(0L, windowStartSlot.captured % windowSizeMs)
        assertTrue(severitySlot.captured == 4.0)
    }
}
