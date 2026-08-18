package com.humblecoders.neckwell

import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.atan2
import kotlin.math.abs

data class PostureMetrics(
    val cva: Float,              // Craniovertebral Angle (Pitch / forward head tilt) in degrees
    val lateralTilt: Float,      // Lateral neck/head tilt (Roll / side tilt) in degrees (mocked to 0 for side-profile)
    val shoulderAlignment: Float,// Shoulder-hip alignment
    val isCorrect: Boolean,      // True only if all posture axes are within acceptable thresholds
    val reason: String
)

object PostureAnalyzer {
    private const val LEFT_EAR = 7
    private const val RIGHT_EAR = 8
    private const val LEFT_SHOULDER = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_HIP = 23
    private const val RIGHT_HIP = 24

    // Upright sitting posture: CVA >= 35° (Highly relaxed for varying camera heights)
    private const val CVA_MIN = 35f
    private const val SHOULDER_HIP_MAX = 0.08f
    
    // In normalized coords (0..1), ear should not be displaced far forward horizontally from shoulder.
    // Relaxed to 0.50f (essentially disabled) to allow for users sitting close to the camera.
    private const val MAX_EAR_SHOULDER_DX = 0.50f
    
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

        // Relaxed visibility threshold slightly to 0.3f
        if (earVis < 0.3f || shVis < 0.3f) {
            return PostureMetrics(0f, 0f, 0f, false, "Turn sideways — show ear & shoulder")
        }
        
        // Removed the strict bounds check (0f..1f) because landmarks can occasionally fall slightly outside the frame 
        // (e.g. -0.05 or 1.05) and still be perfectly valid for angle calculation.

        // 1. Forward/Backward Pitch: Craniovertebral Angle (CVA)
        val dx = abs(earLm.x() - shLm.x())
        // Absolute value used to ensure dy is positive, fixing negative CVA issues if camera Y-axis is inverted
        val dy = abs(shLm.y() - earLm.y())   
        val cvaRad = atan2(dy, dx)
        val cva = Math.toDegrees(cvaRad.toDouble()).toFloat()

        // 2. Lateral Roll / Side-Tilt is NOT calculable from a 2D side-profile view!
        // When the user turns sideways, the X-distance between the ears is compressed to near 0, 
        // making the atan2 calculation highly volatile and inaccurate. We must rely on the ESP32 IMU for roll.
        val lateralTilt = 0f 

        // 3. Torso Alignment (Hip check kept optional — most desk webcam setups won't see hips)
        val hipL = landmarks[LEFT_HIP]
        val hipR = landmarks[RIGHT_HIP]
        val hipVisible = hipL.visibility().orElse(0f) >= 0.4f &&
                hipR.visibility().orElse(0f) >= 0.4f
        val shoulderMidX = (leftShLm.x() + rightShLm.x()) / 2f
        val hipMidX = (hipL.x() + hipR.x()) / 2f
        val shoulderAlignment = if (hipVisible) abs(shoulderMidX - hipMidX) else 0f

        // Gate Checks: ONLY accept if within good posture thresholds
        val cvaOk = cva >= CVA_MIN
        val dxOk = dx <= MAX_EAR_SHOULDER_DX
        val alignmentOk = !hipVisible || shoulderAlignment <= SHOULDER_HIP_MAX
        val isCorrect = cvaOk && dxOk && alignmentOk

        val reason = when {
            isCorrect -> "Good posture — hold still"
            !cvaOk || !dxOk -> "Sit straight (CVA: ${cva.toInt()}°, dx: ${"%.2f".format(dx)})"
            !alignmentOk -> "Align shoulders over hips"
            else -> "Adjust posture"
        }

        android.util.Log.d(
            "Posture",
            "side=${if (useLeft) "L" else "R"} cva=$cva dx=$dx isCorrect=$isCorrect"
        )

        return PostureMetrics(cva, lateralTilt, shoulderAlignment, isCorrect, reason)
    }
}