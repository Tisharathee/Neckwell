package com.humblecoders.neckwell

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.atan2
import kotlin.math.abs

data class PostureMetrics(
    val cva: Float,
    val shoulderAlignment: Float,
    val isCorrect: Boolean,
    val reason: String
)

object PostureAnalyzer {
    private const val LEFT_EAR = 7
    private const val RIGHT_EAR = 8
    private const val LEFT_SHOULDER = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_HIP = 23
    private const val RIGHT_HIP = 24

    // Lowered for frontal webcam demo — tune up to 48 for true side-profile use
    private const val CVA_MIN = 35f
    private const val SHOULDER_HIP_MAX = 0.08f

    fun analyze(result: PoseLandmarkerResult): PostureMetrics? {
        val landmarks = result.landmarks().firstOrNull() ?: return null
        if (landmarks.size < 25) return null

        // Pick whichever side shows a bigger horizontal ear-shoulder gap (true profile side)
        val leftDx = abs(landmarks[LEFT_EAR].x() - landmarks[LEFT_SHOULDER].x())
        val rightDx = abs(landmarks[RIGHT_EAR].x() - landmarks[RIGHT_SHOULDER].x())
        val useLeft = leftDx >= rightDx

        val earLm = if (useLeft) landmarks[LEFT_EAR] else landmarks[RIGHT_EAR]
        val shLm = if (useLeft) landmarks[LEFT_SHOULDER] else landmarks[RIGHT_SHOULDER]
        val earVis = earLm.visibility().orElse(0f)
        val shVis = shLm.visibility().orElse(0f)

        if (earVis < 0.5f || shVis < 0.5f) {
            return PostureMetrics(0f, 0f, false, "Show face and shoulder")
        }
        if (shLm.x() < 0f || shLm.x() > 1f || shLm.y() < 0f || shLm.y() > 1f ||
            earLm.x() < 0f || earLm.x() > 1f || earLm.y() < 0f || earLm.y() > 1f) {
            return PostureMetrics(0f, 0f, false, "Move back — show head and shoulders")
        }

// NEW: reject if ear is below shoulder (impossible for upright sitting)
        if (earLm.y() > shLm.y()) {
            return PostureMetrics(0f, 0f, false, "Move back — full upper body in frame")
        }

        val dx = abs(earLm.x() - shLm.x())
        val dy = shLm.y() - earLm.y()   // image y grows downward, so this is positive when ear is above shoulder
        val cvaRad = atan2(dy, dx)
        val cva = Math.toDegrees(cvaRad.toDouble()).toFloat()

        // Hip check kept optional — most desk webcam setups won't see hips
        val hipL = landmarks[LEFT_HIP]
        val hipR = landmarks[RIGHT_HIP]
        val hipVisible = hipL.visibility().orElse(0f) >= 0.5f &&
                hipR.visibility().orElse(0f) >= 0.5f
        val shoulderMidX = (landmarks[LEFT_SHOULDER].x() + landmarks[RIGHT_SHOULDER].x()) / 2f
        val hipMidX = (hipL.x() + hipR.x()) / 2f
        val shoulderAlignment = if (hipVisible) abs(shoulderMidX - hipMidX) else 0f

        val cvaOk = cva >= CVA_MIN
        val alignmentOk = !hipVisible || shoulderAlignment <= SHOULDER_HIP_MAX
        val isCorrect = cvaOk && alignmentOk

        val reason = when {
            isCorrect -> "Good posture — hold still"
            !cvaOk -> "Lift head — reduce forward tilt"
            !alignmentOk -> "Align shoulders over hips"
            else -> "Adjust posture"
        }

        android.util.Log.d(
            "Posture",
            "side=${if (useLeft) "L" else "R"} ear=(${earLm.x()},${earLm.y()}) shoulder=(${shLm.x()},${shLm.y()}) dx=$dx dy=$dy cva=$cva"
        )

        return PostureMetrics(cva, shoulderAlignment, isCorrect, reason)
    }
}