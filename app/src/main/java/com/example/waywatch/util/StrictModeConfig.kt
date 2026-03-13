package com.example.waywatch.util

import android.os.Build
import android.os.StrictMode

object StrictModeConfig {
    fun enableForDebug() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )

        val vmBuilder = StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .penaltyLog()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            vmBuilder.detectNonSdkApiUsage()
        }

        StrictMode.setVmPolicy(vmBuilder.build())
    }
}
