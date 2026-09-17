package com.humblecoders.neckwell

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CalibrationLogger {
    private const val TAG = "CalibrationValidation"
    private const val CSV_HEADER = "Timestamp,DateTime,RawCVA_Deg,LateralTilt_Deg,ImageFilename,Status\n"

    /**
     * Pure function to format a CSV record line for clinical comparison and automated testing.
     */
    fun formatCsvRecord(
        timestamp: Long,
        isoDate: String,
        rawCva: Float,
        lateralTilt: Float,
        imageFilename: String,
        status: String = "CAPTURED"
    ): String {
        return "$timestamp,$isoDate,${"%.4f".format(Locale.US, rawCva)},${"%.4f".format(Locale.US, lateralTilt)},$imageFilename,$status\n"
    }

    /**
     * Formats a timestamp into an ISO-8601 UTC date string.
     */
    fun formatIsoDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestamp))
    }

    /**
     * Resolves the directory where calibration images and CSV validation logs are stored.
     */
    fun getCapturesDirectory(context: Context): File {
        val externalDir = context.getExternalFilesDir(null)
        val targetDir = if (externalDir != null) {
            File(externalDir, "calibration_captures")
        } else {
            File(context.filesDir, "calibration_captures")
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
    }

    /**
     * Saves the captured frame image and logs the raw CVA telemetry to disk and Firebase.
     */
    fun saveCalibrationCapture(
        context: Context,
        bitmap: Bitmap?,
        rawCva: Float,
        lateralTilt: Float,
        timestamp: Long = System.currentTimeMillis()
    ): File? {
        val dir = getCapturesDirectory(context)
        val cvaFormatted = String.format(Locale.US, "%.2f", rawCva)
        val filename = "cva_${timestamp}_${cvaFormatted}deg.jpg"
        val imageFile = File(dir, filename)

        // 1. Save Image Bitmap
        if (bitmap != null) {
            try {
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                }
                Log.i(TAG, "Saved calibration frame image to ${imageFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save calibration image to disk: ${e.message}", e)
            }
        }

        // 2. Append to CSV validation log
        try {
            val logFile = File(dir, "calibration_validation_log.csv")
            val isNewFile = !logFile.exists() || logFile.length() == 0L
            val isoDate = formatIsoDate(timestamp)
            val record = formatCsvRecord(
                timestamp = timestamp,
                isoDate = isoDate,
                rawCva = rawCva,
                lateralTilt = lateralTilt,
                imageFilename = if (imageFile.exists()) imageFile.name else "NONE",
                status = "CAPTURED"
            )

            FileOutputStream(logFile, true).use { fos ->
                if (isNewFile) {
                    fos.write(CSV_HEADER.toByteArray())
                }
                fos.write(record.toByteArray())
                fos.flush()
            }
            Log.i(TAG, "Appended clinical validation entry: $record")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write calibration CSV log: ${e.message}", e)
        }

        // 3. Clinical Logcat output for real-time validation via adb logcat
        Log.i(
            TAG,
            "CLINICAL_VALIDATION_CAPTURE: timestamp=$timestamp, rawCva=${"%.4f".format(Locale.US, rawCva)}°, lateralTilt=${"%.4f".format(Locale.US, lateralTilt)}°, image=${imageFile.name}"
        )

        // 4. Remote telemetry to Firebase Firestore (best effort)
        try {
            val firestoreData = hashMapOf(
                "timestamp" to timestamp,
                "rawCva" to rawCva,
                "lateralTilt" to lateralTilt,
                "imageFilename" to imageFile.name,
                "capturedAt" to formatIsoDate(timestamp)
            )
            Firebase.firestore
                .collection("calibration_captures")
                .document("capture_$timestamp")
                .set(firestoreData)
        } catch (e: Exception) {
            Log.d(TAG, "Remote Firestore capture logging skipped: ${e.message}")
        }

        return if (imageFile.exists()) imageFile else null
    }
}
