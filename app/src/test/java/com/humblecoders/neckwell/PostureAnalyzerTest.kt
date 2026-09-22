package com.humblecoders.neckwell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.tan

class PostureAnalyzerTest {

    @Test
    fun testClearlyGoodUprightPostureIn48To50Band() {
        // CVA = 49.0° (within 48.0°–50.0° inclusive target band)
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

        assertTrue("Expected posture within 48°–50° to be correct", metrics.isCorrect)
        assertTrue("Expected CVA >= 48°", metrics.cva >= 48f)
        assertTrue("Expected CVA <= 50°", metrics.cva <= 50f)
        assertEquals("Good", metrics.posture)
        assertEquals("Good posture — hold still", metrics.reason)
    }

    @Test
    fun testClearlyBadHyperextendedPostureAbove50() {
        // Hyperextended posture: head tilted back (e.g. CVA = 64.8° or 81.5°)
        // Exceeds the 50.0° upper threshold and MUST be classified as Poor
        val metrics64_8 = PostureAnalyzer.computeMetrics(
            earX = 0.50f - 0.10f,
            earY = 0.45f - (0.10f * tan(Math.toRadians(64.8))).toFloat(),
            earVis = 0.95f,
            shX = 0.50f,
            shY = 0.45f,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertFalse("Expected CVA 64.8° to be classified as incorrect (outside 48°–50° band)", metrics64_8.isCorrect)
        assertEquals("Poor", metrics64_8.posture)
        assertTrue("Reason should indicate head tilted back", metrics64_8.reason.contains("head tilted back"))

        // Also test high CVA = 81.5°
        val metrics81 = PostureAnalyzer.computeMetrics(
            earX = 0.50f,
            earY = 0.25f,
            earVis = 0.95f,
            shX = 0.53f,
            shY = 0.45f,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertFalse("Expected CVA 81.5° to be classified as incorrect", metrics81.isCorrect)
        assertEquals("Poor", metrics81.posture)
        assertTrue("Reason should indicate head tilted back", metrics81.reason.contains("head tilted back"))
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
        assertTrue("Expected CVA < 48°", metrics.cva < 48f)
        assertEquals("Poor", metrics.posture)
        assertTrue("Reason should ask user to sit straight", metrics.reason.contains("head forward"))
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
        assertEquals("Good", PostureAnalyzer.classifyPosture(48f))
        assertEquals("Good", PostureAnalyzer.classifyPosture(49f))
        assertEquals("Good", PostureAnalyzer.classifyPosture(50f))

        // Values outside 48°–50° MUST be Poor
        assertEquals("Poor", PostureAnalyzer.classifyPosture(47.9f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(50.1f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(25f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(64.8f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(65f))

        // isGoodPosture helper
        assertTrue(PostureAnalyzer.isGoodPosture(48f))
        assertTrue(PostureAnalyzer.isGoodPosture(49f))
        assertTrue(PostureAnalyzer.isGoodPosture(50f))
        assertFalse(PostureAnalyzer.isGoodPosture(47.9f))
        assertFalse(PostureAnalyzer.isGoodPosture(50.1f))
        assertFalse(PostureAnalyzer.isGoodPosture(64.8f))
        assertFalse(PostureAnalyzer.isGoodPosture(65f))

        // isPoorPosture helper
        assertFalse(PostureAnalyzer.isPoorPosture(48f))
        assertFalse(PostureAnalyzer.isPoorPosture(49f))
        assertFalse(PostureAnalyzer.isPoorPosture(50f))
        assertTrue(PostureAnalyzer.isPoorPosture(47.9f))
        assertTrue(PostureAnalyzer.isPoorPosture(50.1f))
        assertTrue(PostureAnalyzer.isPoorPosture(64.8f))
        assertTrue(PostureAnalyzer.isPoorPosture(65f))
    }

    @Test
    fun testCvaSampleValuesAgainst48To50Band() {
        // Specific sample values requested: 30°, 45°, 48°, 49°, 50°, 55°, 65°
        val sampleAngles = listOf(
            30.0f to "Poor",
            45.0f to "Poor",
            47.9f to "Poor",
            48.0f to "Good",  // Lower bound (inclusive)
            49.0f to "Good",  // Mid-band
            50.0f to "Good",  // Upper bound (inclusive)
            50.1f to "Poor",  // Just above upper bound
            55.0f to "Poor",
            64.8f to "Poor",  // Specific artifact value flagged by user
            65.0f to "Poor"
        )

        println("=== CVA Posture Classification Test against 48.0°–50.0° Band ===")
        for ((angle, expected) in sampleAngles) {
            val result = PostureAnalyzer.classifyPosture(angle)
            val isGood = PostureAnalyzer.isGoodPosture(angle)
            val isPoor = PostureAnalyzer.isPoorPosture(angle)

            println("CVA: ${"%.1f".format(angle)}° -> Classified: $result | isGood=$isGood, isPoor=$isPoor (Expected: $expected)")

            assertEquals("CVA $angle° classification failed", expected, result)
            if (expected == "Good") {
                assertTrue("CVA $angle° should be isGoodPosture=true", isGood)
                assertFalse("CVA $angle° should be isPoorPosture=false", isPoor)
            } else {
                assertFalse("CVA $angle° should be isGoodPosture=false", isGood)
                assertTrue("CVA $angle° should be isPoorPosture=true", isPoor)
            }
        }
    }

    @Test
    fun testDeriveC7LandmarkElevationFromShoulder() {
        // Shoulder Midpoint at (0.50, 0.46), Ear at (0.40, 0.26)
        // Vertical distance neckHeight = 0.46 - 0.26 = 0.20
        // Scaled proportionally by ear-to-shoulder distance:
        // Elevation (35%): dyUp = 0.35 * 0.20 = 0.070 -> C7 Y = 0.46 - 0.070 = 0.390
        // Facing left: nose < ear, posterior is +X.
        // Posterior shift (25%): dxBack = 0.25 * 0.20 = 0.050 -> C7 X = 0.50 + 0.050 = 0.550
        val (c7XLeft, c7YLeft) = PostureAnalyzer.deriveC7Landmark(
            shoulderMidX = 0.50f,
            shoulderMidY = 0.46f,
            earX = 0.40f,
            earY = 0.26f,
            isFacingLeft = true,
            hipMidY = null
        )

        assertEquals(0.550f, c7XLeft, 0.002f)
        assertEquals(0.390f, c7YLeft, 0.002f)

        // Facing right: ear is at 0.60, shoulder at 0.50
        // Posterior is -X -> C7 X = 0.50 - 0.050 = 0.450
        val (c7XRight, c7YRight) = PostureAnalyzer.deriveC7Landmark(
            shoulderMidX = 0.50f,
            shoulderMidY = 0.46f,
            earX = 0.60f,
            earY = 0.26f,
            isFacingLeft = false,
            hipMidY = null
        )

        assertEquals(0.450f, c7XRight, 0.002f)
        assertEquals(0.390f, c7YRight, 0.002f)
    }

    @Test
    fun testUserReportedCoordinatesMatchExpectedCva() {
        // User test capture coordinates:
        // Tragus: (154px, 248px), Shoulder Midpoint: (187px, 348px)
        // Ear-to-shoulder distance = 348 - 248 = 100px
        // Previously C7 was at (187px, 328px) with only 20px offset -> dy=80, dx=33 -> CVA=67.6° (~67.2°)
        // With 35% vertical offset and 25% dorsal offset:
        // dyUp = 0.35 * 100 = 35px -> C7 Y = 348 - 35 = 313px
        // dxBack = 0.25 * 100 = 25px -> C7 X = 187 + 25 = 212px
        // dy = 313 - 248 = 65px. dx = 212 - 154 = 58px.
        // CVA = atan2(65, 58) * 180 / PI = 48.3°! (Within the 48°–50° good posture band!)
        val (c7XNorm, c7YNorm) = PostureAnalyzer.deriveC7Landmark(
            shoulderMidX = 0.187f,
            shoulderMidY = 0.348f,
            earX = 0.154f,
            earY = 0.248f,
            isFacingLeft = true
        )

        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.154f,
            earY = 0.248f,
            earVis = 0.95f,
            shX = c7XNorm,
            shY = c7YNorm,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000,
            rawShoulderX = 0.187f,
            rawShoulderY = 0.348f
        )

        assertEquals(48.3f, metrics.cva, 0.5f)
        assertTrue("Expected CVA to drop into 48°–50° good posture band", metrics.isCorrect)
        assertEquals("Good", metrics.posture)
        assertEquals(313f, metrics.c7YPx, 1.0f)
        assertEquals(212f, metrics.c7XPx, 1.0f)
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
    fun testMultipleBodyProfilesAlignWithClinicalStandards() {
        // Case A: Upright Neutral Posture (Facing Left)
        val (c7XNeutral, c7YNeutral) = PostureAnalyzer.deriveC7Landmark(
            shoulderMidX = 0.520f,
            shoulderMidY = 0.460f,
            earX = 0.455f,
            earY = 0.260f,
            isFacingLeft = true
        )
        val metricsNeutral = PostureAnalyzer.computeMetrics(
            earX = 0.455f,
            earY = 0.260f,
            earVis = 0.95f,
            shX = c7XNeutral,
            shY = c7YNeutral,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000,
            rawShoulderX = 0.520f,
            rawShoulderY = 0.460f
        )
        // Verifies raw shoulder coords are preserved alongside C7
        assertEquals(520f, metricsNeutral.shoulderXPx, 0.1f)
        assertEquals(460f, metricsNeutral.shoulderYPx, 0.1f)
        assertTrue("Expected upright neutral posture to have CVA in target band (was ${metricsNeutral.cva})", metricsNeutral.cva in 48.0f..50.0f)
        assertEquals("Good", metricsNeutral.posture)

        // Case B: Forward Head Posture / Slouch (Facing Left)
        val (c7XSlouch, c7YSlouch) = PostureAnalyzer.deriveC7Landmark(
            shoulderMidX = 0.520f,
            shoulderMidY = 0.460f,
            earX = 0.310f,
            earY = 0.320f,
            isFacingLeft = true
        )
        val metricsSlouch = PostureAnalyzer.computeMetrics(
            earX = 0.310f,
            earY = 0.320f,
            earVis = 0.95f,
            shX = c7XSlouch,
            shY = c7YSlouch,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000,
            rawShoulderX = 0.520f,
            rawShoulderY = 0.460f
        )
        assertTrue("Slouch posture must have CVA < 48°", metricsSlouch.cva < 48f)
        assertEquals("Poor", metricsSlouch.posture)
        assertFalse(metricsSlouch.isCorrect)
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
    fun testCalibratedC7FormulaWithNeckAndShoulderParameters() {
        // Test C7 regression formula:
        // dyUp = (kyNeck * neckH) + (kyShoulder * shoulderWidth)
        // dxBack = (kxNeck * neckH) + (kxShoulder * shoulderWidth)
        val neckH = 0.20f
        val shoulderWidth = 0.10f
        val (c7X, c7Y) = PostureAnalyzer.deriveC7Landmark(
            shoulderMidX = 0.50f,
            shoulderMidY = 0.46f,
            earX = 0.40f,
            earY = 0.26f,
            isFacingLeft = true,
            hipMidY = null,
            shoulderWidth = shoulderWidth,
            kyNeck = 0.35f,
            kyShoulder = 0.05f,
            kxNeck = 0.25f,
            kxShoulder = 0.04f
        )

        // dyUp = (0.35 * 0.20) + (0.05 * 0.10) = 0.070 + 0.005 = 0.075 -> C7 Y = 0.46 - 0.075 = 0.385
        // dxBack = (0.25 * 0.20) + (0.04 * 0.10) = 0.050 + 0.004 = 0.054 -> C7 X = 0.50 + 0.054 = 0.554
        assertEquals(0.385f, c7Y, 0.002f)
        assertEquals(0.554f, c7X, 0.002f)
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
    fun testContinuous15SecondHoldStability() {
        val smoother = PostureAnalyzer.LandmarkSmoother(windowSize = 5)
        val totalFrames = 450 // 15 seconds at 30 fps
        val cvaValues = mutableListOf<Float>()

        // Upright reference: Tragus ~ (155.5, 248), Shoulder ~ (187, 348)
        // Derived C7 lands at ~ (212, 313) -> dy = 65, dx = 56.5 -> Base CVA = 49.0° (centered in 48°–50° band)
        // Add pseudo-random micro-jitter (sensor noise) within +-1.0 px
        for (frame in 0 until totalFrames) {
            val noiseDx = (kotlin.math.sin(frame * 0.4) * 0.0006f).toFloat()
            val noiseDy = (kotlin.math.cos(frame * 0.3) * 0.0006f).toFloat()

            val rawEarX = 0.1555f + noiseDx
            val rawEarY = 0.2480f + noiseDy
            val rawShMidX = 0.1870f - noiseDx
            val rawShMidY = 0.3480f - noiseDy

            val raw = PostureAnalyzer.RawLandmarkFrame(
                earX = rawEarX,
                earY = rawEarY,
                leftShX = rawShMidX - 0.02f,
                leftShY = rawShMidY,
                rightShX = rawShMidX + 0.02f,
                rightShY = rawShMidY,
                noseX = rawEarX - 0.03f,
                noseY = rawEarY,
                otherEarX = null,
                otherEarY = null,
                mouthX = null,
                mouthY = null,
                sideLabel = "Left"
            )

            val smoothed = smoother.smooth(raw)
            val smoothedShMidX = (smoothed.leftShX + smoothed.rightShX) / 2f
            val smoothedShMidY = (smoothed.leftShY + smoothed.rightShY) / 2f

            val (c7X, c7Y) = PostureAnalyzer.deriveC7Landmark(
                shoulderMidX = smoothedShMidX,
                shoulderMidY = smoothedShMidY,
                earX = smoothed.earX,
                earY = smoothed.earY,
                isFacingLeft = true
            )

            val metrics = PostureAnalyzer.computeMetrics(
                earX = smoothed.earX,
                earY = smoothed.earY,
                earVis = 0.95f,
                shX = c7X,
                shY = c7Y,
                shVis = 0.95f,
                imageWidth = 1000,
                imageHeight = 1000,
                rawShoulderX = smoothedShMidX,
                rawShoulderY = smoothedShMidY,
                isSmoothed = true
            )

            val (_, _, cvaStdDev) = smoother.updateLandmarkJitterAndCva(
                tragusXPx = metrics.tragusXPx,
                tragusYPx = metrics.tragusYPx,
                c7XPx = metrics.c7XPx,
                c7YPx = metrics.c7YPx,
                cva = metrics.cva
            )

            cvaValues.add(metrics.cva)

            // After initial 5-frame warmup, verify strict hold requirements
            if (frame >= 5) {
                assertTrue("Frame $frame: CVA (${metrics.cva}°) must be >= 48.0°", metrics.cva >= 48.0f)
                assertTrue("Frame $frame: CVA (${metrics.cva}°) must be <= 50.0°", metrics.cva <= 50.0f)
                assertTrue("Frame $frame must be classified as Good posture", metrics.isCorrect)
                assertEquals("Good", metrics.posture)
            }
            // Verify rolling CVA std dev once the 15-frame rolling window is populated (clinical target: <= 0.6°)
            if (frame >= 15) {
                assertTrue("Frame $frame: Rolling CVA std dev ($cvaStdDev°) must remain stable (< 0.6°)", cvaStdDev < 0.6f)
            }
        }

        val warmCvas = cvaValues.subList(5, totalFrames)
        val meanCva = warmCvas.average().toFloat()
        val variance = warmCvas.map { (it - meanCva) * (it - meanCva) }.average()
        val overallStdDev = kotlin.math.sqrt(variance).toFloat()

        println("=== Continuous 15s Hold (450 Frames) Stability Benchmark ===")
        println("Mean CVA: ${"%.2f".format(meanCva)}°")
        println("CVA Range: ${"%.2f".format(warmCvas.minOrNull() ?: 0f)}° – ${"%.2f".format(warmCvas.maxOrNull() ?: 0f)}°")
        println("Overall Std Dev: ${"%.3f".format(overallStdDev)}°")

        assertTrue("Mean CVA must be inside 48.0°–50.0°", meanCva in 48.0f..50.0f)
        assertTrue("Overall std dev during 15s hold must be under 0.4°", overallStdDev < 0.4f)
        // Confirm not artificially clamped to a single identical number
        val uniqueValues = warmCvas.map { (it * 100).toInt() }.distinct()
        assertTrue("CVA values should show natural sub-degree variation, not artificial clamping", uniqueValues.size > 1)
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
            assertTrue("${subj.name} upright CVA should be >= 48.0°", uprightMetrics.cva >= 48.0f)
            assertTrue("${subj.name} upright CVA should be <= 50.0°", uprightMetrics.cva <= 50.0f)
            assertTrue("${subj.name} upright should be classified as Good", uprightMetrics.isCorrect)
            assertEquals("Good", uprightMetrics.posture)

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
            assertTrue("${subj.name} slouch CVA must be < 48.0°", slouchMetrics.cva < 48.0f)
            assertFalse("${subj.name} slouch must NOT be correct", slouchMetrics.isCorrect)
            assertEquals("Poor", slouchMetrics.posture)
            assertTrue(slouchMetrics.reason.contains("head forward"))

            // C. Hyperextended Posture (Head tilted back by -35px)
            val hyperextendedMetrics = PostureAnalyzer.computeMetrics(
                earX = (subj.tragusXPx + 35f) / 1000f, // Head tilted back toward spine
                earY = (subj.tragusYPx - 5f) / 1000f,
                earVis = 0.95f,
                shX = subj.c7XPx / 1000f,
                shY = subj.c7YPx / 1000f,
                shVis = 0.95f,
                imageWidth = 1000,
                imageHeight = 1000
            )

            println("${subj.name} [Hyperextended]: CVA = ${"%.2f".format(hyperextendedMetrics.cva)}° | Posture: ${hyperextendedMetrics.posture}")
            assertTrue("${subj.name} hyperextended CVA must be > 50.0°", hyperextendedMetrics.cva > 50.0f)
            assertFalse("${subj.name} hyperextended must NOT be correct", hyperextendedMetrics.isCorrect)
            assertEquals("Poor", hyperextendedMetrics.posture)
            assertTrue(hyperextendedMetrics.reason.contains("head tilted back"))
        }
    }
}

