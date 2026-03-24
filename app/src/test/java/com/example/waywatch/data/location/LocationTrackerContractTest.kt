package com.example.waywatch.data.location

import android.location.Location
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

abstract class LocationTrackerContractTest {
    abstract fun getTracker(): LocationTracker
    abstract fun triggerLocation(location: Location)

    @Test
    fun `test location is emitted`() = runTest {
        val tracker = getTracker()
        val mockLocation = mock(Location::class.java)
        
        // Setup mock
        // Since we are running in runTest, we can collect the first emission
        
        // This is a basic structure for the shared contract test.
        // Specific implementations (like FusedLocationTrackerTest) would 
        // implement triggerLocation according to their inner workings.
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
        val loc = mock(Location::class.java)
        triggerLocation(loc)
        val emitted = tracker.observeLocation().first()
        assertEquals(loc, emitted)
    }
}
