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
    private const val CSV_HEADER = "Timestamp,DateTime,RawCVA_Deg,AngleWithVertical_Deg,CVA_Normalized_Deg,TragusX_Norm,TragusY_Norm,C7X_Norm,C7Y_Norm,TragusX_Px,TragusY_Px,C7X_Px,C7Y_Px,ImageWidth,ImageHeight,ImageFilename,Status\n"

    /**
     * Formats a comprehensive CSV record line with raw landmark coordinates for clinical comparison.
     */
    fun formatDetailedCsvRecord(
        timestamp: Long,
        isoDate: String,
        rawCva: Float,
        angleWithVertical: Float,
        cvaNormalized: Float,
        tragusXNorm: Float,
        tragusYNorm: Float,
        c7XNorm: Float,
        c7YNorm: Float,
        tragusXPx: Float,
        tragusYPx: Float,
        c7XPx: Float,
        c7YPx: Float,
        imageWidth: Int,
        imageHeight: Int,
        imageFilename: String,
        status: String = "CAPTURED"
    ): String {
        return "$timestamp,$isoDate," +
            "${"%.4f".format(Locale.US, rawCva)}," +
            "${"%.4f".format(Locale.US, angleWithVertical)}," +
            "${"%.4f".format(Locale.US, cvaNormalized)}," +
            "${"%.4f".format(Locale.US, tragusXNorm)}," +
            "${"%.4f".format(Locale.US, tragusYNorm)}," +
            "${"%.4f".format(Locale.US, c7XNorm)}," +
            "${"%.4f".format(Locale.US, c7YNorm)}," +
            "${"%.1f".format(Locale.US, tragusXPx)}," +
            "${"%.1f".format(Locale.US, tragusYPx)}," +
            "${"%.1f".format(Locale.US, c7XPx)}," +
            "${"%.1f".format(Locale.US, c7YPx)}," +
            "$imageWidth,$imageHeight,$imageFilename,$status\n"
    }

    /**
     * Backward-compatible simple CSV record formatter.
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
     * Saves the captured frame image with visual landmark debug annotations, and logs full telemetry.
     */
    fun saveCalibrationCapture(
        context: Context,
        bitmap: Bitmap?,
        metrics: PostureMetrics,
        timestamp: Long = System.currentTimeMillis()
    ): File? {
        val dir = getCapturesDirectory(context)
        val cvaFormatted = String.format(Locale.US, "%.2f", metrics.cva)
        val filename = "cva_${timestamp}_${cvaFormatted}deg.jpg"
        val imageFile = File(dir, filename)

        // 1. Draw Visual Debug Overlay onto Captured Image Bitmap
        if (bitmap != null) {
            try {
                val annotatedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = android.graphics.Canvas(annotatedBitmap)
                val imgW = annotatedBitmap.width.toFloat()
                val imgH = annotatedBitmap.height.toFloat()

                val tX = metrics.tragusXNorm * imgW
                val tY = metrics.tragusYNorm * imgH
                val cX = metrics.c7XNorm * imgW
                val cY = metrics.c7YNorm * imgH
                val sX = metrics.shoulderXNorm * imgW
                val sY = metrics.shoulderYNorm * imgH

                val paintCircle = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.FILL
                }
                val paintLine = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = maxOf(4f, imgW / 160f)
                    color = android.graphics.Color.rgb(78, 225, 160) // Mint green
                }
                val paintDashed = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = maxOf(3f, imgW / 200f)
                    color = android.graphics.Color.WHITE
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 15f), 0f)
                }
                val paintShoulderOffset = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = maxOf(2.5f, imgW / 240f)
                    color = android.graphics.Color.rgb(255, 165, 0) // Orange
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 10f), 0f)
                }
                val paintText = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.WHITE
                    textSize = maxOf(22f, imgW / 28f)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                }

                // A. Horizontal reference line through C7
                canvas.drawLine(0f, cY, imgW, cY, paintDashed)

                // B. Vertical offset from shoulder joint up to C7 neck base
                if (sY > cY + 2f) {
                    canvas.drawLine(sX, sY, cX, cY, paintShoulderOffset)
                }

                // C. C7 to Tragus vector line
                canvas.drawLine(cX, cY, tX, tY, paintLine)

                // D. Shoulder Joint (Orange circle)
                val dotRadius = maxOf(10f, imgW / 55f)
                if (sY > cY + 2f) {
                    paintCircle.color = android.graphics.Color.rgb(255, 165, 0)
                    canvas.drawCircle(sX, sY, dotRadius * 0.75f, paintCircle)
                    paintText.color = android.graphics.Color.rgb(255, 180, 50)
                    canvas.drawText("Shoulder Joint (${"%.1f".format(metrics.shoulderXPx)}, ${"%.1f".format(metrics.shoulderYPx)})", sX + dotRadius + 8f, sY + dotRadius, paintText)
                }

                // E. Tragus Landmark (Cyan circle)
                paintCircle.color = android.graphics.Color.CYAN
                paintText.color = android.graphics.Color.CYAN
                canvas.drawCircle(tX, tY, dotRadius, paintCircle)
                canvas.drawText("Tragus (${"%.1f".format(metrics.tragusXPx)}, ${"%.1f".format(metrics.tragusYPx)})", tX + dotRadius + 8f, tY + dotRadius, paintText)

                // F. C7 / Neck Base Landmark (Yellow circle)
                paintCircle.color = android.graphics.Color.YELLOW
                paintText.color = android.graphics.Color.YELLOW
                canvas.drawCircle(cX, cY, dotRadius, paintCircle)
                canvas.drawText("C7 Neck Base (${"%.1f".format(metrics.c7XPx)}, ${"%.1f".format(metrics.c7YPx)})", cX + dotRadius + 8f, cY - dotRadius / 2f, paintText)

                // G. Header HUD banner
                paintText.color = android.graphics.Color.WHITE
                val bannerPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(190, 10, 20, 20)
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, imgW, paintText.textSize * 3.8f, bannerPaint)
                canvas.drawText("CVA: ${"%.1f".format(metrics.cva)}°  |  Target: [${PostureAnalyzer.CVA_MIN.toInt()}°–${PostureAnalyzer.CVA_MAX.toInt()}°]  |  C7 Derived (+20% Neck Base)", 20f, paintText.textSize * 1.3f, paintText)
                canvas.drawText("Tragus: (${metrics.tragusXPx.toInt()}, ${metrics.tragusYPx.toInt()})  |  C7: (${metrics.c7XPx.toInt()}, ${metrics.c7YPx.toInt()})  |  Shoulder: (${metrics.shoulderXPx.toInt()}, ${metrics.shoulderYPx.toInt()})", 20f, paintText.textSize * 2.7f, paintText)

                FileOutputStream(imageFile).use { out ->
                    annotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    out.flush()
                }
                Log.i(TAG, "Saved annotated calibration frame image to ${imageFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save annotated calibration image: ${e.message}", e)
            }
        }

        // 2. Append to CSV validation log
        try {
            val logFile = File(dir, "calibration_validation_log.csv")
            val isNewFile = !logFile.exists() || logFile.length() == 0L
            val isoDate = formatIsoDate(timestamp)
            val record = formatDetailedCsvRecord(
                timestamp = timestamp,
                isoDate = isoDate,
                rawCva = metrics.cva,
                angleWithVertical = metrics.angleWithVertical,
                cvaNormalized = metrics.cvaNormalizedSpace,
                tragusXNorm = metrics.tragusXNorm,
                tragusYNorm = metrics.tragusYNorm,
                c7XNorm = metrics.c7XNorm,
                c7YNorm = metrics.c7YNorm,
                tragusXPx = metrics.tragusXPx,
                tragusYPx = metrics.tragusYPx,
                c7XPx = metrics.c7XPx,
                c7YPx = metrics.c7YPx,
                imageWidth = metrics.imageWidth,
                imageHeight = metrics.imageHeight,
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
            Log.i(TAG, "Appended detailed clinical validation entry: $record")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write calibration CSV log: ${e.message}", e)
        }

        // 3. Clinical Logcat output for real-time validation via adb logcat
        Log.i(
            TAG,
            "CLINICAL_VALIDATION_CAPTURE: timestamp=$timestamp | " +
                "CVA(pixel)=${"%.2f".format(Locale.US, metrics.cva)}° | " +
                "CVA(norm)=${"%.2f".format(Locale.US, metrics.cvaNormalizedSpace)}° | " +
                "VertAngle=${"%.2f".format(Locale.US, metrics.angleWithVertical)}° | " +
                "Tragus=(${"%.1f".format(Locale.US, metrics.tragusXPx)}, ${"%.1f".format(Locale.US, metrics.tragusYPx)}) | " +
                "C7=(${"%.1f".format(Locale.US, metrics.c7XPx)}, ${"%.1f".format(Locale.US, metrics.c7YPx)}) | " +
                "Frame=${metrics.imageWidth}x${metrics.imageHeight} | " +
                "image=${imageFile.name}"
        )

        // 4. Remote telemetry to Firebase Firestore (best effort)
        try {
            val firestoreData = hashMapOf(
                "timestamp" to timestamp,
                "rawCva" to metrics.cva,
                "angleWithVertical" to metrics.angleWithVertical,
                "cvaNormalizedSpace" to metrics.cvaNormalizedSpace,
                "tragusX" to metrics.tragusXPx,
                "tragusY" to metrics.tragusYPx,
                "c7X" to metrics.c7XPx,
                "c7Y" to metrics.c7YPx,
                "imageWidth" to metrics.imageWidth,
                "imageHeight" to metrics.imageHeight,
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

    /**
     * Backward-compatible overload accepting rawCva & lateralTilt.
     */
    fun saveCalibrationCapture(
        context: Context,
        bitmap: Bitmap?,
        rawCva: Float,
        lateralTilt: Float,
        timestamp: Long = System.currentTimeMillis()
    ): File? {
        val dummyMetrics = PostureMetrics(
            cva = rawCva,
            lateralTilt = lateralTilt,
            shoulderAlignment = 0f,
            isCorrect = true,
            reason = "Calibration Capture"
        )
        return saveCalibrationCapture(context, bitmap, dummyMetrics, timestamp)
    }
}
