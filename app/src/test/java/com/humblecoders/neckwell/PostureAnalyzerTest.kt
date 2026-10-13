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
        // CVA = 49.0° (>= 48.0° active threshold)
        // tan(49°) ≈ 1.15037. Let dx = 0.10, dy = 0.11504
        val dx = 0.10f
        val dy = (dx * tan(Math.toRadians(49.0))).toFloat() // ≈ 0.115037f
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
    fun testClearlyBadForwardHeadPostureBelow48() {
        // Forward head posture: ear pushed forward horizontally (dx=0.25, dy=0.10)
        // CVA = atan2(0.10, 0.25) ≈ 21.8° (< 48°)
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
        // Low landmark visibility
        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.50f,
            earY = 0.25f,
            earVis = 0.20f, // Below 0.3 threshold
            shX = 0.50f,
            shY = 0.45f,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertFalse("Expected low visibility to be rejected", metrics.isCorrect)
        assertEquals("Turn sideways — show ear & shoulder", metrics.reason)
    }

    @Test
    fun testExcessiveLateralTiltRejection() {
        // Both ears visible, but head tilted sideways heavily (roll > 15°)
        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.40f,
            earY = 0.20f,
            earVis = 0.90f,
            shX = 0.42f,
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
        PostureAnalyzer.cvaThreshold = PostureAnalyzer.CVA_THRESHOLD_DEFAULT // 48f

        assertEquals("Good", PostureAnalyzer.classifyPosture(48f))
        assertEquals("Good", PostureAnalyzer.classifyPosture(49f))
        assertEquals("Good", PostureAnalyzer.classifyPosture(50f))
        assertEquals("Good", PostureAnalyzer.classifyPosture(55f))
        assertEquals("Good", PostureAnalyzer.classifyPosture(57f))

        // Values below threshold (48°) MUST be Poor
        assertEquals("Poor", PostureAnalyzer.classifyPosture(47.9f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(45f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(25f))

        // isGoodPosture helper
        assertTrue(PostureAnalyzer.isGoodPosture(48f))
        assertTrue(PostureAnalyzer.isGoodPosture(49f))
        assertTrue(PostureAnalyzer.isGoodPosture(50f))
        assertTrue(PostureAnalyzer.isGoodPosture(55f))
        assertFalse(PostureAnalyzer.isGoodPosture(47.9f))
        assertFalse(PostureAnalyzer.isGoodPosture(25f))

        // isPoorPosture helper
        assertFalse(PostureAnalyzer.isPoorPosture(48f))
        assertFalse(PostureAnalyzer.isPoorPosture(49f))
        assertFalse(PostureAnalyzer.isPoorPosture(50f))
        assertTrue(PostureAnalyzer.isPoorPosture(47.9f))
        assertTrue(PostureAnalyzer.isPoorPosture(25f))

        // Calibration-block check uses same threshold
        assertTrue(PostureAnalyzer.canCalibrate(48f))
        assertTrue(PostureAnalyzer.canCalibrate(50f))
        assertFalse(PostureAnalyzer.canCalibrate(47.9f))
        assertFalse(PostureAnalyzer.canCalibrate(25f))
    }

    @Test
    fun testThresholdClampingTo45Through57() {
        // Below MIN (45f) must be clamped to 45f
        PostureAnalyzer.cvaThreshold = 40f
        assertEquals(45f, PostureAnalyzer.cvaThreshold, 0.001f)
        assertEquals(PostureAnalyzer.CVA_THRESHOLD_MIN, PostureAnalyzer.cvaThreshold, 0.001f)

        PostureAnalyzer.cvaThreshold = -10f
        assertEquals(45f, PostureAnalyzer.cvaThreshold, 0.001f)

        // Above MAX (57f) must be clamped to 57f
        PostureAnalyzer.cvaThreshold = 65f
        assertEquals(57f, PostureAnalyzer.cvaThreshold, 0.001f)
        assertEquals(PostureAnalyzer.CVA_THRESHOLD_MAX, PostureAnalyzer.cvaThreshold, 0.001f)

        PostureAnalyzer.cvaThreshold = 100f
        assertEquals(57f, PostureAnalyzer.cvaThreshold, 0.001f)

        // Valid values within 45f..57f are preserved
        PostureAnalyzer.cvaThreshold = 52f
        assertEquals(52f, PostureAnalyzer.cvaThreshold, 0.001f)

        PostureAnalyzer.cvaThreshold = 45f
        assertEquals(45f, PostureAnalyzer.cvaThreshold, 0.001f)

        PostureAnalyzer.cvaThreshold = 57f
        assertEquals(57f, PostureAnalyzer.cvaThreshold, 0.001f)

        // Reset to default
        PostureAnalyzer.cvaThreshold = PostureAnalyzer.CVA_THRESHOLD_DEFAULT
        assertEquals(48f, PostureAnalyzer.cvaThreshold, 0.001f)
    }

    @Test
    fun testClassificationAndCalibrationWithAdjustedThreshold() {
        // Threshold adjusted to lower bound (45°)
        PostureAnalyzer.cvaThreshold = 45f
        assertEquals("Good", PostureAnalyzer.classifyPosture(45f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(44.9f))
        assertTrue(PostureAnalyzer.canCalibrate(45f))
        assertFalse(PostureAnalyzer.canCalibrate(44.9f))

        // Threshold adjusted to upper bound (57°)
        PostureAnalyzer.cvaThreshold = 57f
        assertEquals("Good", PostureAnalyzer.classifyPosture(57f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(56.9f))
        assertTrue(PostureAnalyzer.canCalibrate(57f))
        assertFalse(PostureAnalyzer.canCalibrate(56.9f))

        // Threshold adjusted to 50°
        PostureAnalyzer.cvaThreshold = 50f
        assertEquals("Good", PostureAnalyzer.classifyPosture(50f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(49.9f))
        assertTrue(PostureAnalyzer.canCalibrate(50f))
        assertFalse(PostureAnalyzer.canCalibrate(49.9f))

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
            // 1. Average adult build: dy = 65.5px, dx = 57.5px -> atan2(65.5, 57.5) = 48.7°
            SubjectProfile("Average Build", 154.5f, 248.0f, 212.0f, 313.5f),
            // 2. Slender / Long-neck build: dy = 69.0px, dx = 60.5px -> atan2(69.0, 60.5) = 48.75°
            SubjectProfile("Slender / Long Neck", 140.0f, 220.0f, 200.5f, 289.0f),
            // 3. Stocky / Broad build: dy = 61.0px, dx = 54.0px -> atan2(61.0, 54.0) = 48.48°
            SubjectProfile("Stocky / Broad Build", 165.0f, 270.0f, 219.0f, 331.0f)
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

