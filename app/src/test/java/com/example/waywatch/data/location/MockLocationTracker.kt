package com.example.waywatch.data.location

import android.location.Location
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class MockLocationTracker : LocationTracker {
    private val locationFlow = MutableSharedFlow<Location>(extraBufferCapacity = 1)

    override fun observeLocation(): Flow<Location> = locationFlow

    fun emitLocation(location: Location) {
        locationFlow.tryEmit(location)
    }
}
