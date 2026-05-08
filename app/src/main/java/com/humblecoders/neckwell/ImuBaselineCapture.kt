package com.humblecoders.neckwell

import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

data class ImuSample(val ax: Float, val ay: Float, val az: Float)  // accelerometer gravity vector

data class Baseline(
    val pitch: Float,         // degrees
    val roll: Float,          // degrees
    val sampleCount: Int,
    val capturedAt: Long = System.currentTimeMillis()
)

/**
 * Mock IMU source — pretends to be the ESP32-C6 streaming LSM6DSO data at 50 Hz.
 * Replace this with real BLE characteristic subscription later; the rest of the
 * pipeline stays unchanged.
 */
object MockImuStream {
    // Simulates a device worn with slight forward pitch (~8°) and minor roll (~2°),
    // plus realistic sensor noise. Tune these to whatever neutral your firmware expects.
    private const val TRUE_PITCH_DEG = 8f
    private const val TRUE_ROLL_DEG = 2f
    private const val NOISE_DEG = 0.8f

    fun sample(): ImuSample {
        val pitchRad = Math.toRadians((TRUE_PITCH_DEG + Random.nextFloat() * NOISE_DEG - NOISE_DEG / 2).toDouble())
        val rollRad = Math.toRadians((TRUE_ROLL_DEG + Random.nextFloat() * NOISE_DEG - NOISE_DEG / 2).toDouble())
        // Gravity vector for given pitch/roll (standard aerospace convention, g = 1)
        val ax = -Math.sin(pitchRad).toFloat()
        val ay = (Math.cos(pitchRad) * Math.sin(rollRad)).toFloat()
        val az = (Math.cos(pitchRad) * Math.cos(rollRad)).toFloat()
        return ImuSample(ax, ay, az)
    }
}

object BaselineCapture {
    /**
     * Streams IMU samples for [durationMs] at ~[hz] samples/sec, averages the
     * gravity vector, and returns pitch/roll baseline in degrees.
     * onProgress emits 0.0..1.0 so the UI can show a progress ring.
     */
    suspend fun capture(
        durationMs: Long = 5_000,
        hz: Int = 50,
        onProgress: (Float) -> Unit = {}
    ): Baseline {
        val intervalMs = 1000L / hz
        val totalSamples = (durationMs / intervalMs).toInt()
        var sumAx = 0f; var sumAy = 0f; var sumAz = 0f
        var count = 0

        repeat(totalSamples) { i ->
            val s = MockImuStream.sample()
            sumAx += s.ax; sumAy += s.ay; sumAz += s.az
            count++
            onProgress((i + 1).toFloat() / totalSamples)
            delay(intervalMs)
        }

        val meanAx = sumAx / count
        val meanAy = sumAy / count
        val meanAz = sumAz / count

        // Derive pitch/roll from averaged gravity vector
        val pitchRad = atan2(-meanAx, sqrt(meanAy * meanAy + meanAz * meanAz))
        val rollRad = atan2(meanAy, meanAz)

        return Baseline(
            pitch = Math.toDegrees(pitchRad.toDouble()).toFloat(),
            roll = Math.toDegrees(rollRad.toDouble()).toFloat(),
            sampleCount = count
        )
    }
}