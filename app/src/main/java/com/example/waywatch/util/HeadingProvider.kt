package com.example.waywatch.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Provides a stream of compass headings (0..360) degrees.
 * Uses TYPE_ROTATION_VECTOR when available.
 */
class HeadingProvider(context: Context) {

    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    fun headings(): Flow<Float> = callbackFlow {
        val sensor = rotationSensor
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val rotMat = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                try {
                    SensorManager.getRotationMatrixFromVector(rotMat, event.values)
                    SensorManager.getOrientation(rotMat, orientation)
                    // azimuth in radians (-pi..pi)
                    var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    if (azimuthDeg < 0) azimuthDeg += 360f
                    trySend(azimuthDeg)
                } catch (_: Throwable) {
                    // ignore
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
