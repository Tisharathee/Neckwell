package com.humblecoders.neckwell

import android.util.Log
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.atan2

data class PostureMetrics(
    val cva: Float,              // Craniovertebral Angle (Pitch / forward head tilt) in degrees relative to horizontal
    val lateralTilt: Float,      // Lateral neck/head tilt (Roll / side tilt) in degrees
    val shoulderAlignment: Float,// Shoulder-hip alignment
    val isCorrect: Boolean,      // True only if all posture axes are within acceptable thresholds
    val reason: String,
    val posture: String = if (isCorrect) "Good" else "Poor",
    val tragusXNorm: Float = 0f,
    val tragusYNorm: Float = 0f,
    val c7XNorm: Float = 0f,
    val c7YNorm: Float = 0f,
    val tragusXPx: Float = 0f,
    val tragusYPx: Float = 0f,
    val c7XPx: Float = 0f,
    val c7YPx: Float = 0f,
    val imageWidth: Int = 1000,
    val imageHeight: Int = 1000,
    val cvaPixelSpace: Float = cva,
    val cvaNormalizedSpace: Float = cva,
    val angleWithVertical: Float = 90f - cva,
    val sideLabel: String = "Profile"
)

object PostureAnalyzer {
    private const val TAG = "PostureAnalyzer"
    const val DEV_MODE = true

    // MediaPipe Pose Landmark Map Indices:
    // 0: NOSE
    // 7: LEFT_EAR (Tragus), 8: RIGHT_EAR (Tragus)
    // 11: LEFT_SHOULDER, 12: RIGHT_SHOULDER
    // 23: LEFT_HIP, 24: RIGHT_HIP
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

    fun analyze(
        result: PoseLandmarkerResult,
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ): PostureMetrics? {
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

        // C7 / Neck Base Estimation:
        // In sagittal view, spinal midline is represented by the shoulder midpoint (landmarks 11 & 12).
        // If one shoulder is heavily occluded, fall back to the visible shoulder.
        val c7X = if (leftShVis >= 0.25f && rightShVis >= 0.25f) shoulderMidX else shLm.x()
        val c7Y = if (leftShVis >= 0.25f && rightShVis >= 0.25f) (leftShLm.y() + rightShLm.y()) / 2f else shLm.y()

        return computeMetrics(
            earX = earLm.x(),
            earY = earLm.y(),
            earVis = earVis,
            shX = c7X,
            shY = c7Y,
            shVis = maxOf(shVis, (leftShVis + rightShVis) / 2f),
            sideLabel = if (useLeft) "Left" else "Right",
            otherEarX = otherEarLm.x(),
            otherEarY = otherEarLm.y(),
            otherEarVis = otherEarVis,
            shoulderAlignment = shoulderAlignment,
            hipVisible = hipVisible,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    /**
     * Pure calculation function decoupled from MediaPipe objects for testability and verification.
     * Computes true physical pixel-space CVA relative to the horizontal line through C7.
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
        hipVisible: Boolean = false,
        imageWidth: Int = 0,
        imageHeight: Int = 0
    ): PostureMetrics {
        if (earVis < 0.3f || shVis < 0.3f) {
            val metrics = PostureMetrics(0f, 0f, 0f, false, "Turn sideways — show ear & shoulder")
            Log.d(TAG, "Detection rejected: earVis=$earVis, shVis=$shVis")
            return metrics
        }

        // Frame dimensions (default to square 1000x1000 if not specified to preserve unit tests)
        val w = if (imageWidth > 0) imageWidth else 1000
        val h = if (imageHeight > 0) imageHeight else 1000

        // True pixel coordinates:
        val tragusXPx = earX * w
        val tragusYPx = earY * h
        val c7XPx = shX * w
        val c7YPx = shY * h

        // Image coordinates: Y grows downward (top=0, bottom=h).
        // Upright posture: ear/tragus is HIGHER than C7/shoulder, so tragusY < c7Y.
        // Therefore dyPx = c7YPx - tragusYPx must be strictly POSITIVE.
        val dyPx = c7YPx - tragusYPx
        val dxPx = abs(tragusXPx - c7XPx)

        val dyNorm = shY - earY
        val dxNorm = abs(earX - shX)

        // Check if ear is below or level with shoulder (severe slouch / bent over)
        if (dyPx <= 1f || dyNorm <= 0.01f) {
            val reason = "Sit upright — head is tilted too far down"
            Log.d(TAG, "[$sideLabel] Poor posture (Head below shoulder): dyPx=$dyPx, dxPx=$dxPx")
            return PostureMetrics(
                cva = 0f,
                lateralTilt = 0f,
                shoulderAlignment = shoulderAlignment,
                isCorrect = false,
                reason = reason,
                tragusXNorm = earX,
                tragusYNorm = earY,
                c7XNorm = shX,
                c7YNorm = shY,
                tragusXPx = tragusXPx,
                tragusYPx = tragusYPx,
                c7XPx = c7XPx,
                c7YPx = c7YPx,
                imageWidth = w,
                imageHeight = h,
                cvaPixelSpace = 0f,
                cvaNormalizedSpace = 0f,
                angleWithVertical = 90f,
                sideLabel = sideLabel
            )
        }

        // 1. True Craniovertebral Angle (CVA) in degrees:
        // Angle formed by the vector from C7 to Tragus with the HORIZONTAL line passing through C7.
        // In the right triangle: Horizontal adjacent = dxPx, Vertical opposite = dyPx.
        // tan(CVA) = dyPx / dxPx -> CVA = atan2(dyPx, dxPx)
        val cvaPixelRad = atan2(dyPx.toDouble(), dxPx.toDouble())
        val cvaPixel = Math.toDegrees(cvaPixelRad).toFloat()

        // Normalized space angle (subject to aspect ratio distortion when w != h):
        val cvaNormRad = atan2(dyNorm.toDouble(), dxNorm.toDouble())
        val cvaNorm = Math.toDegrees(cvaNormRad).toFloat()

        // Angle relative to vertical axis (complementary angle):
        val angleWithVertical = (90.0 - cvaPixel.toDouble()).coerceIn(0.0, 90.0).toFloat()

        // 2. Lateral Roll / Side-Tilt: computed if both ears are visible
        val lateralTilt = if (otherEarX != null && otherEarY != null && otherEarVis >= 0.35f) {
            val otherXPx = otherEarX * w
            val otherYPx = otherEarY * h
            val earDxPx = abs(otherXPx - tragusXPx)
            val earDyPx = otherYPx - tragusYPx
            if (earDxPx > 1f) Math.toDegrees(atan2(earDyPx.toDouble(), earDxPx.toDouble())).toFloat() else 0f
        } else {
            0f // Side profile with single ear visible: relies on ESP32 IMU for roll
        }

        // Gate Checks: ONLY accept if within verified good posture thresholds
        val cvaOk = isGoodPosture(cvaPixel)
        val dxOk = dxNorm <= MAX_EAR_SHOULDER_DX
        val rollOk = abs(lateralTilt) <= MAX_LATERAL_TILT_DEG
        val alignmentOk = !hipVisible || shoulderAlignment <= SHOULDER_HIP_MAX
        val isCorrect = cvaOk && dxOk && rollOk && alignmentOk
        val posture = if (isCorrect) "Good" else "Poor"

        val reason = when {
            isCorrect -> "Good posture — hold still"
            !cvaOk || !dxOk -> "Sit straight (CVA: ${"%.1f".format(cvaPixel)}°, dx: ${"%.2f".format(dxNorm)})"
            !rollOk -> "Level your head (tilt: ${lateralTilt.toInt()}°)"
            !alignmentOk -> "Align shoulders over hips"
            else -> "Adjust posture"
        }

        if (DEV_MODE) {
            Log.d(
                TAG,
                "[$sideLabel] LANDMARK_TELEMETRY: " +
                    "Tragus(norm)=(${"%.3f".format(earX)}, ${"%.3f".format(earY)}), " +
                    "C7(norm)=(${"%.3f".format(shX)}, ${"%.3f".format(shY)}) | " +
                    "Tragus(px)=(${"%.1f".format(tragusXPx)}, ${"%.1f".format(tragusYPx)}), " +
                    "C7(px)=(${"%.1f".format(c7XPx)}, ${"%.1f".format(c7YPx)}) | " +
                    "dxPx=${"%.1f".format(dxPx)}, dyPx=${"%.1f".format(dyPx)} | " +
                    "Frame=${w}x${h} (aspect=${"%.2f".format(w.toFloat() / h)}) | " +
                    "CVA(pixel)=${"%.1f".format(cvaPixel)}°, CVA(norm)=${"%.1f".format(cvaNorm)}°, " +
                    "VertAngle=${"%.1f".format(angleWithVertical)}° | Posture=$posture"
            )
        }

        return PostureMetrics(
            cva = cvaPixel,
            lateralTilt = lateralTilt,
            shoulderAlignment = shoulderAlignment,
            isCorrect = isCorrect,
            reason = reason,
            posture = posture,
            tragusXNorm = earX,
            tragusYNorm = earY,
            c7XNorm = shX,
            c7YNorm = shY,
            tragusXPx = tragusXPx,
            tragusYPx = tragusYPx,
            c7XPx = c7XPx,
            c7YPx = c7YPx,
            imageWidth = w,
            imageHeight = h,
            cvaPixelSpace = cvaPixel,
            cvaNormalizedSpace = cvaNorm,
            angleWithVertical = angleWithVertical,
            sideLabel = sideLabel
        )
    }
}