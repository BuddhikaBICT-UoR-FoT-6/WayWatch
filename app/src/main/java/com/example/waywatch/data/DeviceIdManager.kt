package com.example.waywatch.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "device_prefs")

class DeviceIdManager(private val context: Context) {

    private val DEVICE_ID_KEY = stringPreferencesKey("device_id")

    suspend fun getDeviceId(): String {
        val existingId = context.dataStore.data.map { preferences ->
            preferences[DEVICE_ID_KEY]
        }.first()

        if (existingId != null) {
            return existingId
        }

        val newId = UUID.randomUUID().toString()
        context.dataStore.edit { preferences ->
            preferences[DEVICE_ID_KEY] = newId
        }
        return newId
    }
}
