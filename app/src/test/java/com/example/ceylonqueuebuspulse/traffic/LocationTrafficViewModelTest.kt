package com.example.ceylonqueuebuspulse.traffic

import com.example.ceylonqueuebuspulse.data.network.DebugApi
import com.example.ceylonqueuebuspulse.data.network.model.ApiResponse
import com.example.ceylonqueuebuspulse.data.repository.TrafficAggregationRepository
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
        coEvery { debugApi.providerPoint(any(), any()) } returns ApiResponse(ok = true, data = emptyMap())

        val repo = mockk<TrafficAggregationRepository>(relaxed = true)
        val vm = LocationTrafficViewModel(debugApi, repo)

        vm.selectLocation(10.0, 20.0)
        advanceUntilIdle()

        coVerify { debugApi.providerPoint(10.0, 20.0) }
    }

    @Test
    fun `selectLocation success sets provider and status`() = scope.runTest {
        val debugApi = mockk<DebugApi>()
        val payload = mapOf("foo" to "bar")
        coEvery { debugApi.providerPoint(10.0, 20.0) } returns ApiResponse(ok = true, data = payload)

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
        coEvery { debugApi.providerPoint(10.0, 20.0) } returns ApiResponse(ok = false, error = "boom")

        val repo = mockk<TrafficAggregationRepository>(relaxed = true)
        val vm = LocationTrafficViewModel(debugApi, repo)

        vm.selectLocation(10.0, 20.0)
        advanceUntilIdle()

        assertEquals("Provider lookup returned error: boom", vm.status.value)
    }

    @Test
    fun `selectLocation exception sets status`() = scope.runTest {
        val debugApi = mockk<DebugApi>()
        coEvery { debugApi.providerPoint(10.0, 20.0) } throws RuntimeException("network")

        val repo = mockk<TrafficAggregationRepository>(relaxed = true)
        val vm = LocationTrafficViewModel(debugApi, repo)

        vm.selectLocation(10.0, 20.0)
        advanceUntilIdle()

        assertEquals("Provider lookup failed: network", vm.status.value)
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
        } returns ApiResponse(ok = true, data = emptyMap())

        val vm = LocationTrafficViewModel(debugApi, repo)

        vm.submitSample(null, 4, 10.0, 20.0) { _, _ -> }
        advanceUntilIdle()

        coVerify { repo.submitUserSample(any(), any(), any(), any(), any(), any()) }

        val windowSizeMs = 15 * 60 * 1000L
        assertEquals(0L, windowStartSlot.captured % windowSizeMs)
        assertTrue(severitySlot.captured == 4.0)
    }
}
