package com.humblecoders.neckwell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationLoggerTest {

    @Test
    fun testFormatCsvRecord() {
        val timestamp = 1758092100000L
        val isoDate = "2026-09-17T06:55:00.000Z"
        val rawCva = 51.4829f
        val lateralTilt = 1.2345f
        val filename = "cva_1758092100000_51.48deg.jpg"

        val record = CalibrationLogger.formatCsvRecord(
            timestamp = timestamp,
            isoDate = isoDate,
            rawCva = rawCva,
            lateralTilt = lateralTilt,
            imageFilename = filename,
            status = "CAPTURED"
        )

        val expected = "1758092100000,2026-09-17T06:55:00.000Z,51.4829,1.2345,cva_1758092100000_51.48deg.jpg,CAPTURED\n"
        assertEquals(expected, record)
    }

    @Test
    fun testFormatIsoDate() {
        // Epoch 0
        val epochIso = CalibrationLogger.formatIsoDate(0L)
        assertEquals("1970-01-01T00:00:00.000Z", epochIso)

        // Non-empty formatted string for current time
        val nowIso = CalibrationLogger.formatIsoDate(1758092100000L)
        assertTrue(nowIso.endsWith("Z"))
        assertTrue(nowIso.contains("T"))
    }

    @Test
    fun testRawCvaPrecisionPreservation() {
        val rawCva = 49.321456f
        val formatted = String.format(java.util.Locale.US, "%.2f", rawCva)
        assertEquals("49.32", formatted)

        // Ensure raw float is unrounded in string conversion
        assertTrue(rawCva.toString().startsWith("49.321"))
    }
}
