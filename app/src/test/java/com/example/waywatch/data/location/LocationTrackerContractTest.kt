package com.example.waywatch.data.location

import android.location.Location
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

abstract class LocationTrackerContractTest {
    abstract fun getTracker(): LocationTracker
    abstract fun triggerLocation(location: Location)

    @Test
    fun `test location is emitted`() = runTest {
        val tracker = getTracker()
        val mockLocation = mockk<Location>(relaxed = true)
        triggerLocation(mockLocation)
        val emitted = tracker.observeLocation().first()
        assertEquals(mockLocation, emitted)
    }
}

class MockLocationTrackerTest : LocationTrackerContractTest() {
    private val tracker = MockLocationTracker()

    override fun getTracker(): LocationTracker = tracker

    override fun triggerLocation(location: Location) {
        tracker.emitLocation(location)
    }

    @Test
    fun `test direct emission`() = runTest {
        val loc = mockk<Location>(relaxed = true)
        triggerLocation(loc)
        val emitted = tracker.observeLocation().first()
        assertEquals(loc, emitted)
    }
}
