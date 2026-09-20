package com.humblecoders.neckwell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.tan

/**
 * Clinical CVA (Craniovertebral Angle) computation unit tests.
 *
 * Verifies:
 * 1. Physical pixel-space CVA calculation relative to the true horizontal axis through C7.
 * 2. Mathematical reproduction of the 64.8° artifact when using aspect-distorted normalized coordinates.
 * 3. Validation of complementary angle with vertical (90° - CVA).
 * 4. Boundary cases (0° pure horizontal, 90° pure vertical, slouched/negative angles).
 * 5. Full landmark telemetry data preservation in PostureMetrics and Detailed CSV logger.
 */
class CvaComputationTest {

    @Test
    fun testExpected48DegPostureProducesExact48DegPixelCva() {
        // Known clinical test case: Expected CVA = 48.0°
        // In a 720 x 1280 camera frame:
        val frameWidth = 720
        val frameHeight = 1280

        // In physical pixels:
        // C7 is at (300px, 600px)
        // For CVA = 48.0°: tan(48°) = dy / dx ≈ 1.11061
        // Let dx = 100px. Then dy = 100 * tan(48°) = 111.061px.
        // Tragus is higher (smaller Y in screen coords): tragusY = 600 - 111.061 = 488.939px.
        // Tragus is forward (ear is further left in right profile or right in left profile):
        val c7XPx = 300f
        val c7YPx = 600f
        val dxPx = 100f
        val dyPx = (dxPx * tan(Math.toRadians(48.0))).toFloat() // ≈ 111.061f
        val tragusXPx = c7XPx - dxPx // 200f
        val tragusYPx = c7YPx - dyPx // ≈ 488.939f

        // Convert to MediaPipe normalized coordinates [0.0, 1.0]:
        val normTragusX = tragusXPx / frameWidth
        val normTragusY = tragusYPx / frameHeight
        val normC7X = c7XPx / frameWidth
        val normC7Y = c7YPx / frameHeight

        val metrics = PostureAnalyzer.computeMetrics(
            earX = normTragusX,
            earY = normTragusY,
            earVis = 0.99f,
            shX = normC7X,
            shY = normC7Y,
            shVis = 0.99f,
            imageWidth = frameWidth,
            imageHeight = frameHeight
        )

        // Verify computed CVA in pixel space matches 48.0° clinically
        assertEquals(48.0f, metrics.cva, 0.1f)
        assertEquals(48.0f, metrics.cvaPixelSpace, 0.1f)

        // Verify angle with vertical is 90° - 48° = 42°
        assertEquals(42.0f, metrics.angleWithVertical, 0.1f)

        // Verify that 48° is classified as Good posture
        assertTrue(metrics.isCorrect)
        assertEquals("Good", metrics.posture)

        // Verify exact pixel landmark telemetry
        assertEquals(tragusXPx, metrics.tragusXPx, 0.5f)
        assertEquals(tragusYPx, metrics.tragusYPx, 0.5f)
        assertEquals(c7XPx, metrics.c7XPx, 0.5f)
        assertEquals(c7YPx, metrics.c7YPx, 0.5f)
        assertEquals(frameWidth, metrics.imageWidth)
        assertEquals(frameHeight, metrics.imageHeight)
    }

    @Test
    fun testMathematicalProofOf64_8DegNormalizedArtifact() {
        // Demonstration of root cause:
        // When using raw normalized coordinates on an uncorrected camera buffer with aspect ratio ~ 1.913:
        // dy_norm / dx_norm = (dy_px / H) / (dx_px / W) = (dy_px / dx_px) * (W / H)
        // With tan(48°) ≈ 1.11061 and aspect multiplier 1.913:
        // tan(CVA_norm) = 1.11061 * 1.913 = 2.125
        // atan(2.125) = 64.8°!
        val expectedTrueCva = 48.0
        val tanTrue = tan(Math.toRadians(expectedTrueCva)) // 1.11061
        val distortedTan = tanTrue * 1.91307 // 2.12467
        val distortedAngleDeg = Math.toDegrees(atan2(distortedTan, 1.0))

        assertEquals(64.8, distortedAngleDeg, 0.1)

        // Verify that PostureAnalyzer.computeMetrics preserves both pixel CVA (48°)
        // and records the distorted normalized CVA so engineers can inspect both
        val frameWidth = 1000
        val frameHeight = 523 // aspect ratio W/H ≈ 1.912

        val dxPx = 100f
        val dyPx = (dxPx * tan(Math.toRadians(48.0))).toFloat()
        val c7XPx = 500f
        val c7YPx = 400f
        val tragusXPx = c7XPx - dxPx
        val tragusYPx = c7YPx - dyPx

        val metrics = PostureAnalyzer.computeMetrics(
            earX = tragusXPx / frameWidth,
            earY = tragusYPx / frameHeight,
            earVis = 0.99f,
            shX = c7XPx / frameWidth,
            shY = c7YPx / frameHeight,
            shVis = 0.99f,
            imageWidth = frameWidth,
            imageHeight = frameHeight
        )

        // Pixel CVA is accurate (48°)
        assertEquals(48.0f, metrics.cva, 0.2f)
        // Normalized CVA clearly reveals the 64.8° aspect artifact
        assertEquals(64.8f, metrics.cvaNormalizedSpace, 0.3f)
    }

    @Test
    fun testForwardHeadPostureBelow48DegThreshold() {
        // Forward head posture with true CVA = 41.5° (poor posture)
        val frameW = 1080
        val frameH = 1920

        val dxPx = 120f
        val dyPx = (dxPx * tan(Math.toRadians(41.5))).toFloat() // ≈ 106.18px

        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.40f,
            earY = 0.30f,
            earVis = 0.95f,
            shX = 0.40f + (dxPx / frameW),
            shY = 0.30f + (dyPx / frameH),
            shVis = 0.95f,
            imageWidth = frameW,
            imageHeight = frameH
        )

        assertEquals(41.5f, metrics.cva, 0.2f)
        assertFalse("Expected CVA < 48° to be classified as incorrect", metrics.isCorrect)
        assertEquals("Poor", metrics.posture)
        assertTrue(metrics.reason.contains("Sit straight"))
    }

    @Test
    fun testHorizontalAngleAndVerticalComplementaryAngle() {
        // CVA = 49.0° upright neutral
        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.50f,
            earY = 0.30f,
            earVis = 0.95f,
            shX = 0.50f + (100f / 1000f),
            shY = 0.30f + ((100f * tan(Math.toRadians(49.0))).toFloat() / 1000f),
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertEquals(49.0f, metrics.cva, 0.1f)
        // Horizontal angle = 49.0°
        assertEquals(49.0f, metrics.cvaPixelSpace, 0.1f)
        // Vertical angle = 90° - 49° = 41.0°
        assertEquals(41.0f, metrics.angleWithVertical, 0.1f)
        assertTrue("Expected 49.0° to be classified as correct within 48°–50° band", metrics.isCorrect)
        assertEquals("Good", metrics.posture)
    }

    @Test
    fun testHyperextended64_8DegClassifiedAsPoor() {
        val metrics = PostureAnalyzer.computeMetrics(
            earX = 0.40f,
            earY = 0.40f - ((100f * tan(Math.toRadians(64.8))).toFloat() / 1000f),
            earVis = 0.95f,
            shX = 0.50f,
            shY = 0.40f,
            shVis = 0.95f,
            imageWidth = 1000,
            imageHeight = 1000
        )
        assertEquals(64.8f, metrics.cva, 0.2f)
        assertFalse("64.8° must be classified as incorrect (> 50°)", metrics.isCorrect)
        assertEquals("Poor", metrics.posture)
        assertTrue(metrics.reason.contains("head tilted back"))
    }

    @Test
    fun testDetailedCsvRecordFormat() {
        val timestamp = 1758092100000L
        val isoDate = "2026-09-17T06:55:00.000Z"
        val record = CalibrationLogger.formatDetailedCsvRecord(
            timestamp = timestamp,
            isoDate = isoDate,
            rawCva = 48.0412f,
            angleWithVertical = 41.9588f,
            cvaNormalized = 64.8211f,
            tragusXNorm = 0.4123f,
            tragusYNorm = 0.3156f,
            c7XNorm = 0.5211f,
            c7YNorm = 0.4321f,
            tragusXPx = 296.8f,
            tragusYPx = 403.9f,
            c7XPx = 375.2f,
            c7YPx = 553.1f,
            imageWidth = 720,
            imageHeight = 1280,
            imageFilename = "cva_test.jpg",
            status = "CAPTURED"
        )

        assertTrue(record.startsWith("1758092100000,2026-09-17T06:55:00.000Z,48.0412,41.9588,64.8211,"))
        assertTrue(record.contains("296.8,403.9,375.2,553.1,"))
        assertTrue(record.endsWith("720,1280,cva_test.jpg,CAPTURED\n"))
    }
}
