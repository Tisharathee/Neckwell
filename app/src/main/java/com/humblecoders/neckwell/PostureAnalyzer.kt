package com.humblecoders.neckwell

import android.util.Log
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.atan2

data class PostureMetrics(
    val cva: Float,              // Craniovertebral Angle (Pitch / forward head tilt) in degrees
    val lateralTilt: Float,      // Lateral neck/head tilt (Roll / side tilt) in degrees
    val shoulderAlignment: Float,// Shoulder-hip alignment
    val isCorrect: Boolean,      // True only if all posture axes are within acceptable thresholds
    val reason: String,
    val posture: String = if (isCorrect) "Good" else "Poor"
)

object PostureAnalyzer {
    private const val TAG = "PostureAnalyzer"
    const val DEV_MODE = true

    private const val LEFT_EAR = 7
    private const val RIGHT_EAR = 8
    private const val LEFT_SHOULDER = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_HIP = 23
    private const val RIGHT_HIP = 24

    // Clinical standard for Craniovertebral Angle (CVA):
    // Normal upright posture: CVA >= 48°
    // Forward head posture (FHP / poor posture): CVA < 48°
    const val CVA_MIN = 48f

    fun isGoodPosture(cva: Float): Boolean = cva >= CVA_MIN
    fun isPoorPosture(cva: Float): Boolean = cva < CVA_MIN
    fun classifyPosture(cva: Float): String = if (isGoodPosture(cva)) "Good" else "Poor"

    // Maximum allowed forward horizontal distance between ear and shoulder in normalized coords
    const val MAX_EAR_SHOULDER_DX = 0.16f

    // Maximum allowed lateral tilt (roll) in degrees
    const val MAX_LATERAL_TILT_DEG = 15f

    const val SHOULDER_HIP_MAX = 0.08f

    fun analyze(result: PoseLandmarkerResult): PostureMetrics? {
        val landmarks = result.landmarks().firstOrNull() ?: return null
        if (landmarks.size < 25) return null

        val leftEarLm = landmarks[LEFT_EAR]
        val rightEarLm = landmarks[RIGHT_EAR]
        val leftShLm = landmarks[LEFT_SHOULDER]
        val rightShLm = landmarks[RIGHT_SHOULDER]

        val leftEarVis = leftEarLm.visibility().orElse(0f)
        val rightEarVis = rightEarLm.visibility().orElse(0f)
        val leftShVis = leftShLm.visibility().orElse(0f)
        val rightShVis = rightShLm.visibility().orElse(0f)

        // Pick whichever side is more visible to the camera (true profile side)
        val leftVis = (leftEarVis + leftShVis) / 2f
        val rightVis = (rightEarVis + rightShVis) / 2f
        val useLeft = leftVis >= rightVis

        val earLm = if (useLeft) leftEarLm else rightEarLm
        val shLm = if (useLeft) leftShLm else rightShLm
        val earVis = earLm.visibility().orElse(0f)
        val shVis = shLm.visibility().orElse(0f)

        val otherEarLm = if (useLeft) rightEarLm else leftEarLm
        val otherEarVis = otherEarLm.visibility().orElse(0f)

        // Torso Alignment (Hip check kept optional — most desk setups won't see hips)
        val hipL = landmarks[LEFT_HIP]
        val hipR = landmarks[RIGHT_HIP]
        val hipVisible = hipL.visibility().orElse(0f) >= 0.4f && hipR.visibility().orElse(0f) >= 0.4f
        val shoulderMidX = (leftShLm.x() + rightShLm.x()) / 2f
        val hipMidX = (hipL.x() + hipR.x()) / 2f
        val shoulderAlignment = if (hipVisible) abs(shoulderMidX - hipMidX) else 0f

        return computeMetrics(
            earX = earLm.x(),
            earY = earLm.y(),
            earVis = earVis,
            shX = shLm.x(),
            shY = shLm.y(),
            shVis = shVis,
            sideLabel = if (useLeft) "Left" else "Right",
            otherEarX = otherEarLm.x(),
            otherEarY = otherEarLm.y(),
            otherEarVis = otherEarVis,
            shoulderAlignment = shoulderAlignment,
            hipVisible = hipVisible
        )
    }

    /**
     * Pure calculation function decoupled from MediaPipe objects for testability and verification.
     */
    fun computeMetrics(
        earX: Float,
        earY: Float,
        earVis: Float,
        shX: Float,
        shY: Float,
        shVis: Float,
        sideLabel: String = "Profile",
        otherEarX: Float? = null,
        otherEarY: Float? = null,
        otherEarVis: Float = 0f,
        shoulderAlignment: Float = 0f,
        hipVisible: Boolean = false
    ): PostureMetrics {
        if (earVis < 0.3f || shVis < 0.3f) {
            val metrics = PostureMetrics(0f, 0f, 0f, false, "Turn sideways — show ear & shoulder")
            Log.d(TAG, "Detection rejected: earVis=$earVis, shVis=$shVis")
            return metrics
        }

        // Image coordinates: Y grows downward (top=0, bottom=1).
        // Upright posture: ear is HIGHER than shoulder, so earY < shY.
        // Therefore dy = shY - earY must be strictly POSITIVE.
        val dy = shY - earY
        val dx = abs(earX - shX)

        // Check if ear is below or level with shoulder (severe slouch / bent over)
        if (dy <= 0.01f) {
            val reason = "Sit upright — head is tilted too far down"
            Log.d(TAG, "[$sideLabel] Poor posture (Head below shoulder): dy=$dy, dx=$dx")
            return PostureMetrics(0f, 0f, shoulderAlignment, false, reason)
        }

        // 1. Craniovertebral Angle (CVA) in degrees: angle of ear-shoulder vector with horizontal
        val cvaRad = atan2(dy, dx)
        val cva = Math.toDegrees(cvaRad.toDouble()).toFloat()

        // 2. Lateral Roll / Side-Tilt: computed if both ears are visible
        val lateralTilt = if (otherEarX != null && otherEarY != null && otherEarVis >= 0.35f) {
            val earDx = abs(otherEarX - earX)
            val earDy = otherEarY - earY
            if (earDx > 0.01f) Math.toDegrees(atan2(earDy, earDx).toDouble()).toFloat() else 0f
        } else {
            0f // Side profile with single ear visible: relies on ESP32 IMU for roll
        }

        // Gate Checks: ONLY accept if within verified good posture thresholds
        val cvaOk = isGoodPosture(cva)
        val dxOk = dx <= MAX_EAR_SHOULDER_DX
        val rollOk = abs(lateralTilt) <= MAX_LATERAL_TILT_DEG
        val alignmentOk = !hipVisible || shoulderAlignment <= SHOULDER_HIP_MAX
        val isCorrect = cvaOk && dxOk && rollOk && alignmentOk
        val posture = if (isCorrect) "Good" else "Poor"

        val reason = when {
            isCorrect -> "Good posture — hold still"
            !cvaOk || !dxOk -> "Sit straight (CVA: ${cva.toInt()}°, dx: ${"%.2f".format(dx)})"
            !rollOk -> "Level your head (tilt: ${lateralTilt.toInt()}°)"
            !alignmentOk -> "Align shoulders over hips"
            else -> "Adjust posture"
        }

        if (DEV_MODE) {
            Log.d(
                TAG,
                "[$sideLabel] Raw: ear=(${"%.3f".format(earX)}, ${"%.3f".format(earY)}), sh=(${"%.3f".format(shX)}, ${"%.3f".format(shY)}) | dx=${"%.3f".format(dx)}, dy=${"%.3f".format(dy)} -> Raw CVA=${"%.1f".format(cva)}°, Threshold=${CVA_MIN}° | Posture=$posture (cvaOk=$cvaOk, dxOk=$dxOk, isCorrect=$isCorrect, reason='$reason')"
            )
        }

        return PostureMetrics(cva, lateralTilt, shoulderAlignment, isCorrect, reason, posture)
    }
}