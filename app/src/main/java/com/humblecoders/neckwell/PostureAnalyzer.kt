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
    val sideLabel: String = "Profile",
    val shoulderXNorm: Float = c7XNorm,
    val shoulderYNorm: Float = c7YNorm,
    val shoulderXPx: Float = c7XPx,
    val shoulderYPx: Float = c7YPx
)

object PostureAnalyzer {
    private const val TAG = "PostureAnalyzer"
    const val DEV_MODE = true

    // MediaPipe Pose Landmark Map Indices:
    // 0: NOSE
    // 7: LEFT_EAR (Tragus), 8: RIGHT_EAR (Tragus)
    // 11: LEFT_SHOULDER, 12: RIGHT_SHOULDER
    // 23: LEFT_HIP, 24: RIGHT_HIP
    private const val NOSE = 0
    private const val LEFT_EAR = 7
    private const val RIGHT_EAR = 8
    private const val LEFT_SHOULDER = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_HIP = 23
    private const val RIGHT_HIP = 24

    // Target clinical range for Craniovertebral Angle (CVA):
    // Good posture: CVA is within 48°–50° (inclusive)
    // Poor posture: CVA < 48° (forward head tilt) or CVA > 50° (hyperextension / head tilted back)
    const val CVA_MIN = 48f
    const val CVA_MAX = 50f

    fun isGoodPosture(cva: Float): Boolean = cva in CVA_MIN..CVA_MAX
    fun isPoorPosture(cva: Float): Boolean = !isGoodPosture(cva)
    fun classifyPosture(cva: Float): String = if (isGoodPosture(cva)) "Good" else "Poor"

    // Derived C7 (Neck Base) Landmark Constants:
    // In human anatomy, C7 (vertebra prominens) sits at the posterior base of the cervical spine,
    // noticeably above the acromioclavicular line and backward along the neck toward the back.
    // MediaPipe Pose landmarks 11 & 12 locate the outer acromion / glenohumeral joints.
    // Correct estimation:
    // 1. Take the midpoint between left & right shoulder landmarks to locate the coronal spinal axis (not sideways).
    // 2. Elevate upward toward the head by 25%–35% of ear-to-shoulder distance (default 35%).
    // 3. Shift inward/back along the neck (posteriorly toward dorsal spine, away from face) by ~25% of neck length.
    const val DEFAULT_C7_NECK_UPWARD_RATIO = 0.35f   // 35% of ear-to-shoulder vertical distance
    const val DEFAULT_C7_POSTERIOR_RATIO = 0.25f     // 25% of neck height backward toward dorsal neck
    const val DEFAULT_C7_TORSO_UPWARD_RATIO = 0.14f  // 14% of torso height (within 10–15% range)
    const val DEFAULT_C7_VERTICAL_OFFSET_RATIO = 0.35f // Legacy compatibility alias

    var c7TorsoUpwardRatio: Float = DEFAULT_C7_TORSO_UPWARD_RATIO
    var c7NeckUpwardRatio: Float = DEFAULT_C7_NECK_UPWARD_RATIO
    var c7PosteriorRatio: Float = DEFAULT_C7_POSTERIOR_RATIO
    var c7VerticalOffsetRatio: Float = DEFAULT_C7_NECK_UPWARD_RATIO

    /**
     * Primary anatomical C7 estimator:
     * Takes the shoulder midpoint and scales proportionally by ear-to-shoulder distance:
     * moves upward toward the head (35% of ear-to-shoulder distance)
     * and backward along the neck toward the dorsal spine (25% of ear-to-shoulder distance away from the face).
     */
    fun deriveC7Landmark(
        shoulderMidX: Float,
        shoulderMidY: Float,
        earX: Float,
        earY: Float,
        isFacingLeft: Boolean,
        hipMidY: Float? = null,
        torsoUpwardRatio: Float = c7TorsoUpwardRatio,
        neckUpwardRatio: Float = c7NeckUpwardRatio,
        posteriorRatio: Float = c7PosteriorRatio
    ): Pair<Float, Float> {
        val earToShoulderDistance = maxOf(0.01f, shoulderMidY - earY)
        val dyUp = neckUpwardRatio * earToShoulderDistance
        val c7Y = shoulderMidY - dyUp

        // If facing Left (nose < ear), gaze is left (-X), so back of neck is right (+X).
        // If facing Right (nose > ear), gaze is right (+X), so back of neck is left (-X).
        val posteriorDirection = if (isFacingLeft) 1f else -1f
        val dxBack = posteriorRatio * earToShoulderDistance
        val c7X = shoulderMidX + (posteriorDirection * dxBack)

        return Pair(c7X, c7Y)
    }

    /**
     * Overloaded helper for backwards compatibility with tests / callers without hip or facing data.
     */
    fun deriveC7Landmark(
        shoulderX: Float,
        shoulderY: Float,
        earX: Float,
        earY: Float,
        verticalOffsetRatio: Float = c7NeckUpwardRatio
    ): Pair<Float, Float> {
        val isFacingLeft = earX <= shoulderX
        return deriveC7Landmark(
            shoulderMidX = shoulderX,
            shoulderMidY = shoulderY,
            earX = earX,
            earY = earY,
            isFacingLeft = isFacingLeft,
            hipMidY = null,
            neckUpwardRatio = verticalOffsetRatio
        )
    }

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
        val noseLm = landmarks.getOrNull(NOSE)

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
        val hipMidX = (hipL.x() + hipR.x()) / 2f
        val hipMidY = if (hipVisible) (hipL.y() + hipR.y()) / 2f else null

        // 1. Shoulder Midpoint:
        // Do NOT use LEFT_SHOULDER or RIGHT_SHOULDER directly, as they sit out on the lateral shoulder joint.
        // Always compute the midpoint between the two shoulders as the spinal center baseline.
        val shoulderMidX = (leftShLm.x() + rightShLm.x()) / 2f
        val shoulderMidY = (leftShLm.y() + rightShLm.y()) / 2f
        val shoulderAlignment = if (hipVisible) abs(shoulderMidX - hipMidX) else 0f

        // 2. Facing Direction (Anterior vs Posterior):
        // Nose is anterior to ear.
        val noseVis = noseLm?.visibility()?.orElse(0f) ?: 0f
        val isFacingLeft = if (noseLm != null && noseVis >= 0.25f) {
            noseLm.x() < earLm.x()
        } else {
            useLeft
        }

        // 3. Anatomically Derived C7 (Neck Base) Landmark:
        // Adjust upward toward head (10–15% torso or ~22% neck) and backward along dorsal neck (~12% neck).
        val (c7X, c7Y) = deriveC7Landmark(
            shoulderMidX = shoulderMidX,
            shoulderMidY = shoulderMidY,
            earX = earLm.x(),
            earY = earLm.y(),
            isFacingLeft = isFacingLeft,
            hipMidY = hipMidY
        )

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
            imageHeight = imageHeight,
            rawShoulderX = shoulderMidX,
            rawShoulderY = shoulderMidY
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
        imageHeight: Int = 0,
        rawShoulderX: Float = shX,
        rawShoulderY: Float = shY
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
        val shoulderXPx = rawShoulderX * w
        val shoulderYPx = rawShoulderY * h

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
                sideLabel = sideLabel,
                shoulderXNorm = rawShoulderX,
                shoulderYNorm = rawShoulderY,
                shoulderXPx = shoulderXPx,
                shoulderYPx = shoulderYPx
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
            !rollOk -> "Level your head (tilt: ${lateralTilt.toInt()}°)"
            cvaPixel < CVA_MIN -> "Sit straight — head forward (CVA: ${"%.1f".format(cvaPixel)}° < 48°)"
            cvaPixel > CVA_MAX -> "Sit natural — head tilted back (CVA: ${"%.1f".format(cvaPixel)}° > 50°)"
            !dxOk -> "Align head with shoulders (dx: ${"%.2f".format(dxNorm)})"
            !alignmentOk -> "Align shoulders over hips"
            else -> "Adjust posture"
        }

        if (DEV_MODE) {
            Log.d(
                TAG,
                "[$sideLabel] LANDMARK_TELEMETRY: " +
                    "Tragus(norm)=(${"%.3f".format(earX)}, ${"%.3f".format(earY)}), " +
                    "C7_NeckBase(norm)=(${"%.3f".format(shX)}, ${"%.3f".format(shY)}), " +
                    "Shoulder(norm)=(${"%.3f".format(rawShoulderX)}, ${"%.3f".format(rawShoulderY)}) | " +
                    "Tragus(px)=(${"%.1f".format(tragusXPx)}, ${"%.1f".format(tragusYPx)}), " +
                    "C7_NeckBase(px)=(${"%.1f".format(c7XPx)}, ${"%.1f".format(c7YPx)}), " +
                    "Shoulder(px)=(${"%.1f".format(shoulderXPx)}, ${"%.1f".format(shoulderYPx)}) | " +
                    "dxPx=${"%.1f".format(dxPx)}, dyPx=${"%.1f".format(dyPx)} | " +
                    "Frame=${w}x${h} (aspect=${"%.2f".format(w.toFloat() / h)}) | " +
                    "CVA(pixel)=${"%.1f".format(cvaPixel)}°, CVA(norm)=${"%.1f".format(cvaNorm)}°, " +
                    "VertAngle=${"%.1f".format(angleWithVertical)}° | TargetBand=[${CVA_MIN}..${CVA_MAX}] | Posture=$posture (cvaOk=$cvaOk, isCorrect=$isCorrect, reason='$reason')"
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
            sideLabel = sideLabel,
            shoulderXNorm = rawShoulderX,
            shoulderYNorm = rawShoulderY,
            shoulderXPx = shoulderXPx,
            shoulderYPx = shoulderYPx
        )
    }
}