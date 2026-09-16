package com.humblecoders.neckwell
import com.google.firebase.auth.FirebaseAuth
import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.util.*

data class PostureData(
    val timestamp: Long? = null,
    val posture: String = ""
)

/**
 * Pure function to filter posture data for a specific calendar day.
 * Extracted for deterministic testing and reuse across one-time queries and real-time listeners.
 */
fun filterTodayPostureData(allData: List<PostureData>, nowMillis: Long = System.currentTimeMillis()): List<PostureData> {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfDay = calendar.timeInMillis / 1000

    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
    val endOfDay = calendar.timeInMillis / 1000

    return allData.filter { data ->
        data.timestamp != null && data.timestamp in startOfDay..endOfDay
    }.sortedBy { it.timestamp }
}

/**
 * Calculates active tracking time in minutes from a collection of posture readings.
 * - 0 readings -> 0 minutes
 * - 1 reading -> 1 minute
 * - >= 2 readings -> difference between latest and earliest timestamp in minutes (min 1 min)
 */
fun calculateActiveMinutes(data: List<PostureData>): Int {
    val timestamps = data.mapNotNull { it.timestamp }
    if (timestamps.isEmpty()) return 0
    if (timestamps.size == 1) return 1
    return maxOf(1, ((timestamps.maxOrNull()!! - timestamps.minOrNull()!!) / 60L).toInt())
}

/**
 * Real-time Firestore snapshot listener on the 'posture_data' collection.
 * Invokes [onDataUpdated] immediately upon subscription and every time new data is added or modified.
 * Returns a [ListenerRegistration] so the caller can detach on unmount/dispose.
 */
fun listenToPostureData(
    onDataUpdated: (todayData: List<PostureData>, latest: PostureData?) -> Unit,
    onError: (Exception) -> Unit = {}
): ListenerRegistration {
    val db = FirebaseFirestore.getInstance()
    Log.d("NeckWell", "Registering real-time listener on 'posture_data' collection...")

    return db.collection("posture_data")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("NeckWell", "Error in posture_data snapshot listener", error)
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener

            val allData = snapshot.documents.mapNotNull { doc ->
                val timestamp = doc.getLong("timestamp")
                val posture = doc.getString("posture") ?: ""
                PostureData(
                    timestamp = timestamp,
                    posture = posture
                )
            }

            val todayData = filterTodayPostureData(allData)
            val latest = allData.maxByOrNull { it.timestamp ?: 0L }

            Log.d("NeckWell", "Live posture_data updated: ${todayData.size} readings today, latest=${latest?.posture}")
            onDataUpdated(todayData, latest)
        }
}

suspend fun fetchTodayPostureData(): List<PostureData> {
    val db = FirebaseFirestore.getInstance()
    Log.d("NeckWell", "Starting fetchTodayPostureData()")

    return try {
        val snapshot = db.collection("posture_data")
            .get()
            .await()

        val allData = snapshot.documents.mapNotNull { doc ->
            val timestamp = doc.getLong("timestamp")
            val posture = doc.getString("posture") ?: ""
            PostureData(
                timestamp = timestamp,
                posture = posture
            )
        }

        val todayData = filterTodayPostureData(allData)
        Log.d("NeckWell", "Today's data count: ${todayData.size}")
        todayData
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
