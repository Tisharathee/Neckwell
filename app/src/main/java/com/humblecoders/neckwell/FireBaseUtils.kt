package com.humblecoders.neckwell
import com.google.firebase.auth.FirebaseAuth
import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

data class PostureData(
    val timestamp: Long? = null,
    val posture: String = ""
)

suspend fun fetchTodayPostureData(): List<PostureData> {
    val db = FirebaseFirestore.getInstance()
    Log.d("NeckWell", "Starting fetchTodayPostureData()")

    // Get start and end of today as Unix timestamps in seconds
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val startOfDay = calendar.timeInMillis / 1000 // Convert to Unix timestamp in seconds

    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    val endOfDay = calendar.timeInMillis / 1000 // Convert to Unix timestamp in seconds

    Log.d("NeckWell", "Looking for data between $startOfDay and $endOfDay")

    return try {
        // Fetch all data from Firebase (simple approach, no index needed)
        Log.d("NeckWell", "Fetching all documents from posture_data collection...")
        val snapshot = db.collection("posture_data")
            .get()
            .await()

        Log.d("NeckWell", "Fetched ${snapshot.documents.size} total documents from Firebase")
        
        // Log each document's data
        snapshot.documents.forEach { doc ->
            Log.d("NeckWell", "Document ID: ${doc.id}, Data: ${doc.data}")
        }

        // Filter on client side to get today's data
        val allData = snapshot.documents.mapNotNull { doc ->
            val timestamp = doc.getLong("timestamp")
            val posture = doc.getString("posture") ?: ""
            
            Log.d("NeckWell", "Mapped doc: timestamp=$timestamp, posture=$posture")
            
            PostureData(
                timestamp = timestamp,
                posture = posture
            )
        }
        
        Log.d("NeckWell", "Total mapped data: ${allData.size} items")
        
        val todayData = allData.filter { data ->
            val isInRange = data.timestamp != null && data.timestamp >= startOfDay && data.timestamp <= endOfDay
            if (isInRange) {
                Log.d("NeckWell", "Included: timestamp=${data.timestamp}, posture=${data.posture}")
            } else {
                Log.d("NeckWell", "Excluded: timestamp=${data.timestamp} (not in range)")
            }
            isInRange
        }.sortedBy { it.timestamp }
        
        Log.d("NeckWell", "Today's data count: ${todayData.size}")
        return todayData
    } catch (e: Exception) {
        Log.e("NeckWell", "Error fetching today's posture data", e)
        emptyList()
    }
}

suspend fun fetchLatestPosture(): PostureData? {
    val db = FirebaseFirestore.getInstance()
    Log.d("NeckWell", "Starting fetchLatestPosture()")

    return try {
        // Fetch all data from Firebase (simple approach, no index needed)
        Log.d("NeckWell", "Fetching all documents for latest posture...")
        val snapshot = db.collection("posture_data")
            .get()
            .await()

        Log.d("NeckWell", "Fetched ${snapshot.documents.size} documents")
        
        val allData = snapshot.documents.mapNotNull { doc ->
            val timestamp = doc.getLong("timestamp")
            val posture = doc.getString("posture") ?: ""
            Log.d("NeckWell", "Latest posture doc: timestamp=$timestamp, posture=$posture")
            
            PostureData(
                timestamp = timestamp,
                posture = posture
            )
        }
        
        val latest = allData.maxByOrNull { it.timestamp ?: 0L }
        Log.d("NeckWell", "Latest posture: $latest")
        return latest
    } catch (e: Exception) {
        Log.e("NeckWell", "Error fetching latest posture", e)
        null
    }
}

fun calculatePostureScore(posture: String): Int {
    return when (posture) {
        "Excellent" -> 95
        "Good" -> 85
        "Okay" -> 70
        "Poor" -> 50
        "Very poor" -> 30
        else -> 0
    }
}

fun getPostureEmoji(posture: String): String {
    return when (posture) {
        "Excellent" -> "😊"
        "Good" -> "🙂"
        "Okay" -> "😐"
        "Poor" -> "😕"
        "Very poor" -> "☹️"
        else -> "😐"
    }
}

fun calculateGoodPosturePercentage(postureDataList: List<PostureData>): Int {
    if (postureDataList.isEmpty()) return 0

    val goodPostures = postureDataList.count {
        it.posture == "Excellent" || it.posture == "Good"
    }

    return (goodPostures * 100) / postureDataList.size
}

fun countAlerts(postureDataList: List<PostureData>): Int {
    return postureDataList.count {
        it.posture == "Poor" || it.posture == "Very poor"
    }
}
suspend fun saveBaselineToFirestore(baseline: Baseline): Result<Unit> = runCatching {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
    val data = mapOf(
        "pitch" to baseline.pitch,
        "roll" to baseline.roll,
        "sampleCount" to baseline.sampleCount,
        "capturedAt" to baseline.capturedAt
    )
    FirebaseFirestore.getInstance()
        .collection("users").document(uid)
        .collection("calibration").document("baseline")
        .set(data).await()
}

fun getPostureDistribution(postureDataList: List<PostureData>): Map<String, Int> {
    if (postureDataList.isEmpty()) {
        return mapOf(
            "Excellent" to 0,
            "Good" to 0,
            "Okay" to 0,
            "Poor" to 0
        )
    }

    val total = postureDataList.size
    val excellentCount = postureDataList.count { it.posture == "Excellent" }
    val goodCount = postureDataList.count { it.posture == "Good" }
    val okayCount = postureDataList.count { it.posture == "Okay" }
    val poorCount = postureDataList.count { it.posture == "Poor" || it.posture == "Very poor" }

    return mapOf(
        "Excellent" to (excellentCount * 100) / total,
        "Good" to (goodCount * 100) / total,
        "Okay" to (okayCount * 100) / total,
        "Poor" to (poorCount * 100) / total
    )
}

@SuppressLint("DefaultLocale")
fun getHourlyPostureQuality(postureDataList: List<PostureData>): List<Pair<String, Float>> {
    val hourlyData = mutableMapOf<Int, MutableList<Int>>()

    postureDataList.forEach { data ->
        data.timestamp?.let { timestamp ->
            val calendar = Calendar.getInstance()
            // Convert Unix timestamp in seconds to milliseconds
            calendar.timeInMillis = timestamp * 1000
            val hour = calendar.get(Calendar.HOUR_OF_DAY)

            val score = calculatePostureScore(data.posture)
            if (!hourlyData.containsKey(hour)) {
                hourlyData[hour] = mutableListOf()
            }
            hourlyData[hour]?.add(score)
        }
    }

    return hourlyData.map { (hour, scores) ->
        val avgScore = scores.average().toFloat()
        val timeStr = String.format("%d:00", hour)
        timeStr to avgScore
    }.sortedBy { it.first }
}