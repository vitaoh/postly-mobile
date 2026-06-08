package com.victor.postly.utils

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detecta quando o usuário chacoalha o dispositivo e dispara [onShake].
 *
 * Uso:
 *   val detector = ShakeDetector { openCamera() }
 *   detector.start(sensorManager)   // onResume
 *   detector.stop(sensorManager)    // onPause
 */
class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {

    companion object {
        // Limiar de aceleração (m/s²) para considerar como shake.
        // 12 é um bom valor — ignora movimentos comuns mas detecta agitação real.
        private const val SHAKE_THRESHOLD_G = 12f
        // Intervalo mínimo entre dois shakes consecutivos (ms)
        private const val MIN_INTERVAL_MS = 1_500L
    }

    private var lastShakeTime = 0L

    fun start(sensorManager: SensorManager) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop(sensorManager: SensorManager) {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Magnitude da aceleração total, removendo a gravidade (≈9.8)
        val gForce = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

        if (gForce >= SHAKE_THRESHOLD_G) {
            val now = System.currentTimeMillis()
            if (now - lastShakeTime > MIN_INTERVAL_MS) {
                lastShakeTime = now
                onShake()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
