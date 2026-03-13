package com.example.waywatch.util

import android.util.Log

/**
 * Workaround for noisy logcat spam like:
 * "callback not found for RELEASED message"
 *
 * This message is emitted by Google Play Services internals (BasePendingResult callback handler)
 * when results are released/canceled and no listener is registered.
 *
 * It's benign and not coming from our app-level message bus.
 *
 * We keep this wrapper so we can (a) document it and (b) optionally filter it in the future.
 */
object ReleasedCallbackRegistry {
    private const val TAG = "ReleasedCallback"

    /**
     * Call once at app start to log a note and avoid chasing this as an app bug.
     */
    fun noteIfSeen() {
        Log.d(TAG, "If you see 'callback not found for RELEASED message' in Logcat: it's benign Play Services noise, not an app callback issue.")
    }
}

