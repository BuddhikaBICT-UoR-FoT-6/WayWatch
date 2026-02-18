package com.example.ceylonqueuebuspulse.traffic

import com.example.ceylonqueuebuspulse.data.network.TomTomAddress
import com.example.ceylonqueuebuspulse.data.network.TomTomPosition
import com.example.ceylonqueuebuspulse.data.network.TomTomSearchApi
import com.example.ceylonqueuebuspulse.data.network.TomTomSearchResponse
import com.example.ceylonqueuebuspulse.data.network.TomTomSearchResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapComposeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank query sets status and does not call api`() = testScope.runTest {
        val api = mockk<TomTomSearchApi>(relaxed = true)
        val locVm = mockk<LocationTrafficViewModel>(relaxed = true)
        val vm = MapComposeViewModel(api, locVm)

        vm.search(" ", "KEY")
        advanceUntilIdle()

        assertEquals("Query is blank", vm.status.value)
        coVerify(exactly = 0) { api.search(any(), any(), any()) }
    }

    @Test
    fun `search success populates places`() = testScope.runTest {
        val api = mockk<TomTomSearchApi>()
        val locVm = mockk<LocationTrafficViewModel>(relaxed = true)
        val response = TomTomSearchResponse(
            results = listOf(
                TomTomSearchResult(
                    position = TomTomPosition(1.0, 2.0),
                    address = TomTomAddress(freeformAddress = "Colombo")
                )
            )
        )
        coEvery { api.search("q", "KEY", 10) } returns response

        val vm = MapComposeViewModel(api, locVm)
        vm.search("q", "KEY")
        advanceUntilIdle()

        assertEquals(1, vm.places.value.size)
        assertEquals("Colombo", vm.places.value[0].label)
        assertEquals(1.0, vm.places.value[0].lat, 0.0)
        assertEquals(2.0, vm.places.value[0].lon, 0.0)
        assertNull(vm.status.value)
        coVerify { api.search("q", "KEY", 10) }
    }

    @Test
    fun `search filters out results with null position`() = testScope.runTest {
        val api = mockk<TomTomSearchApi>()
        val locVm = mockk<LocationTrafficViewModel>(relaxed = true)
        val response = TomTomSearchResponse(
            results = listOf(
                TomTomSearchResult(position = null, address = null),
                TomTomSearchResult(position = TomTomPosition(1.0, 2.0), address = null)
            )
        )
        coEvery { api.search("q", "KEY", 10) } returns response

        val vm = MapComposeViewModel(api, locVm)
        vm.search("q", "KEY")
        advanceUntilIdle()

        assertEquals(1, vm.places.value.size)
        assertEquals(1.0, vm.places.value[0].lat, 0.0)
        assertEquals(2.0, vm.places.value[0].lon, 0.0)
        coVerify { api.search("q", "KEY", 10) }
    }

    @Test
    fun `selectPlace delegates to LocationTrafficViewModel`() = testScope.runTest {
        val api = mockk<TomTomSearchApi>(relaxed = true)
        val locVm = mockk<LocationTrafficViewModel>(relaxed = true)

        val vm = MapComposeViewModel(api, locVm)
        val place = PlaceResult(label = "x", lat = 10.0, lon = 20.0)

        vm.selectPlace(place)

        coVerify { locVm.selectLocation(10.0, 20.0) }
    }

    @Test
    fun `submitSampleForPlace updates status from callback`() = testScope.runTest {
        val api = mockk<TomTomSearchApi>(relaxed = true)
        val locVm = mockk<LocationTrafficViewModel>()
        every {
            locVm.submitSample(any(), any(), any(), any(), any())
        } answers {
            val cb = arg<(Boolean, String?) -> Unit>(4)
            cb(true, null)
        }

        val vm = MapComposeViewModel(api, locVm)
        val place = PlaceResult(label = "x", lat = 10.0, lon = 20.0)

        vm.submitSampleForPlace(place, severity = 4)
        advanceUntilIdle()

        assertEquals("Sample submitted", vm.status.value)
    }

    @Test
    fun `search retries then fails and sets status`() = testScope.runTest {
        val api = mockk<TomTomSearchApi>()
        val locVm = mockk<LocationTrafficViewModel>(relaxed = true)
        coEvery { api.search("bad", "KEY", 10) } throws RuntimeException("network")
        val vm = MapComposeViewModel(api, locVm)

        vm.search("bad", "KEY", maxAttempts = 2)
        advanceUntilIdle()

        assertEquals("Search failed: network", vm.status.value)
        coVerify(exactly = 2) { api.search("bad", "KEY", 10) }
    }
}
