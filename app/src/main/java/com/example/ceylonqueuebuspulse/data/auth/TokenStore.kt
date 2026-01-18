package com.example.ceylonqueuebuspulse.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class TokenStore(private val context: Context) {
    // Create or retrieve a MasterKey for encryption
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val keyAccessToken = "access_token"
    private val keyRefreshToken = "refresh_token"

    val accessTokenFlow: Flow<String?> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == keyAccessToken) {
                trySend(prefs.getString(keyAccessToken, null)).isSuccess
            }
        }

        // emit initial
        trySend(prefs.getString(keyAccessToken, null)).isSuccess

        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val refreshTokenFlow: Flow<String?> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == keyRefreshToken) {
                trySend(prefs.getString(keyRefreshToken, null)).isSuccess
            }
        }

        trySend(prefs.getString(keyRefreshToken, null)).isSuccess

        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    suspend fun setTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(keyAccessToken, accessToken)
            .putString(keyRefreshToken, refreshToken)
            .apply()
    }

    suspend fun setAccessToken(accessToken: String) {
        prefs.edit().putString(keyAccessToken, accessToken).apply()
    }

    suspend fun clear() {
        prefs.edit().remove(keyAccessToken).remove(keyRefreshToken).apply()
    }
}
