package com.humblecoders.neckwell

import android.content.Context
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
    val shoulderYPx: Float = c7YPx,
    val rawEarXNorm: Float = tragusXNorm,
    val rawEarYNorm: Float = tragusYNorm,
    val rawEarXPx: Float = tragusXPx,
    val rawEarYPx: Float = tragusYPx,
    val jitterTragusPx: Float = 0f,
    val jitterC7Px: Float = 0f,
    val cvaStdDev: Float = 0f,
    val rawInstantaneousCva: Float = cva,
    val isSmoothed: Boolean = false
)

data class ClinicalReference(
    val tragusXPx: Float,
    val tragusYPx: Float,
    val c7XPx: Float,
    val c7YPx: Float,
    val expectedCva: Float? = null,
    val label: String = "Clinical Reference"
)

data class LandmarkErrorMetrics(
    val tragusPixelError: Float,
    val c7PixelError: Float,
    val cvaDelta: Float,
    val isTragusAccurate: Boolean = tragusPixelError <= 8.0f,
    val isC7Accurate: Boolean = c7PixelError <= 8.0f
)

data class CalibrationDataPoint(
    val rawEarX: Float,
    val rawEarY: Float,
    val rawShoulderX: Float,
    val rawShoulderY: Float,
    val isFacingLeft: Boolean,
    val clinicalTragusX: Float,
    val clinicalTragusY: Float,
    val clinicalC7X: Float,
    val clinicalC7Y: Float,
    val shoulderWidth: Float = 0f
)

object PostureAnalyzer {
    private const val TAG = "PostureAnalyzer"
    const val DEV_MODE = true

    // MediaPipe Pose Landmark Map Indices:
    // 0: NOSE
    // 7: LEFT_EAR (Raw Auricle), 8: RIGHT_EAR (Raw Auricle)
    // 9: MOUTH_LEFT, 10: MOUTH_RIGHT
    // 11: LEFT_SHOULDER, 12: RIGHT_SHOULDER
    // 23: LEFT_HIP, 24: RIGHT_HIP
    private const val NOSE = 0
    private const val LEFT_EAR = 7
    private const val RIGHT_EAR = 8
    private const val MOUTH_LEFT = 9
    private const val MOUTH_RIGHT = 10
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

    // ---------------------------------------------------------------------------------------------
    // 1. Anatomical Tragus (Ear) Landmark Constants & Derivation:
    // MediaPipe Pose landmarks 7 & 8 identify the outer auricle / upper helix of the ear.
    // The anatomical Tragus is the small cartilaginous flap immediately anterior to the external
    // acoustic meatus (ear canal opening), located forward toward the jaw hinge and slightly inferior.
    // ---------------------------------------------------------------------------------------------
    const val DEFAULT_TRAGUS_ANTERIOR_RATIO = 0.08f  // 8% of neck height forward toward jaw hinge
    const val DEFAULT_TRAGUS_INFERIOR_RATIO = 0.04f  // 4% of neck height downward toward jaw hinge

    var tragusAnteriorRatio: Float = DEFAULT_TRAGUS_ANTERIOR_RATIO
    var tragusInferiorRatio: Float = DEFAULT_TRAGUS_INFERIOR_RATIO

    /**
     * Anatomical Tragus Estimator:
     * Derives the true tragus (ear canal opening flap) from the raw MediaPipe ear pinna landmark.
     * Shifts anteriorly toward the face/jaw along the coronal plane and slightly downward toward the jaw hinge.
     */
    fun deriveTragusLandmark(
        earX: Float,
        earY: Float,
        isFacingLeft: Boolean,
        neckHeight: Float,
        mouthX: Float? = null,
        mouthY: Float? = null,
        anteriorRatio: Float = tragusAnteriorRatio,
        inferiorRatio: Float = tragusInferiorRatio
    ): Pair<Float, Float> {
        val anteriorDirection = if (isFacingLeft) -1f else 1f
        val dx = anteriorRatio * neckHeight
        val dy = inferiorRatio * neckHeight

        val tragusX = earX + (anteriorDirection * dx)
        val tragusY = earY + dy

        return Pair(tragusX, tragusY)
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Calibrated C7 (Neck Base) Regression Model Constants & Derivation:
    // C7 (vertebra prominens) sits at the posterior base of the cervical spine.
    // MediaPipe Pose landmarks 11 & 12 locate the outer acromion joints.
    // Rather than guessing a fixed ratio, C7 is modeled via regression over neck length
    // (ear-to-shoulder vertical distance) and shoulder width (acromial span).
    // ---------------------------------------------------------------------------------------------
    const val DEFAULT_C7_KY_NECK = 0.35f          // Vertical neck coefficient (elevates upward toward head)
    const val DEFAULT_C7_KY_SHOULDER = 0.05f      // Vertical shoulder width coefficient
    const val DEFAULT_C7_KX_NECK = 0.25f          // Horizontal dorsal neck coefficient (shifts back toward spine)
    const val DEFAULT_C7_KX_SHOULDER = 0.04f      // Horizontal dorsal shoulder width coefficient

    var c7KyNeck: Float = DEFAULT_C7_KY_NECK
    var c7KyShoulder: Float = DEFAULT_C7_KY_SHOULDER
    var c7KxNeck: Float = DEFAULT_C7_KX_NECK
    var c7KxShoulder: Float = DEFAULT_C7_KX_SHOULDER

    // Compatibility aliases
    const val DEFAULT_C7_NECK_UPWARD_RATIO = DEFAULT_C7_KY_NECK
    const val DEFAULT_C7_POSTERIOR_RATIO = DEFAULT_C7_KX_NECK
    const val DEFAULT_C7_TORSO_UPWARD_RATIO = 0.14f
    const val DEFAULT_C7_VERTICAL_OFFSET_RATIO = DEFAULT_C7_KY_NECK

    var c7TorsoUpwardRatio: Float = DEFAULT_C7_TORSO_UPWARD_RATIO
    var c7NeckUpwardRatio: Float
        get() = c7KyNeck
        set(value) { c7KyNeck = value }
    var c7PosteriorRatio: Float
        get() = c7KxNeck
        set(value) { c7KxNeck = value }
    var c7VerticalOffsetRatio: Float
        get() = c7KyNeck
        set(value) { c7KyNeck = value }

    // ---------------------------------------------------------------------------------------------
    // 3. Temporal Landmark Smoothing Filter & Jitter Metrics:
    // Moving average filter over raw landmark coordinates across a sliding window of frames (default N=5).
    // Eliminates micro-jitter and noise-driven angle spikes while maintaining true anatomical responsiveness.
    // ---------------------------------------------------------------------------------------------
    const val DEFAULT_SMOOTHING_WINDOW = 5
    var smoothingWindowSize: Int = DEFAULT_SMOOTHING_WINDOW
    var isSmoothingEnabled: Boolean = true

    data class RawLandmarkFrame(
        val earX: Float,
        val earY: Float,
        val leftShX: Float,
        val leftShY: Float,
        val rightShX: Float,
        val rightShY: Float,
        val noseX: Float?,
        val noseY: Float?,
        val otherEarX: Float?,
        val otherEarY: Float?,
        val mouthX: Float?,
        val mouthY: Float?,
        val sideLabel: String
    )

    data class SmoothedLandmarkFrame(
        val earX: Float,
        val earY: Float,
        val leftShX: Float,
        val leftShY: Float,
        val rightShX: Float,
        val rightShY: Float,
        val noseX: Float?,
        val noseY: Float?,
        val otherEarX: Float?,
        val otherEarY: Float?,
        val mouthX: Float?,
        val mouthY: Float?,
        val windowCount: Int
    )

    class LandmarkSmoother(var windowSize: Int = DEFAULT_SMOOTHING_WINDOW) {
        private val history = ArrayDeque<RawLandmarkFrame>()
        private val cvaHistory = ArrayDeque<Float>()
        private var lastTragusXPx: Float? = null
        private var lastTragusYPx: Float? = null
        private var lastC7XPx: Float? = null
        private var lastC7YPx: Float? = null
        private var lastSideLabel: String? = null

        @Synchronized
        fun reset() {
            history.clear()
            cvaHistory.clear()
            lastTragusXPx = null
            lastTragusYPx = null
            lastC7XPx = null
            lastC7YPx = null
            lastSideLabel = null
        }

        @Synchronized
        fun smooth(raw: RawLandmarkFrame): SmoothedLandmarkFrame {
            if (lastSideLabel != null && lastSideLabel != raw.sideLabel) {
                reset()
            }
            lastSideLabel = raw.sideLabel

            history.addLast(raw)
            val effectiveWindow = maxOf(1, windowSize)
            while (history.size > effectiveWindow) {
                history.removeFirst()
            }

            val n = history.size
            var sumEarX = 0f; var sumEarY = 0f
            var sumLeftShX = 0f; var sumLeftShY = 0f
            var sumRightShX = 0f; var sumRightShY = 0f
            var sumNoseX = 0f; var sumNoseY = 0f; var noseCount = 0
            var sumOtherEarX = 0f; var sumOtherEarY = 0f; var otherEarCount = 0
            var sumMouthX = 0f; var sumMouthY = 0f; var mouthCount = 0

            for (f in history) {
                sumEarX += f.earX; sumEarY += f.earY
                sumLeftShX += f.leftShX; sumLeftShY += f.leftShY
                sumRightShX += f.rightShX; sumRightShY += f.rightShY
                if (f.noseX != null && f.noseY != null) {
                    sumNoseX += f.noseX; sumNoseY += f.noseY; noseCount++
                }
                if (f.otherEarX != null && f.otherEarY != null) {
                    sumOtherEarX += f.otherEarX; sumOtherEarY += f.otherEarY; otherEarCount++
                }
                if (f.mouthX != null && f.mouthY != null) {
                    sumMouthX += f.mouthX; sumMouthY += f.mouthY; mouthCount++
                }
            }

            return SmoothedLandmarkFrame(
                earX = sumEarX / n,
                earY = sumEarY / n,
                leftShX = sumLeftShX / n,
                leftShY = sumLeftShY / n,
                rightShX = sumRightShX / n,
                rightShY = sumRightShY / n,
                noseX = if (noseCount > 0) sumNoseX / noseCount else raw.noseX,
                noseY = if (noseCount > 0) sumNoseY / noseCount else raw.noseY,
                otherEarX = if (otherEarCount > 0) sumOtherEarX / otherEarCount else raw.otherEarX,
                otherEarY = if (otherEarCount > 0) sumOtherEarY / otherEarCount else raw.otherEarY,
                mouthX = if (mouthCount > 0) sumMouthX / mouthCount else raw.mouthX,
                mouthY = if (mouthCount > 0) sumMouthY / mouthCount else raw.mouthY,
                windowCount = n
            )
        }

        @Synchronized
        fun updateLandmarkJitterAndCva(
            tragusXPx: Float,
            tragusYPx: Float,
            c7XPx: Float,
            c7YPx: Float,
            cva: Float
        ): Triple<Float, Float, Float> {
            val jTragus = if (lastTragusXPx != null && lastTragusYPx != null) {
                val dx = tragusXPx - lastTragusXPx!!
                val dy = tragusYPx - lastTragusYPx!!
                kotlin.math.sqrt(dx * dx + dy * dy)
            } else 0f

            val jC7 = if (lastC7XPx != null && lastC7YPx != null) {
                val dx = c7XPx - lastC7XPx!!
                val dy = c7YPx - lastC7YPx!!
                kotlin.math.sqrt(dx * dx + dy * dy)
            } else 0f

            lastTragusXPx = tragusXPx
            lastTragusYPx = tragusYPx
            lastC7XPx = c7XPx
            lastC7YPx = c7YPx

            if (cva > 0f) {
                cvaHistory.addLast(cva)
                while (cvaHistory.size > 15) {
                    cvaHistory.removeFirst()
                }
            }

            val stdDev = if (cvaHistory.size >= 2) {
                val mean = cvaHistory.average().toFloat()
                val variance = cvaHistory.map { (it - mean) * (it - mean) }.average()
                kotlin.math.sqrt(variance).toFloat()
            } else 0f

            return Triple(jTragus, jC7, stdDev)
        }
    }

    val smoother = LandmarkSmoother(DEFAULT_SMOOTHING_WINDOW)

    fun resetSmoother() {
        smoother.reset()
    }

    // ---------------------------------------------------------------------------------------------
    // Calibration Persistence & Tuning Helpers (for Goniometer on-device validation):
    // ---------------------------------------------------------------------------------------------
    private const val PREFS_NAME = "neckwell_calibration_prefs"
    private const val KEY_C7_KY_NECK = "c7_ky_neck"
    private const val KEY_C7_KX_NECK = "c7_kx_neck"
    private const val KEY_TRAGUS_ANTERIOR = "tragus_anterior_ratio"
    private const val KEY_SMOOTHING_WINDOW = "smoothing_window_size"
    private const val KEY_SMOOTHING_ENABLED = "smoothing_enabled"

    fun saveToPreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_C7_KY_NECK, c7KyNeck)
            .putFloat(KEY_C7_KX_NECK, c7KxNeck)
            .putFloat(KEY_TRAGUS_ANTERIOR, tragusAnteriorRatio)
            .putInt(KEY_SMOOTHING_WINDOW, smoothingWindowSize)
            .putBoolean(KEY_SMOOTHING_ENABLED, isSmoothingEnabled)
            .apply()
    }

    fun loadFromPreferences(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_C7_KY_NECK)) {
            c7KyNeck = prefs.getFloat(KEY_C7_KY_NECK, DEFAULT_C7_KY_NECK)
            c7KxNeck = prefs.getFloat(KEY_C7_KX_NECK, DEFAULT_C7_KX_NECK)
            tragusAnteriorRatio = prefs.getFloat(KEY_TRAGUS_ANTERIOR, DEFAULT_TRAGUS_ANTERIOR_RATIO)
            smoothingWindowSize = prefs.getInt(KEY_SMOOTHING_WINDOW, DEFAULT_SMOOTHING_WINDOW)
            isSmoothingEnabled = prefs.getBoolean(KEY_SMOOTHING_ENABLED, true)
            smoother.windowSize = smoothingWindowSize
        }
    }

    fun resetToDefaults(context: Context? = null) {
        c7KyNeck = DEFAULT_C7_KY_NECK
        c7KyShoulder = DEFAULT_C7_KY_SHOULDER
        c7KxNeck = DEFAULT_C7_KX_NECK
        c7KxShoulder = DEFAULT_C7_KX_SHOULDER
        tragusAnteriorRatio = DEFAULT_TRAGUS_ANTERIOR_RATIO
        tragusInferiorRatio = DEFAULT_TRAGUS_INFERIOR_RATIO
        smoothingWindowSize = DEFAULT_SMOOTHING_WINDOW
        isSmoothingEnabled = true
        smoother.windowSize = DEFAULT_SMOOTHING_WINDOW
        smoother.reset()
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
    }

    /**
     * Primary anatomical C7 estimator using calibrated regression coefficients:
     * Takes the shoulder midpoint and scales by neck length (ear-to-shoulder vertical distance)
     * and optional shoulder width.
     */
    fun deriveC7Landmark(
        shoulderMidX: Float,
        shoulderMidY: Float,
        earX: Float,
        earY: Float,
        isFacingLeft: Boolean,
        hipMidY: Float? = null,
        shoulderWidth: Float? = null,
        torsoUpwardRatio: Float = c7TorsoUpwardRatio,
        kyNeck: Float = c7KyNeck,
        kyShoulder: Float = c7KyShoulder,
        kxNeck: Float = c7KxNeck,
        kxShoulder: Float = c7KxShoulder
    ): Pair<Float, Float> {
        val earToShoulderDistance = maxOf(0.01f, shoulderMidY - earY)
        val sWidth = maxOf(0f, shoulderWidth ?: 0f)

        val dyUp = (kyNeck * earToShoulderDistance) + (kyShoulder * sWidth)
        val c7Y = shoulderMidY - dyUp

        // If facing Left (nose < ear), gaze is left (-X), so back of neck is right (+X).
        // If facing Right (nose > ear), gaze is right (+X), so back of neck is left (-X).
        val posteriorDirection = if (isFacingLeft) 1f else -1f
        val dxBack = (kxNeck * earToShoulderDistance) + (kxShoulder * sWidth)
        val c7X = shoulderMidX + (posteriorDirection * dxBack)

        return Pair(c7X, c7Y)
    }

    /**
     * Overloaded helper for backwards compatibility with tests / callers without shoulderWidth.
     */
    fun deriveC7Landmark(
        shoulderMidX: Float,
        shoulderMidY: Float,
        earX: Float,
        earY: Float,
        isFacingLeft: Boolean,
        hipMidY: Float?
    ): Pair<Float, Float> {
        return deriveC7Landmark(
            shoulderMidX = shoulderMidX,
            shoulderMidY = shoulderMidY,
            earX = earX,
            earY = earY,
            isFacingLeft = isFacingLeft,
            hipMidY = hipMidY,
            shoulderWidth = null
        )
    }

    /**
     * Overloaded helper for backwards compatibility with legacy tests.
     */
    fun deriveC7Landmark(
        shoulderX: Float,
        shoulderY: Float,
        earX: Float,
        earY: Float,
        verticalOffsetRatio: Float = c7KyNeck
    ): Pair<Float, Float> {
        val isFacingLeft = earX <= shoulderX
        return deriveC7Landmark(
            shoulderMidX = shoulderX,
            shoulderMidY = shoulderY,
            earX = earX,
            earY = earY,
            isFacingLeft = isFacingLeft,
            hipMidY = null,
            shoulderWidth = null,
            kyNeck = verticalOffsetRatio
        )
    }

    /**
     * Calibrates C7 and Tragus coefficients from a dataset of clinical reference points
     * using regression over neck height.
     */
    fun calibrateFromDataset(dataset: List<CalibrationDataPoint>) {
        if (dataset.isEmpty()) return
        var sumTragusAnterior = 0.0
        var sumTragusInferior = 0.0
        var sumC7Ky = 0.0
        var sumC7Kx = 0.0
        var count = 0

        for (pt in dataset) {
            val neckH = maxOf(0.01f, pt.rawShoulderY - pt.rawEarY)
            val anteriorDir = if (pt.isFacingLeft) -1f else 1f
            val posteriorDir = if (pt.isFacingLeft) 1f else -1f

            val tragusDx = (pt.clinicalTragusX - pt.rawEarX) * anteriorDir
            val tragusDy = pt.clinicalTragusY - pt.rawEarY
            sumTragusAnterior += (tragusDx / neckH).coerceIn(0.02f, 0.20f)
            sumTragusInferior += (tragusDy / neckH).coerceIn(0.01f, 0.12f)

            val c7Dy = pt.rawShoulderY - pt.clinicalC7Y
            val c7Dx = (pt.clinicalC7X - pt.rawShoulderX) * posteriorDir
            sumC7Ky += (c7Dy / neckH).coerceIn(0.20f, 0.45f)
            sumC7Kx += (c7Dx / neckH).coerceIn(0.15f, 0.35f)
            count++
        }

        if (count > 0) {
            tragusAnteriorRatio = (sumTragusAnterior / count).toFloat()
            tragusInferiorRatio = (sumTragusInferior / count).toFloat()
            c7KyNeck = (sumC7Ky / count).toFloat()
            c7KxNeck = (sumC7Kx / count).toFloat()
        }
    }

    /**
     * Calculates Euclidean pixel-distance error and CVA delta against a clinical reference.
     */
    fun computeLandmarkErrors(
        metrics: PostureMetrics,
        ref: ClinicalReference
    ): LandmarkErrorMetrics {
        val tragusDx = metrics.tragusXPx - ref.tragusXPx
        val tragusDy = metrics.tragusYPx - ref.tragusYPx
        val tragusErr = kotlin.math.sqrt(tragusDx * tragusDx + tragusDy * tragusDy)

        val c7Dx = metrics.c7XPx - ref.c7XPx
        val c7Dy = metrics.c7YPx - ref.c7YPx
        val c7Err = kotlin.math.sqrt(c7Dx * c7Dx + c7Dy * c7Dy)

        val cvaDelta = if (ref.expectedCva != null) {
            abs(metrics.cva - ref.expectedCva)
        } else {
            0f
        }

        return LandmarkErrorMetrics(
            tragusPixelError = tragusErr,
            c7PixelError = c7Err,
            cvaDelta = cvaDelta
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
        imageHeight: Int = 0,
        enableSmoothing: Boolean = isSmoothingEnabled
    ): PostureMetrics? {
        val landmarks = result.landmarks().firstOrNull() ?: run {
            smoother.reset()
            return null
        }
        if (landmarks.size < 25) {
            smoother.reset()
            return null
        }

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
        val sideLabel = if (useLeft) "Left" else "Right"

        val earLm = if (useLeft) leftEarLm else rightEarLm
        val shLm = if (useLeft) leftShLm else rightShLm
        val earVis = earLm.visibility().orElse(0f)
        val shVis = shLm.visibility().orElse(0f)

        if (earVis < 0.3f || shVis < 0.3f) {
            smoother.reset()
            return PostureMetrics(0f, 0f, 0f, false, "Turn sideways — show ear & shoulder", sideLabel = sideLabel)
        }

        val otherEarLm = if (useLeft) rightEarLm else leftEarLm
        val otherEarVis = otherEarLm.visibility().orElse(0f)

        // Torso Alignment (Hip check kept optional — most desk setups won't see hips)
        val hipL = landmarks[LEFT_HIP]
        val hipR = landmarks[RIGHT_HIP]
        val hipVisible = hipL.visibility().orElse(0f) >= 0.4f && hipR.visibility().orElse(0f) >= 0.4f
        val hipMidX = (hipL.x() + hipR.x()) / 2f
        val hipMidY = if (hipVisible) (hipL.y() + hipR.y()) / 2f else null

        val mouthLm = if (useLeft) landmarks.getOrNull(MOUTH_LEFT) else landmarks.getOrNull(MOUTH_RIGHT)

        val rawFrame = RawLandmarkFrame(
            earX = earLm.x(),
            earY = earLm.y(),
            leftShX = leftShLm.x(),
            leftShY = leftShLm.y(),
            rightShX = rightShLm.x(),
            rightShY = rightShLm.y(),
            noseX = noseLm?.x(),
            noseY = noseLm?.y(),
            otherEarX = otherEarLm.x(),
            otherEarY = otherEarLm.y(),
            mouthX = mouthLm?.x(),
            mouthY = mouthLm?.y(),
            sideLabel = sideLabel
        )

        val activeFrame = if (enableSmoothing) {
            smoother.windowSize = smoothingWindowSize
            smoother.smooth(rawFrame)
        } else {
            SmoothedLandmarkFrame(
                earX = rawFrame.earX,
                earY = rawFrame.earY,
                leftShX = rawFrame.leftShX,
                leftShY = rawFrame.leftShY,
                rightShX = rawFrame.rightShX,
                rightShY = rawFrame.rightShY,
                noseX = rawFrame.noseX,
                noseY = rawFrame.noseY,
                otherEarX = rawFrame.otherEarX,
                otherEarY = rawFrame.otherEarY,
                mouthX = rawFrame.mouthX,
                mouthY = rawFrame.mouthY,
                windowCount = 1
            )
        }

        // 1. Shoulder Midpoint & Span (from active landmarks):
        val shoulderMidX = (activeFrame.leftShX + activeFrame.rightShX) / 2f
        val shoulderMidY = (activeFrame.leftShY + activeFrame.rightShY) / 2f
        val shoulderWidth = abs(activeFrame.leftShX - activeFrame.rightShX)
        val shoulderAlignment = if (hipVisible) abs(shoulderMidX - hipMidX) else 0f

        // 2. Facing Direction (Anterior vs Posterior):
        val noseVis = noseLm?.visibility()?.orElse(0f) ?: 0f
        val isFacingLeft = if (activeFrame.noseX != null && noseVis >= 0.25f) {
            activeFrame.noseX < activeFrame.earX
        } else {
            useLeft
        }

        val neckHeight = maxOf(0.01f, shoulderMidY - activeFrame.earY)

        // 3. Anatomically Derived Tragus (Ear Canal Opening Flap):
        val (tragusX, tragusY) = deriveTragusLandmark(
            earX = activeFrame.earX,
            earY = activeFrame.earY,
            isFacingLeft = isFacingLeft,
            neckHeight = neckHeight,
            mouthX = activeFrame.mouthX,
            mouthY = activeFrame.mouthY
        )

        // 4. Anatomically Derived C7 (Neck Base) Landmark:
        val (c7X, c7Y) = deriveC7Landmark(
            shoulderMidX = shoulderMidX,
            shoulderMidY = shoulderMidY,
            earX = tragusX,
            earY = tragusY,
            isFacingLeft = isFacingLeft,
            hipMidY = hipMidY,
            shoulderWidth = shoulderWidth
        )

        // Frame dimensions:
        val w = if (imageWidth > 0) imageWidth else 1000
        val h = if (imageHeight > 0) imageHeight else 1000

        // Calculate instantaneous unsmoothed CVA:
        val rawShoulderMidX = (leftShLm.x() + rightShLm.x()) / 2f
        val rawShoulderMidY = (leftShLm.y() + rightShLm.y()) / 2f
        val rawNeckH = maxOf(0.01f, rawShoulderMidY - earLm.y())
        val rawFacingLeft = if (noseLm != null && noseVis >= 0.25f) noseLm.x() < earLm.x() else useLeft
        val (rawTX, rawTY) = deriveTragusLandmark(earLm.x(), earLm.y(), rawFacingLeft, rawNeckH, mouthLm?.x(), mouthLm?.y())
        val (rawC7X, rawC7Y) = deriveC7Landmark(rawShoulderMidX, rawShoulderMidY, rawTX, rawTY, rawFacingLeft, hipMidY, abs(leftShLm.x() - rightShLm.x()))
        val rawDyPx = (rawC7Y * h) - (rawTY * h)
        val rawDxPx = abs((rawTX * w) - (rawC7X * w))
        val rawInstantCva = if (rawDyPx > 1f) Math.toDegrees(atan2(rawDyPx.toDouble(), rawDxPx.toDouble())).toFloat() else 0f

        val tXPx = tragusX * w
        val tYPx = tragusY * h
        val cXPx = c7X * w
        val cYPx = c7Y * h
        val dyPx = cYPx - tYPx
        val dxPx = abs(tXPx - cXPx)
        val currentCva = if (dyPx > 1f) Math.toDegrees(atan2(dyPx.toDouble(), dxPx.toDouble())).toFloat() else 0f

        val (jitterTragus, jitterC7, cvaStdDev) = smoother.updateLandmarkJitterAndCva(
            tragusXPx = tXPx,
            tragusYPx = tYPx,
            c7XPx = cXPx,
            c7YPx = cYPx,
            cva = currentCva
        )

        return computeMetrics(
            earX = tragusX,
            earY = tragusY,
            earVis = earVis,
            shX = c7X,
            shY = c7Y,
            shVis = maxOf(shVis, (leftShVis + rightShVis) / 2f),
            sideLabel = sideLabel,
            otherEarX = activeFrame.otherEarX,
            otherEarY = activeFrame.otherEarY,
            otherEarVis = otherEarVis,
            shoulderAlignment = shoulderAlignment,
            hipVisible = hipVisible,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            rawShoulderX = shoulderMidX,
            rawShoulderY = shoulderMidY,
            rawEarX = activeFrame.earX,
            rawEarY = activeFrame.earY,
            jitterTragusPx = jitterTragus,
            jitterC7Px = jitterC7,
            cvaStdDev = cvaStdDev,
            rawInstantaneousCva = rawInstantCva,
            isSmoothed = enableSmoothing
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
        rawShoulderY: Float = shY,
        rawEarX: Float = earX,
        rawEarY: Float = earY,
        jitterTragusPx: Float = 0f,
        jitterC7Px: Float = 0f,
        cvaStdDev: Float = 0f,
        rawInstantaneousCva: Float? = null,
        isSmoothed: Boolean = false
    ): PostureMetrics {
        if (earVis < 0.3f || shVis < 0.3f) {
            val metrics = PostureMetrics(0f, 0f, 0f, false, "Turn sideways — show ear & shoulder", sideLabel = sideLabel)
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
                shoulderYPx = shoulderYPx,
                rawEarXNorm = rawEarX,
                rawEarYNorm = rawEarY,
                rawEarXPx = rawEarX * w,
                rawEarYPx = rawEarY * h,
                jitterTragusPx = jitterTragusPx,
                jitterC7Px = jitterC7Px,
                cvaStdDev = cvaStdDev,
                rawInstantaneousCva = rawInstantaneousCva ?: 0f,
                isSmoothed = isSmoothed
            )
        }

        // 1. True Craniovertebral Angle (CVA) in degrees:
        // Angle formed by the vector from C7 to Tragus with the HORIZONTAL line passing through C7.
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

        val rawInstCva = rawInstantaneousCva ?: cvaPixel

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
                    "CVA(pixel)=${"%.1f".format(cvaPixel)}° (rawInstant=${"%.1f".format(rawInstCva)}°, jitter=[T:${"%.1f".format(jitterTragusPx)}px, C7:${"%.1f".format(jitterC7Px)}px]), " +
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
            shoulderYPx = shoulderYPx,
            rawEarXNorm = rawEarX,
            rawEarYNorm = rawEarY,
            rawEarXPx = rawEarX * w,
            rawEarYPx = rawEarY * h,
            jitterTragusPx = jitterTragusPx,
            jitterC7Px = jitterC7Px,
            cvaStdDev = cvaStdDev,
            rawInstantaneousCva = rawInstCva,
            isSmoothed = isSmoothed
        )
    }
}