package com.example.waywatch.data.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface LocationTracker {
    fun observeLocation(): Flow<Location>
}
