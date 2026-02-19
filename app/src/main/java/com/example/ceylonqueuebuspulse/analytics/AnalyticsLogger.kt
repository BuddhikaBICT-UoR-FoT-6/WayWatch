package com.example.ceylonqueuebuspulse.analytics

import android.os.Bundle

/**
 * Tiny wrapper around Firebase Analytics so feature code doesn't depend on the Firebase API directly.
 *
 * Implementation uses reflection so builds and unit tests don't require a Firebase runtime.
 */
interface AnalyticsLogger {
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap())
}

class FirebaseAnalyticsLogger : AnalyticsLogger {
    override fun logEvent(name: String, params: Map<String, Any?>) {
        try {
            val analyticsClazz = Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
            val instance = analyticsClazz
                .getMethod("getInstance", android.content.Context::class.java)
                .invoke(null, com.example.ceylonqueuebuspulse.AppGlobals.appContext)

            val bundle = Bundle()
            params.forEach { (k, v) ->
                when (v) {
                    null -> Unit
                    is String -> bundle.putString(k, v)
                    is Int -> bundle.putInt(k, v)
                    is Long -> bundle.putLong(k, v)
                    is Double -> bundle.putDouble(k, v)
                    is Float -> bundle.putFloat(k, v)
                    is Boolean -> bundle.putBoolean(k, v)
                    else -> bundle.putString(k, v.toString())
                }
            }

            analyticsClazz
                .getMethod("logEvent", String::class.java, Bundle::class.java)
                .invoke(instance, name, bundle)
        } catch (_: Throwable) {
            // ignore
        }
    }
}
