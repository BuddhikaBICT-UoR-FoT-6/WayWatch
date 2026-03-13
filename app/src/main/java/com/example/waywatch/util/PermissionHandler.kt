package com.example.waywatch.util

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class PermissionHandler(private val activity: ComponentActivity) {

    private var onPermissionsGranted: (() -> Unit)? = null

    private val locationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            onPermissionsGranted?.invoke()
        }
    }

    private val notificationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                requestPermissions()
            }
        })
    }

    fun setOnPermissionsGrantedListener(listener: () -> Unit) {
        onPermissionsGranted = listener
    }

    fun requestPermissions() {
        val fineGranted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            onPermissionsGranted?.invoke()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        if (Build.VERSION.SDK_INT >= 33) {
            val notifyGranted = ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifyGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
