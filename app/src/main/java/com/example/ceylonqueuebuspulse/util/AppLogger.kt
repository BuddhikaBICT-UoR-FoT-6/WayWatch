package com.example.ceylonqueuebuspulse.util

import android.util.Log
import com.example.ceylonqueuebuspulse.BuildConfig

/**
 * Small structured logger facade.
 * - Debug: logs to Logcat.
 * - Release: logs warnings/errors.
 * - If Firebase Crashlytics is on the classpath, errors are also forwarded as non-fatals.
 */
object AppLogger {

    fun d(tag: String, message: String) {
        if (!BuildConfig.LOGGING_ENABLED) return
        runCatching { Log.d(tag, message) }
    }

    fun i(tag: String, message: String) {
        if (!BuildConfig.LOGGING_ENABLED) return
        runCatching { Log.i(tag, message) }
    }

    fun w(tag: String, message: String, tr: Throwable? = null) {
        runCatching {
            if (tr != null) Log.w(tag, message, tr) else Log.w(tag, message)
        }
    }

    fun e(tag: String, message: String, tr: Throwable? = null, report: Boolean = true) {
        runCatching {
            if (tr != null) Log.e(tag, message, tr) else Log.e(tag, message)
        }
        if (report) {
            reportToCrashlytics(tag, message, tr)
        }
    }

    private fun reportToCrashlytics(tag: String, message: String, tr: Throwable?) {
        try {
            val clazz = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics")
            val instance = clazz.getMethod("getInstance").invoke(null)
            runCatching {
                clazz.getMethod("log", String::class.java).invoke(instance, "$tag: $message")
            }
            if (tr != null) {
                runCatching {
                    clazz.getMethod("recordException", Throwable::class.java).invoke(instance, tr)
                }
            }
        } catch (_: Throwable) {
            // Crashlytics not available
        }
    }
}
