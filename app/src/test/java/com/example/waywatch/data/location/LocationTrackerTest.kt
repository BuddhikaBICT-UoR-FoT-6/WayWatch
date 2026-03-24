package com.example.waywatch.data.location

import android.location.Location
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.mock

class LocationTrackerTest {

    private val tracker = MockLocationTracker()

    @Test
    fun `emitted location is received by observer`() = runTest {
        val loc = mock(Location::class.java)
        tracker.emitLocation(loc)
        val received = tracker.observeLocation().first()
        assertEquals(loc, received)
    }

    @Test
    fun `multiple emissions are received in order`() = runTest {
        val loc1 = mock(Location::class.java)
        val loc2 = mock(Location::class.java)
        tracker.emitLocation(loc1)
        val first = tracker.observeLocation().first()
        assertNotNull(first)
        tracker.emitLocation(loc2)
        val second = tracker.observeLocation().first()
        assertNotNull(second)
    }

    @Test
    fun `observeLocation returns a non-null flow`() {
        assertNotNull(tracker.observeLocation())
    }
}
