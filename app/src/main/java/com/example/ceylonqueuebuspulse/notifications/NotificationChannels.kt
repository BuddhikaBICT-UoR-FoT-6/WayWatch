package com.example.ceylonqueuebuspulse.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CHANNEL_SEVERE_TRAFFIC = "severe_traffic"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val channel = NotificationChannel(
            CHANNEL_SEVERE_TRAFFIC,
            "Severe traffic alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when severe incidents are detected on watched routes"
        }

        manager.createNotificationChannel(channel)
    }
}
