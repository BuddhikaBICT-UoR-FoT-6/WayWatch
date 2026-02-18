package com.example.ceylonqueuebuspulse.data.auth

import android.content.Context
import android.net.Uri

/**
 * Very small store for "deep link pending until user logs in".
 *
 * Why prefs:
 * - survives process death
 * - doesn't require DB
 */
class PendingDeepLinkStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun set(uri: Uri) {
        prefs.edit().putString(KEY_URI, uri.toString()).apply()
    }

    fun consume(): Uri? {
        val raw = prefs.getString(KEY_URI, null) ?: return null
        prefs.edit().remove(KEY_URI).apply()
        return runCatching { Uri.parse(raw) }.getOrNull()
    }

    fun clear() {
        prefs.edit().remove(KEY_URI).apply()
    }

    private companion object {
        private const val PREFS_NAME = "pending_deeplink"
        private const val KEY_URI = "uri"
    }
}
