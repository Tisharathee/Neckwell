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
        // Shoulder at (0.50, 0.45), Ear at (0.40, 0.25)
        // Vertical distance = 0.45 - 0.25 = 0.20
        // With 20% vertical offset:
        // C7 Y = 0.45 - (0.20 * 0.20) = 0.41
        // C7 X = 0.50
        val (c7X, c7Y) = PostureAnalyzer.deriveC7Landmark(
            shoulderX = 0.50f,
            shoulderY = 0.45f,
            earX = 0.40f,
            earY = 0.25f,
            verticalOffsetRatio = 0.20f
        )

        assertEquals(0.50f, c7X, 0.001f)
        assertEquals(0.41f, c7Y, 0.001f)
    }

    @Test
    fun testCvaShiftFromShoulderToDerivedC7() {
        // In a 1000x1000 frame:
        // Shoulder Joint at (500px, 450px)
        // Ear at (326px, 250px) -> dx = 174px
        // 1. Raw Shoulder CVA: dy = 450 - 250 = 200px.
        // tan(CVA_shoulder) = 200 / 174 ≈ 1.1494 -> CVA ≈ 49.0° (artificially within band)
        val metricsRawShoulder = PostureAnalyzer.computeMetrics(
            earX = 0.326f,
            earY = 0.250f,
            earVis = 0.95f,
            shX = 0.500f,
            shY = 0.450f,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )
        assertEquals(49.0f, metricsRawShoulder.cva, 0.2f)

        // 2. Corrected C7 Neck Base Landmark:
        // Elevated 20% upward: C7 Y = 0.450 - (0.20 * 0.200) = 0.410 (410px)
        // Corrected dy = 410 - 250 = 160px.
        // tan(CVA_c7) = 160 / 174 ≈ 0.9195 -> CVA ≈ 42.6° (accurately reveals forward head posture!)
        val (c7X, c7Y) = PostureAnalyzer.deriveC7Landmark(0.500f, 0.450f, 0.326f, 0.250f)
        val metricsDerivedC7 = PostureAnalyzer.computeMetrics(
            earX = 0.326f,
            earY = 0.250f,
            earVis = 0.95f,
            shX = c7X,
            shY = c7Y,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000,
            rawShoulderX = 0.500f,
            rawShoulderY = 0.450f
        )
        assertEquals(42.6f, metricsDerivedC7.cva, 0.3f)
        // Correctly flags as Poor posture because actual CVA from neck base is 42.6° (< 48°)
        assertEquals("Poor", metricsDerivedC7.posture)
        assertFalse(metricsDerivedC7.isCorrect)

        // Verifies raw shoulder coords are preserved alongside C7
        assertEquals(500f, metricsDerivedC7.shoulderXPx, 0.1f)
        assertEquals(450f, metricsDerivedC7.shoulderYPx, 0.1f)
        assertEquals(500f, metricsDerivedC7.c7XPx, 0.1f)
        assertEquals(410f, metricsDerivedC7.c7YPx, 0.1f)
    }
}
