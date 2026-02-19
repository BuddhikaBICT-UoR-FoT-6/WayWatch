package com.example.ceylonqueuebuspulse

import android.content.Context

/**
 * Simple global accessor used by reflection-based analytics/logger wrappers.
 *
 * This avoids plumbing Android Context through every layer.
 */
object AppGlobals {
    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
