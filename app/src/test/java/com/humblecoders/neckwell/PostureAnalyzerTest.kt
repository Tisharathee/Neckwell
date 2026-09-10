package com.humblecoders.neckwell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostureAnalyzerTest {

    @Test
    fun testClearlyGoodUprightPosture() {
        // Ear directly above shoulder, small horizontal displacement (dx=0.03, dy=0.20)
        // CVA = atan2(0.20, 0.03) ≈ 81.5° (well above 48° threshold)
        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.50f,
            earY = 0.25f,
            earVis = 0.95f,
            shX = 0.53f,
            shY = 0.45f,
            shVis = 0.95f
        )

        assertTrue("Expected upright posture to be correct", metrics.isCorrect)
        assertTrue("Expected CVA >= 48°", metrics.cva >= 48f)
        assertEquals("Good posture — hold still", metrics.reason)
    }

    @Test
    fun testClearlyBadForwardHeadPosture() {
        // Forward head posture: ear pushed forward horizontally (dx=0.25, dy=0.10)
        // CVA = atan2(0.10, 0.25) ≈ 21.8° (< 48°) and dx (0.25) exceeds MAX_EAR_SHOULDER_DX (0.16)
        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.25f,
            earY = 0.35f,
            earVis = 0.95f,
            shX = 0.50f,
            shY = 0.45f,
            shVis = 0.95f
        )

        assertFalse("Expected forward head posture to be classified as incorrect", metrics.isCorrect)
        assertTrue("Expected CVA < 48°", metrics.cva < 48f)
        assertTrue("Reason should ask user to sit straight", metrics.reason.startsWith("Sit straight"))
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
            shVis = 0.95f
        )

        assertFalse("Expected head below shoulder to be classified as incorrect", metrics.isCorrect)
        assertEquals(0f, metrics.cva, 0.001f)
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
            shVis = 0.95f
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
            otherEarVis = 0.90f
        )

        assertFalse("Expected excessive lateral tilt to be rejected", metrics.isCorrect)
        assertTrue("Reason should flag head tilt", metrics.reason.startsWith("Level your head"))
    }

    @Test
    fun testPostureClassificationAndHelpers() {
        assertEquals("Good", PostureAnalyzer.classifyPosture(48f))
        assertEquals("Good", PostureAnalyzer.classifyPosture(65f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(47.9f))
        assertEquals("Poor", PostureAnalyzer.classifyPosture(25f))

        assertTrue(PostureAnalyzer.isGoodPosture(48f))
        assertFalse(PostureAnalyzer.isGoodPosture(47.9f))

        assertTrue(PostureAnalyzer.isPoorPosture(47.9f))
        assertFalse(PostureAnalyzer.isPoorPosture(48f))

        val goodMetrics = PostureAnalyzer.computeMetrics(
            earX = 0.50f,
            earY = 0.25f,
            earVis = 0.95f,
            shX = 0.53f,
            shY = 0.45f,
            shVis = 0.95f
        )
        assertEquals("Good", goodMetrics.posture)

        val badMetrics = PostureAnalyzer.computeMetrics(
            earX = 0.25f,
            earY = 0.35f,
            earVis = 0.95f,
            shX = 0.50f,
            shY = 0.45f,
            shVis = 0.95f
        )
        assertEquals("Poor", badMetrics.posture)
    }
}
