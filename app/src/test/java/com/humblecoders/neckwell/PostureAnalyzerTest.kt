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
}
