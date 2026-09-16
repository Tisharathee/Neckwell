package com.humblecoders.neckwell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class PostureDataAggregationTest {

    @Test
    fun testFilterTodayPostureData() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 16, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetDayMillis = calendar.timeInMillis
        val noonTodaySec = targetDayMillis / 1000

        // Morning today (08:30)
        calendar.set(Calendar.HOUR_OF_DAY, 8)
        calendar.set(Calendar.MINUTE, 30)
        val morningSec = calendar.timeInMillis / 1000

        // Night today (23:15)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 15)
        val nightSec = calendar.timeInMillis / 1000

        // Yesterday (23:59:50)
        val yesterdaySec = morningSec - (24 * 3600)

        // Tomorrow (01:00:00)
        val tomorrowSec = nightSec + (2 * 3600)

        val sampleData = listOf(
            PostureData(timestamp = yesterdaySec, posture = "Good"),
            PostureData(timestamp = morningSec, posture = "Excellent"),
            PostureData(timestamp = noonTodaySec, posture = "Poor"),
            PostureData(timestamp = null, posture = "Okay"),
            PostureData(timestamp = nightSec, posture = "Good"),
            PostureData(timestamp = tomorrowSec, posture = "Poor")
        )

        val filtered = filterTodayPostureData(sampleData, nowMillis = targetDayMillis)

        assertEquals(3, filtered.size)
        assertEquals("Excellent", filtered[0].posture)
        assertEquals(morningSec, filtered[0].timestamp)
        assertEquals("Poor", filtered[1].posture)
        assertEquals(noonTodaySec, filtered[1].timestamp)
        assertEquals("Good", filtered[2].posture)
        assertEquals(nightSec, filtered[2].timestamp)
    }

    @Test
    fun testCalculateActiveMinutes() {
        // 0 readings -> 0 minutes
        assertEquals(0, calculateActiveMinutes(emptyList()))

        // 1 reading -> 1 minute active
        val singleReading = listOf(PostureData(timestamp = 1758000000L, posture = "Good"))
        assertEquals(1, calculateActiveMinutes(singleReading))

        // Multiple readings with 30-second interval -> clamped to min 1 minute
        val shortSpan = listOf(
            PostureData(timestamp = 1758000000L, posture = "Good"),
            PostureData(timestamp = 1758000030L, posture = "Good")
        )
        assertEquals(1, calculateActiveMinutes(shortSpan))

        // Multiple readings over 45 minutes
        val fortyFiveMinutes = listOf(
            PostureData(timestamp = 1758000000L, posture = "Good"),
            PostureData(timestamp = 1758001000L, posture = "Poor"),
            PostureData(timestamp = 1758000000L + (45 * 60), posture = "Excellent")
        )
        assertEquals(45, calculateActiveMinutes(fortyFiveMinutes))

        // Multiple readings over 2 hours (120 minutes)
        val twoHours = listOf(
            PostureData(timestamp = 1758000000L, posture = "Good"),
            PostureData(timestamp = 1758000000L + (120 * 60), posture = "Good")
        )
        assertEquals(120, calculateActiveMinutes(twoHours))
    }

    @Test
    fun testReadingsTodayIncrementalCount() {
        val baseTime = 1758000000L
        val dataList = mutableListOf<PostureData>()

        assertEquals(0, dataList.size)

        // Reading 1 arrives via live listener
        dataList.add(PostureData(timestamp = baseTime, posture = "Good"))
        assertEquals(1, dataList.size)
        assertEquals(1, calculateActiveMinutes(dataList))

        // Reading 2 arrives 10 minutes later
        dataList.add(PostureData(timestamp = baseTime + 600, posture = "Poor"))
        assertEquals(2, dataList.size)
        assertEquals(10, calculateActiveMinutes(dataList))

        // Reading 3 arrives 20 minutes after reading 1
        dataList.add(PostureData(timestamp = baseTime + 1200, posture = "Excellent"))
        assertEquals(3, dataList.size)
        assertEquals(20, calculateActiveMinutes(dataList))
    }

    @Test
    fun testLatestPostureExtraction() {
        val emptyList = emptyList<PostureData>()
        assertNull(emptyList.maxByOrNull { it.timestamp ?: 0L })

        val readings = listOf(
            PostureData(timestamp = 1000L, posture = "Good"),
            PostureData(timestamp = 3000L, posture = "Poor"),
            PostureData(timestamp = 2000L, posture = "Okay")
        )

        val latest = readings.maxByOrNull { it.timestamp ?: 0L }
        assertEquals("Poor", latest?.posture)
        assertEquals(3000L, latest?.timestamp)
    }

    @Test
    fun testGoodPosturePercentageAndAlertCountRecalculation() {
        val readings = listOf(
            PostureData(timestamp = 1000L, posture = "Excellent"),
            PostureData(timestamp = 2000L, posture = "Good"),
            PostureData(timestamp = 3000L, posture = "Okay"),
            PostureData(timestamp = 4000L, posture = "Poor"),
            PostureData(timestamp = 5000L, posture = "Very poor")
        )

        // Good postures: Excellent, Good -> 2 out of 5 = 40%
        val goodPct = calculateGoodPosturePercentage(readings)
        assertEquals(40, goodPct)

        // Alert postures: Poor, Very poor -> 2 alerts
        val alerts = countAlerts(readings)
        assertEquals(2, alerts)

        // Dynamic update: Add a new Good posture reading
        val updatedReadings = readings + PostureData(timestamp = 6000L, posture = "Good")
        // Now 3 out of 6 = 50%
        assertEquals(50, calculateGoodPosturePercentage(updatedReadings))
        // Alerts remain 2
        assertEquals(2, countAlerts(updatedReadings))
    }
}
