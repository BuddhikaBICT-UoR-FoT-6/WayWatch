// Edited: 2025-12-28
// Purpose: Encapsulates FusedLocationProviderClient setup and controls to start/stop location updates, with a simple permission check helper.

package com.example.waywatch.location

// Android permission and context utilities
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

// Google Play Services Location APIs
import com.google.android.gms.location.*

// Simple helper class to stream device location updates to a provided callback
class LocationStreamer(context: Context) {
    // Fused Location client used to request location updates
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    // Request configuration: high accuracy, update every ~5s, min interval ~2.5s
    private val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
        .setMinUpdateIntervalMillis(2_500L)
        .build()

    // Check whether either fine or coarse location permission is granted
    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    // Start streaming location updates using the provided callback and looper
    // Caller must ensure location permission is granted before invoking this
    @SuppressLint("MissingPermission")
    fun start(callback: LocationCallback, looper: android.os.Looper) {
        fusedClient.requestLocationUpdates(request, callback, looper)
    }

    // Stop streaming location updates for the provided callback
    fun stop(callback: LocationCallback) {
        fusedClient.removeLocationUpdates(callback)
    }
}
