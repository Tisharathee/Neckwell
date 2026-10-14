package com.humblecoders.neckwell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

class PostureAnalyzerTest {

    @Test
    fun testClearlyGoodUprightPostureAboveThreshold() {
        PostureAnalyzer.cvaThreshold = PostureAnalyzer.CVA_THRESHOLD_DEFAULT
        // CVA = 56.0° (>= 55.0° active threshold)
        // tan(56°) ≈ 1.48256. Let dx = 0.10, dy = 0.14826
        val dx = 0.10f
        val dy = (dx * tan(Math.toRadians(56.0))).toFloat() // ≈ 0.148256f
        val shX = 0.50f
        val shY = 0.40f
        val earX = shX - dx // 0.40f
        val earY = shY - dy // ≈ 0.28496f

        val metrics = PostureAnalyzer.computeMetrics(
            earX = earX,
            earY = earY,
            earVis = 0.95f,
            shX = shX,
            shY = shY,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertTrue("Expected posture with CVA >= threshold to be correct", metrics.isCorrect)
        assertTrue("Expected CVA >= active threshold", metrics.cva >= PostureAnalyzer.cvaThreshold)
        assertEquals("Good", metrics.posture)
        assertEquals("Good posture — hold still", metrics.reason)
        assertTrue("Calibration must be allowed for good posture", PostureAnalyzer.canCalibrate(metrics.cva))
    }

    @Test
    fun testClearlyBadForwardHeadPostureBelowThreshold() {
        // Forward head posture: ear pushed forward horizontally (dx=0.25, dy=0.10)
        // CVA = atan2(0.10, 0.25) ≈ 21.8° (< 55°)
        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.25f,
            earY = 0.35f,
            earVis = 0.95f,
            shX = 0.50f,
            shY = 0.45f,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertFalse("Expected forward head posture to be classified as incorrect", metrics.isCorrect)
        assertTrue("Expected CVA < active threshold", metrics.cva < PostureAnalyzer.cvaThreshold)
        assertEquals("Poor", metrics.posture)
        assertTrue("Reason should ask user to sit straight", metrics.reason.contains("head forward"))
        assertFalse("Calibration must be blocked for poor posture", PostureAnalyzer.canCalibrate(metrics.cva))
    }

    @Test
    fun testClearlyBadSlouchedHeadBelowShoulder() {
        // Severe slouch: ear is below shoulder level (earY=0.55, shY=0.45, dy=-0.10)
        // Must be rejected immediately
        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.50f,
            earY = 0.55f,
            earVis = 0.95f,
            shX = 0.50f,
            shY = 0.45f,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertFalse("Expected head below shoulder to be classified as incorrect", metrics.isCorrect)
        assertEquals(0f, metrics.cva, 0.001f)
        assertEquals("Poor", metrics.posture)
        assertTrue("Reason should flag head tilted down", metrics.reason.contains("head is tilted too far down"))
    }

    @Test
    fun testLowVisibilityRejection() {
        // Low landmark visibility (< 0.5) must show "No person detected, adjust camera"
        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.50f,
            earY = 0.25f,
            earVis = 0.20f, // Below 0.5 threshold
            shX = 0.50f,
            shY = 0.45f,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertFalse("Expected low visibility to be rejected", metrics.isCorrect)
        assertFalse("Expected invalid landmarks", metrics.hasValidLandmarks)
        assertEquals("No person detected, adjust camera", metrics.reason)
        assertEquals(0f, metrics.cva, 0.001f)
        assertFalse("Calibration must be blocked when no person detected", PostureAnalyzer.canCalibrate(metrics))
    }

    @Test
    fun testLandmarksOutsideFrameRejected() {
        // Landmark outside frame (earX = 1.05f)
        val metrics = PostureAnalyzer.computeMetrics(
            earX = 1.05f,
            earY = 0.25f,
            earVis = 0.95f,
            shX = 0.50f,
            shY = 0.45f,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertFalse(metrics.isCorrect)
        assertFalse(metrics.hasValidLandmarks)
        assertEquals("No person detected, adjust camera", metrics.reason)
        assertEquals(0f, metrics.cva, 0.001f)
        assertFalse(PostureAnalyzer.canCalibrate(metrics))
    }

    @Test
    fun testExcessiveLateralTiltRejection() {
        // Both ears visible, but head tilted sideways heavily (roll > 15°)
        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.40f,
            earY = 0.20f,
            earVis = 0.90f,
            shX = 0.54f, // Set shX to 0.54 so CVA = atan2(0.25, 0.14) = 60.7° (plausible), while testing lateral tilt
            shY = 0.45f,
            shVis = 0.90f,
            otherEarX = 0.55f,
            otherEarY = 0.30f, // Big vertical drop between ears
            otherEarVis = 0.90f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertFalse("Expected excessive lateral tilt to be rejected", metrics.isCorrect)
        assertTrue("Reason should flag head tilt", metrics.reason.startsWith("Level your head"))
    }

    @Test
    fun testPostureClassificationAndHelpers() {
        PostureAnalyzer.cvaThreshold = PostureAnalyzer.CVA_THRESHOLD_DEFAULT // 55f

        assertEquals("Good", PostureAnalyzer.classifyPosture(55f))
        assertEquals("Good", PostureAnalyzer.classifyPosture(56f))
        assertEquals("Good", PostureAnalyzer.classifyPosture(57f))
        assertEquals("Good", PostureAnalyzer.classifyPosture(58f))
        assertEquals("Good", PostureAnalyzer.classifyPosture(59f))

        // Values below threshold (55°) MUST be Poor
        assertEquals("Poor", PostureAnalyzer.classifyPosture(54.9f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(51f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(48f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(25f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(20f))

        // Values outside plausibility range (20°..75°) MUST be Invalid and block calibration
        assertEquals("Invalid", PostureAnalyzer.classifyPosture(89.8f))
        assertEquals("Invalid", PostureAnalyzer.classifyPosture(75.1f))
        assertEquals("Invalid", PostureAnalyzer.classifyPosture(19.9f))
        assertEquals("Invalid", PostureAnalyzer.classifyPosture(0f))

        // isGoodPosture helper
        assertTrue(PostureAnalyzer.isGoodPosture(55f))
        assertTrue(PostureAnalyzer.isGoodPosture(56f))
        assertTrue(PostureAnalyzer.isGoodPosture(57f))
        assertTrue(PostureAnalyzer.isGoodPosture(59f))
        assertFalse(PostureAnalyzer.isGoodPosture(54.9f))
        assertFalse(PostureAnalyzer.isGoodPosture(48f))
        assertFalse(PostureAnalyzer.isGoodPosture(25f))
        assertFalse("89.8° ceiling artifact must never be Good", PostureAnalyzer.isGoodPosture(89.8f))
        assertFalse("75.1° must never be Good", PostureAnalyzer.isGoodPosture(75.1f))
        assertFalse("19.9° must never be Good", PostureAnalyzer.isGoodPosture(19.9f))

        // isPoorPosture helper
        assertFalse(PostureAnalyzer.isPoorPosture(55f))
        assertFalse(PostureAnalyzer.isPoorPosture(56f))
        assertFalse(PostureAnalyzer.isPoorPosture(59f))
        assertTrue(PostureAnalyzer.isPoorPosture(54.9f))
        assertTrue(PostureAnalyzer.isPoorPosture(48f))
        assertTrue(PostureAnalyzer.isPoorPosture(25f))
        assertTrue(PostureAnalyzer.isPoorPosture(89.8f)) // Not Good

        // Calibration-block check uses same threshold and plausibility guard
        assertTrue(PostureAnalyzer.canCalibrate(55f))
        assertTrue(PostureAnalyzer.canCalibrate(59f))
        assertFalse(PostureAnalyzer.canCalibrate(54.9f))
        assertFalse(PostureAnalyzer.canCalibrate(48f))
        assertFalse(PostureAnalyzer.canCalibrate(25f))
        assertFalse("89.8° must block calibration", PostureAnalyzer.canCalibrate(89.8f))
        assertFalse("75.1° must block calibration", PostureAnalyzer.canCalibrate(75.1f))
        assertFalse("19.9° must block calibration", PostureAnalyzer.canCalibrate(19.9f))
    }

    @Test
    fun testCeilingArtifact89DegPlausibilityGuard() {
        val ceilingMetrics = PostureAnalyzer.computeMetrics(
            earX = 0.500f,
            earY = 0.100f,
            earVis = 0.95f,
            shX = 0.501f, // Almost directly under ear -> dx = 1px, dy = 700px -> CVA ~ 89.9°
            shY = 0.800f,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertTrue("Expected CVA > 75° (actual: ${ceilingMetrics.cva}°)", ceilingMetrics.cva > 75f)
        assertFalse("Ceiling artifact must not be correct", ceilingMetrics.isCorrect)
        assertEquals("Invalid", ceilingMetrics.posture)
        assertEquals("Invalid, retake", ceilingMetrics.reason)
        assertFalse("Ceiling artifact must block calibration", PostureAnalyzer.canCalibrate(ceilingMetrics))
    }

    @Test
    fun testRealPersonSideViewPostureSlouchedAndUpright() {
        // Side-view camera setup:
        // A. Upright posture: head aligned with cervical spine
        val uprightEarX = 0.49f
        val uprightEarY = 0.24f
        val shoulderX = 0.50f
        val shoulderY = 0.46f

        val (uprightTragusX, uprightTragusY) = PostureAnalyzer.deriveTragusLandmark(
            earX = uprightEarX,
            earY = uprightEarY,
            isFacingLeft = true,
            neckHeight = shoulderY - uprightEarY
        )
        val (uprightC7X, uprightC7Y) = PostureAnalyzer.deriveC7Landmark(
            shoulderMidX = shoulderX,
            shoulderMidY = shoulderY,
            earX = uprightTragusX,
            earY = uprightTragusY,
            isFacingLeft = true
        )

        val uprightMetrics = PostureAnalyzer.computeMetrics(
            earX = uprightTragusX,
            earY = uprightTragusY,
            earVis = 0.95f,
            shX = uprightC7X,
            shY = uprightC7Y,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        println("Upright Side View CVA: ${uprightMetrics.cva}° | Posture: ${uprightMetrics.posture}")
        assertTrue("Upright CVA should be about 55°-65° (actual: ${uprightMetrics.cva}°)", uprightMetrics.cva in 55f..65f)
        assertTrue("Upright posture must be classified as Good", uprightMetrics.isCorrect)
        assertEquals("Good", uprightMetrics.posture)
        assertTrue("Calibration must be allowed for upright posture", PostureAnalyzer.canCalibrate(uprightMetrics))

        // B. Slouched posture: head translated forward (ear moves anteriorly to 0.38)
        val slouchedEarX = 0.38f
        val slouchedEarY = 0.28f

        val (slouchedTragusX, slouchedTragusY) = PostureAnalyzer.deriveTragusLandmark(
            earX = slouchedEarX,
            earY = slouchedEarY,
            isFacingLeft = true,
            neckHeight = shoulderY - slouchedEarY
        )
        val (slouchedC7X, slouchedC7Y) = PostureAnalyzer.deriveC7Landmark(
            shoulderMidX = shoulderX,
            shoulderMidY = shoulderY,
            earX = slouchedTragusX,
            earY = slouchedTragusY,
            isFacingLeft = true
        )

        val slouchedMetrics = PostureAnalyzer.computeMetrics(
            earX = slouchedTragusX,
            earY = slouchedTragusY,
            earVis = 0.95f,
            shX = slouchedC7X,
            shY = slouchedC7Y,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        println("Slouched Side View CVA: ${slouchedMetrics.cva}° | Posture: ${slouchedMetrics.posture}")
        assertTrue("Slouched CVA should be about 35°-45° (actual: ${slouchedMetrics.cva}°)", slouchedMetrics.cva in 30f..48f)
        assertFalse("Slouched posture must NOT be correct", slouchedMetrics.isCorrect)
        assertEquals("Poor", slouchedMetrics.posture)
        assertFalse("Calibration must be blocked for slouched posture", PostureAnalyzer.canCalibrate(slouchedMetrics))
    }

    @Test
    fun testThresholdClampingTo51Through59() {
        // Below MIN (51f) must be clamped to 51f
        PostureAnalyzer.cvaThreshold = 40f
        assertEquals(51f, PostureAnalyzer.cvaThreshold, 0.001f)
        assertEquals(PostureAnalyzer.CVA_THRESHOLD_MIN, PostureAnalyzer.cvaThreshold, 0.001f)

        PostureAnalyzer.cvaThreshold = -10f
        assertEquals(51f, PostureAnalyzer.cvaThreshold, 0.001f)

        // Above MAX (59f) must be clamped to 59f
        PostureAnalyzer.cvaThreshold = 65f
        assertEquals(59f, PostureAnalyzer.cvaThreshold, 0.001f)
        assertEquals(PostureAnalyzer.CVA_THRESHOLD_MAX, PostureAnalyzer.cvaThreshold, 0.001f)

        PostureAnalyzer.cvaThreshold = 100f
        assertEquals(59f, PostureAnalyzer.cvaThreshold, 0.001f)

        // Valid values within 51f..59f are preserved
        PostureAnalyzer.cvaThreshold = 55f
        assertEquals(55f, PostureAnalyzer.cvaThreshold, 0.001f)

        PostureAnalyzer.cvaThreshold = 51f
        assertEquals(51f, PostureAnalyzer.cvaThreshold, 0.001f)

        PostureAnalyzer.cvaThreshold = 59f
        assertEquals(59f, PostureAnalyzer.cvaThreshold, 0.001f)

        // Reset to default
        PostureAnalyzer.cvaThreshold = PostureAnalyzer.CVA_THRESHOLD_DEFAULT
        assertEquals(55f, PostureAnalyzer.cvaThreshold, 0.001f)
    }

    @Test
    fun testClassificationAndCalibrationWithAdjustedThreshold() {
        // Threshold adjusted to lower bound (51°)
        PostureAnalyzer.cvaThreshold = 51f
        assertEquals("Good", PostureAnalyzer.classifyPosture(51f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(50.9f))
        assertTrue(PostureAnalyzer.canCalibrate(51f))
        assertFalse(PostureAnalyzer.canCalibrate(50.9f))

        // Threshold adjusted to upper bound (59°)
        PostureAnalyzer.cvaThreshold = 59f
        assertEquals("Good", PostureAnalyzer.classifyPosture(59f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(58.9f))
        assertTrue(PostureAnalyzer.canCalibrate(59f))
        assertFalse(PostureAnalyzer.canCalibrate(58.9f))

        // Threshold adjusted to 55°
        PostureAnalyzer.cvaThreshold = 55f
        assertEquals("Good", PostureAnalyzer.classifyPosture(55f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(54.9f))
        assertTrue(PostureAnalyzer.canCalibrate(55f))
        assertFalse(PostureAnalyzer.canCalibrate(54.9f))

        // Reset
        PostureAnalyzer.cvaThreshold = PostureAnalyzer.CVA_THRESHOLD_DEFAULT
    }

    @Test
    fun testC7LandmarkNotPlacedOnShoulder() {
        val shL = Pair(0.48f, 0.47f)
        val shR = Pair(0.52f, 0.45f)
        val shoulderMidX = (shL.first + shR.first) / 2f // 0.50f
        val shoulderMidY = (shL.second + shR.second) / 2f // 0.46f

        val (c7X, c7Y) = PostureAnalyzer.deriveC7Landmark(
            shoulderMidX = shoulderMidX,
            shoulderMidY = shoulderMidY,
            earX = 0.40f,
            earY = 0.26f,
            isFacingLeft = true
        )

        // Must NOT match left shoulder
        assertFalse("C7 must not equal left shoulder", c7X == shL.first && c7Y == shL.second)
        // Must NOT match right shoulder
        assertFalse("C7 must not equal right shoulder", c7X == shR.first && c7Y == shR.second)
        // Must NOT match shoulder midpoint alone
        assertFalse("C7 must not equal shoulder midpoint alone", c7X == shoulderMidX && c7Y == shoulderMidY)
        // Must be elevated upward (lower Y)
        assertTrue("C7 must be above shoulder line", c7Y < shoulderMidY)
        // Must be shifted back along the neck
        assertTrue("C7 must be shifted back from shoulder line", c7X > shoulderMidX)
    }

    @Test
    fun testAnatomicalTragusDerivationForwardAndDownwardFromEar() {
        // Raw ear at (0.40, 0.25), neckHeight = 0.20
        // Facing Left: anterior is -X (toward face/jaw), inferior is +Y (toward jaw)
        // With default ratios: dx = 0.08 * 0.20 = 0.016, dy = 0.04 * 0.20 = 0.008
        val (tragusXLeft, tragusYLeft) = PostureAnalyzer.deriveTragusLandmark(
            earX = 0.40f,
            earY = 0.25f,
            isFacingLeft = true,
            neckHeight = 0.20f
        )
        assertEquals(0.384f, tragusXLeft, 0.001f) // Shifts forward toward face (-X)
        assertEquals(0.258f, tragusYLeft, 0.001f) // Shifts downward toward jaw (+Y)

        // Facing Right: anterior is +X (toward face/jaw)
        val (tragusXRight, tragusYRight) = PostureAnalyzer.deriveTragusLandmark(
            earX = 0.60f,
            earY = 0.25f,
            isFacingLeft = false,
            neckHeight = 0.20f
        )
        assertEquals(0.616f, tragusXRight, 0.001f) // Shifts forward toward face (+X)
        assertEquals(0.258f, tragusYRight, 0.001f) // Shifts downward toward jaw (+Y)
    }

    @Test
    fun testClinicalReferenceEuclideanErrorMetrics() {
        val testMetrics = PostureMetrics(
            cva = 48.8f,
            lateralTilt = 0f,
            shoulderAlignment = 0f,
            isCorrect = true,
            reason = "Test",
            tragusXPx = 157.0f, // 3px off from 154
            tragusYPx = 252.0f, // 4px off from 248 -> sqrt(3^2 + 4^2) = 5.0px error
            c7XPx = 215.0f,     // 3px off from 212
            c7YPx = 317.0f      // 4px off from 313 -> sqrt(3^2 + 4^2) = 5.0px error
        )

        val reference = ClinicalReference(
            tragusXPx = 154.0f,
            tragusYPx = 248.0f,
            c7XPx = 212.0f,
            c7YPx = 313.0f,
            expectedCva = 48.5f
        )

        val errors = PostureAnalyzer.computeLandmarkErrors(testMetrics, reference)
        assertEquals(5.0f, errors.tragusPixelError, 0.01f)
        assertEquals(5.0f, errors.c7PixelError, 0.01f)
        assertEquals(0.3f, errors.cvaDelta, 0.01f)
        assertTrue("Tragus error <= 8px should be considered accurate", errors.isTragusAccurate)
        assertTrue("C7 error <= 8px should be considered accurate", errors.isC7Accurate)
    }

    @Test
    fun testDatasetCalibrationSolver() {
        val initialKy = PostureAnalyzer.c7KyNeck
        val initialKx = PostureAnalyzer.c7KxNeck

        // Create a synthetic calibration dataset
        val samplePoints = listOf(
            CalibrationDataPoint(
                rawEarX = 0.154f,
                rawEarY = 0.248f,
                rawShoulderX = 0.187f,
                rawShoulderY = 0.348f,
                isFacingLeft = true,
                clinicalTragusX = 0.146f,
                clinicalTragusY = 0.252f,
                clinicalC7X = 0.212f,
                clinicalC7Y = 0.313f
            )
        )

        PostureAnalyzer.calibrateFromDataset(samplePoints)

        // Verify calibrated ratios are within reasonable anatomical bounds
        assertTrue("Calibrated tragus anterior ratio should be within (0.02..0.20)", PostureAnalyzer.tragusAnteriorRatio in 0.02f..0.20f)
        assertTrue("Calibrated C7 Ky ratio should be within (0.20..0.45)", PostureAnalyzer.c7KyNeck in 0.20f..0.45f)
        assertTrue("Calibrated C7 Kx ratio should be within (0.15..0.35)", PostureAnalyzer.c7KxNeck in 0.15f..0.35f)

        // Restore defaults
        PostureAnalyzer.c7KyNeck = initialKy
        PostureAnalyzer.c7KxNeck = initialKx
        PostureAnalyzer.tragusAnteriorRatio = PostureAnalyzer.DEFAULT_TRAGUS_ANTERIOR_RATIO
        PostureAnalyzer.tragusInferiorRatio = PostureAnalyzer.DEFAULT_TRAGUS_INFERIOR_RATIO
    }

    @Test
    fun testMovingAverageLandmarkSmoothingReducesJitter() {
        val smoother = PostureAnalyzer.LandmarkSmoother(windowSize = 5)
        val numFrames = 40
        val baseEarX = 0.154f
        val baseEarY = 0.248f

        val rawFrames = mutableListOf<PostureAnalyzer.RawLandmarkFrame>()
        val smoothedFrames = mutableListOf<PostureAnalyzer.SmoothedLandmarkFrame>()

        // Simulate noisy frames with alternating micro-sensor jitter (+- 4 pixels at 1000px = +- 0.004f)
        for (i in 0 until numFrames) {
            val noiseX = if (i % 2 == 0) 0.004f else -0.004f
            val noiseY = if (i % 3 == 0) 0.003f else -0.003f
            val raw = PostureAnalyzer.RawLandmarkFrame(
                earX = baseEarX + noiseX,
                earY = baseEarY + noiseY,
                leftShX = 0.187f,
                leftShY = 0.348f,
                rightShX = 0.220f,
                rightShY = 0.348f,
                noseX = 0.120f,
                noseY = 0.245f,
                otherEarX = null,
                otherEarY = null,
                mouthX = null,
                mouthY = null,
                sideLabel = "Left"
            )
            rawFrames.add(raw)
            smoothedFrames.add(smoother.smooth(raw))
        }

        // Calculate average frame-to-frame jitter in pixels (after 5-frame warmup)
        var totalRawJitter = 0f
        var totalSmoothedJitter = 0f
        var count = 0

        for (i in 6 until numFrames) {
            val rawDx = (rawFrames[i].earX - rawFrames[i - 1].earX) * 1000f
            val rawDy = (rawFrames[i].earY - rawFrames[i - 1].earY) * 1000f
            val rawJitter = kotlin.math.sqrt(rawDx * rawDx + rawDy * rawDy)

            val smDx = (smoothedFrames[i].earX - smoothedFrames[i - 1].earX) * 1000f
            val smDy = (smoothedFrames[i].earY - smoothedFrames[i - 1].earY) * 1000f
            val smJitter = kotlin.math.sqrt(smDx * smDx + smDy * smDy)

            totalRawJitter += rawJitter
            totalSmoothedJitter += smJitter
            count++
        }

        val avgRawJitter = totalRawJitter / count
        val avgSmoothedJitter = totalSmoothedJitter / count
        val jitterReductionPercent = ((avgRawJitter - avgSmoothedJitter) / avgRawJitter) * 100f

        println("=== Landmark Smoothing Jitter Reduction Benchmark ===")
        println("Average Raw Jitter: ${"%.2f".format(avgRawJitter)} px")
        println("Average Smoothed Jitter: ${"%.2f".format(avgSmoothedJitter)} px")
        println("Jitter Reduction: ${"%.1f".format(jitterReductionPercent)}%")

        assertTrue("Smoothed jitter should be substantially lower than raw jitter", avgSmoothedJitter < avgRawJitter)
        assertTrue("5-frame moving average should reduce jitter by at least 60%", jitterReductionPercent >= 60f)
        assertTrue("Smoothed frame-to-frame jitter should stay below 3.0 px", avgSmoothedJitter < 3.0f)
    }

    @Test
    fun testMultiSubjectGeneralizationAcrossBodyTypes() {
        data class SubjectProfile(
            val name: String,
            val tragusXPx: Float,
            val tragusYPx: Float,
            val c7XPx: Float,
            val c7YPx: Float
        )

        // 3 Distinct body builds in upright neutral posture:
        val subjects = listOf(
            // 1. Average adult build: dy = 72.75px, dx = 50.0px -> atan2(72.75, 50.0) = 55.5°
            SubjectProfile("Average Build", 162.0f, 240.75f, 212.0f, 313.5f),
            // 2. Slender / Long-neck build: dy = 73.0px, dx = 50.0px -> atan2(73.0, 50.0) = 55.6°
            SubjectProfile("Slender / Long Neck", 150.5f, 216.0f, 200.5f, 289.0f),
            // 3. Stocky / Broad build: dy = 72.5px, dx = 50.0px -> atan2(72.5, 50.0) = 55.4°
            SubjectProfile("Stocky / Broad Build", 169.0f, 258.5f, 219.0f, 331.0f)
        )

        println("=== Multi-Subject Anatomical Body Profile Verification ===")
        for (subj in subjects) {
            // A. Upright Neutral Posture
            val uprightMetrics = PostureAnalyzer.computeMetrics(
                earX = subj.tragusXPx / 1000f,
                earY = subj.tragusYPx / 1000f,
                earVis = 0.95f,
                shX = subj.c7XPx / 1000f,
                shY = subj.c7YPx / 1000f,
                shVis = 0.95f,
                imageWidth = 1000,
                imageHeight = 1000
            )

            println("${subj.name} [Upright]: CVA = ${"%.2f".format(uprightMetrics.cva)}° | Posture: ${uprightMetrics.posture}")
            assertTrue("${subj.name} upright CVA should be >= active threshold", uprightMetrics.cva >= PostureAnalyzer.cvaThreshold)
            assertTrue("${subj.name} upright should be classified as Good", uprightMetrics.isCorrect)
            assertEquals("Good", uprightMetrics.posture)
            assertTrue(PostureAnalyzer.canCalibrate(uprightMetrics.cva))

            // B. Forward Head Slouch (Head pushed forward by +45px)
            val slouchMetrics = PostureAnalyzer.computeMetrics(
                earX = (subj.tragusXPx - 45f) / 1000f, // Head pushed left (forward)
                earY = (subj.tragusYPx + 10f) / 1000f,
                earVis = 0.95f,
                shX = subj.c7XPx / 1000f,
                shY = subj.c7YPx / 1000f,
                shVis = 0.95f,
                imageWidth = 1000,
                imageHeight = 1000
            )

            println("${subj.name} [Slouch]: CVA = ${"%.2f".format(slouchMetrics.cva)}° | Posture: ${slouchMetrics.posture}")
            assertTrue("${subj.name} slouch CVA must be < active threshold", slouchMetrics.cva < PostureAnalyzer.cvaThreshold)
            assertFalse("${subj.name} slouch must NOT be correct", slouchMetrics.isCorrect)
            assertEquals("Poor", slouchMetrics.posture)
            assertTrue(slouchMetrics.reason.contains("head forward"))
            assertFalse(PostureAnalyzer.canCalibrate(slouchMetrics.cva))
        }
    }
}

