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
    private const val CSV_HEADER = "Timestamp,DateTime,RawCVA_Deg,AngleWithVertical_Deg,CVA_Normalized_Deg,TragusX_Norm,TragusY_Norm,C7X_Norm,C7Y_Norm,TragusX_Px,TragusY_Px,C7X_Px,C7Y_Px,RawEarX_Px,RawEarY_Px,RefTragusX_Px,RefTragusY_Px,TragusError_Px,RefC7X_Px,RefC7Y_Px,C7Error_Px,ImageWidth,ImageHeight,ImageFilename,Status\n"

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
        rawEarXPx: Float = tragusXPx,
        rawEarYPx: Float = tragusYPx,
        refTragusXPx: Float? = null,
        refTragusYPx: Float? = null,
        tragusErrorPx: Float? = null,
        refC7XPx: Float? = null,
        refC7YPx: Float? = null,
        c7ErrorPx: Float? = null,
        imageWidth: Int,
        imageHeight: Int,
        imageFilename: String,
        status: String = "CAPTURED"
    ): String {
        val refTStr = if (refTragusXPx != null && refTragusYPx != null) "${"%.1f".format(Locale.US, refTragusXPx)},${"%.1f".format(Locale.US, refTragusYPx)},${"%.1f".format(Locale.US, tragusErrorPx ?: 0f)}" else "-,-,-"
        val refCStr = if (refC7XPx != null && refC7YPx != null) "${"%.1f".format(Locale.US, refC7XPx)},${"%.1f".format(Locale.US, refC7YPx)},${"%.1f".format(Locale.US, c7ErrorPx ?: 0f)}" else "-,-,-"

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
            "${"%.1f".format(Locale.US, rawEarXPx)}," +
            "${"%.1f".format(Locale.US, rawEarYPx)}," +
            "$refTStr,$refCStr," +
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
        timestamp: Long = System.currentTimeMillis(),
        clinicalRef: ClinicalReference? = null
    ): File? {
        val dir = getCapturesDirectory(context)
        val cvaFormatted = String.format(Locale.US, "%.2f", metrics.cva)
        val filename = "cva_${timestamp}_${cvaFormatted}deg.jpg"
        val imageFile = File(dir, filename)

        val errorMetrics = clinicalRef?.let { PostureAnalyzer.computeLandmarkErrors(metrics, it) }

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
                val rawEX = metrics.rawEarXNorm * imgW
                val rawEY = metrics.rawEarYNorm * imgH

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

                // B. Offset path from shoulder midpoint up & back to C7 neck base
                if (sY > cY + 2f || Math.abs(sX - cX) > 2f) {
                    canvas.drawLine(sX, sY, cX, cY, paintShoulderOffset)
                }

                // C. Raw Ear -> Derived Tragus correction vector
                if (Math.abs(rawEX - tX) > 2f || Math.abs(rawEY - tY) > 2f) {
                    val paintEarCorrection = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = maxOf(2f, imgW / 300f)
                        color = android.graphics.Color.CYAN
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f)
                    }
                    canvas.drawLine(rawEX, rawEY, tX, tY, paintEarCorrection)
                }

                // D. C7 to Tragus vector line
                canvas.drawLine(cX, cY, tX, tY, paintLine)

                // E. Shoulder Midpoint (Orange circle)
                val dotRadius = maxOf(10f, imgW / 55f)
                if (sY > cY + 2f || Math.abs(sX - cX) > 2f) {
                    paintCircle.color = android.graphics.Color.rgb(255, 165, 0)
                    canvas.drawCircle(sX, sY, dotRadius * 0.75f, paintCircle)
                    paintText.color = android.graphics.Color.rgb(255, 180, 50)
                    canvas.drawText("Shoulder Midpoint (${"%.1f".format(metrics.shoulderXPx)}, ${"%.1f".format(metrics.shoulderYPx)})", sX + dotRadius + 8f, sY + dotRadius, paintText)
                }

                // F. Tragus Landmark (Cyan circle)
                paintCircle.color = android.graphics.Color.CYAN
                paintText.color = android.graphics.Color.CYAN
                canvas.drawCircle(tX, tY, dotRadius, paintCircle)
                canvas.drawText("Tragus (${"%.1f".format(metrics.tragusXPx)}, ${"%.1f".format(metrics.tragusYPx)})", tX + dotRadius + 8f, tY + dotRadius, paintText)

                // G. C7 / Neck Base Landmark (Yellow circle)
                paintCircle.color = android.graphics.Color.YELLOW
                paintText.color = android.graphics.Color.YELLOW
                canvas.drawCircle(cX, cY, dotRadius, paintCircle)
                canvas.drawText("C7 Neck Base (${"%.1f".format(metrics.c7XPx)}, ${"%.1f".format(metrics.c7YPx)})", cX + dotRadius + 8f, cY - dotRadius / 2f, paintText)

                // H. Clinical Reference Overlays (when active)
                if (clinicalRef != null) {
                    val refTx = (clinicalRef.tragusXPx / metrics.imageWidth) * imgW
                    val refTy = (clinicalRef.tragusYPx / metrics.imageHeight) * imgH
                    val refCx = (clinicalRef.c7XPx / metrics.imageWidth) * imgW
                    val refCy = (clinicalRef.c7YPx / metrics.imageHeight) * imgH

                    val paintRefLine = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = maxOf(3f, imgW / 180f)
                        color = android.graphics.Color.MAGENTA
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(14f, 8f), 0f)
                    }
                    canvas.drawLine(refCx, refCy, refTx, refTy, paintRefLine)

                    // Error lines
                    val paintErrLine = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 2f
                        color = android.graphics.Color.MAGENTA
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 6f), 0f)
                    }
                    canvas.drawLine(tX, tY, refTx, refTy, paintErrLine)
                    canvas.drawLine(cX, cY, refCx, refCy, paintErrLine)

                    paintCircle.color = android.graphics.Color.MAGENTA
                    canvas.drawCircle(refTx, refTy, dotRadius * 0.8f, paintCircle)
                    paintText.color = android.graphics.Color.MAGENTA
                    canvas.drawText("Ref Tragus (Δ${"%.1f".format(errorMetrics?.tragusPixelError ?: 0f)}px)", refTx + dotRadius + 6f, refTy + dotRadius, paintText)

                    paintCircle.color = android.graphics.Color.rgb(255, 64, 129)
                    canvas.drawCircle(refCx, refCy, dotRadius * 0.8f, paintCircle)
                    paintText.color = android.graphics.Color.rgb(255, 64, 129)
                    canvas.drawText("Ref C7 (Δ${"%.1f".format(errorMetrics?.c7PixelError ?: 0f)}px)", refCx + dotRadius + 6f, refCy - dotRadius / 2f, paintText)
                }

                // I. Header HUD banner
                paintText.color = android.graphics.Color.WHITE
                val bannerPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(190, 10, 20, 20)
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, imgW, paintText.textSize * 3.8f, bannerPaint)
                canvas.drawText("CVA: ${"%.1f".format(metrics.cva)}°  |  Target: [${PostureAnalyzer.CVA_MIN.toInt()}°–${PostureAnalyzer.CVA_MAX.toInt()}°]  |  Anatomical Tragus & C7", 20f, paintText.textSize * 1.3f, paintText)
                val bannerLine2 = if (errorMetrics != null) {
                    "Tragus: (${metrics.tragusXPx.toInt()}, ${metrics.tragusYPx.toInt()}) [Δ${"%.1f".format(errorMetrics.tragusPixelError)}px]  |  C7: (${metrics.c7XPx.toInt()}, ${metrics.c7YPx.toInt()}) [Δ${"%.1f".format(errorMetrics.c7PixelError)}px]"
                } else {
                    "Tragus: (${metrics.tragusXPx.toInt()}, ${metrics.tragusYPx.toInt()})  |  C7: (${metrics.c7XPx.toInt()}, ${metrics.c7YPx.toInt()})  |  ShoulderMid: (${metrics.shoulderXPx.toInt()}, ${metrics.shoulderYPx.toInt()})"
                }
                canvas.drawText(bannerLine2, 20f, paintText.textSize * 2.7f, paintText)

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
                rawEarXPx = metrics.rawEarXPx,
                rawEarYPx = metrics.rawEarYPx,
                refTragusXPx = clinicalRef?.tragusXPx,
                refTragusYPx = clinicalRef?.tragusYPx,
                tragusErrorPx = errorMetrics?.tragusPixelError,
                refC7XPx = clinicalRef?.c7XPx,
                refC7YPx = clinicalRef?.c7YPx,
                c7ErrorPx = errorMetrics?.c7PixelError,
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

    /**
     * High-speed continuous frame logger for logcat inspection during live tracking.
     */
    fun logLiveTrackingFrame(metrics: PostureMetrics, frameIndex: Long) {
        Log.i(
            "CVA_LIVE_FRAME",
            "LIVE_FRAME #$frameIndex | " +
                "CVA=${"%.2f".format(Locale.US, metrics.cva)}° (rawInstant=${"%.2f".format(Locale.US, metrics.rawInstantaneousCva)}°, stdDev=±${"%.2f".format(Locale.US, metrics.cvaStdDev)}°) | " +
                "Tragus=(${"%.1f".format(Locale.US, metrics.tragusXPx)}, ${"%.1f".format(Locale.US, metrics.tragusYPx)}) | " +
                "C7=(${"%.1f".format(Locale.US, metrics.c7XPx)}, ${"%.1f".format(Locale.US, metrics.c7YPx)}) | " +
                "jitter=[T:${"%.1f".format(Locale.US, metrics.jitterTragusPx)}px, C7:${"%.1f".format(Locale.US, metrics.jitterC7Px)}px] | " +
                "Posture=${metrics.posture} (isCorrect=${metrics.isCorrect})"
        )
    }

    /**
     * Session summary data model for continuous live tracking recordings.
     */
    data class LiveSessionSummary(
        val sessionFile: File,
        val totalFrames: Int,
        val durationSec: Float,
        val meanCva: Float,
        val cvaStdDev: Float,
        val minCva: Float,
        val maxCva: Float,
        val goodPosturePercentage: Float,
        val avgTragusJitterPx: Float,
        val avgC7JitterPx: Float
    )

    /**
     * Continuous 10–15 second live session CSV recorder.
     */
    object LiveSessionRecorder {
        private const val LIVE_CSV_HEADER = "FrameIndex,Timestamp,ElapsedSec,CVA_Deg,RawInstantCVA_Deg,CVA_StdDev,TragusX_Px,TragusY_Px,C7X_Px,C7Y_Px,JitterTragus_Px,JitterC7_Px,Posture,IsCorrect\n"

        private var activeSessionFile: File? = null
        private var sessionStartTime: Long = 0L
        private var frameCount: Int = 0
        private val cvaList = mutableListOf<Float>()
        private var goodCount: Int = 0
        private var sumJitterTragus = 0.0
        private var sumJitterC7 = 0.0
        private var isRecordingSession = false

        @Synchronized
        fun isRecording(): Boolean = isRecordingSession

        @Synchronized
        fun startSession(context: Context): File {
            val dir = getCapturesDirectory(context)
            val timestamp = System.currentTimeMillis()
            val file = File(dir, "live_session_${timestamp}.csv")
            FileOutputStream(file, false).use { fos ->
                fos.write(LIVE_CSV_HEADER.toByteArray())
                fos.flush()
            }
            activeSessionFile = file
            sessionStartTime = timestamp
            frameCount = 0
            cvaList.clear()
            goodCount = 0
            sumJitterTragus = 0.0
            sumJitterC7 = 0.0
            isRecordingSession = true
            Log.i(TAG, "Started live tracking session recording: ${file.absolutePath}")
            return file
        }

        @Synchronized
        fun recordFrame(metrics: PostureMetrics) {
            if (!isRecordingSession) return
            val file = activeSessionFile ?: return
            val now = System.currentTimeMillis()
            val elapsedSec = (now - sessionStartTime) / 1000f
            frameCount++
            cvaList.add(metrics.cva)
            if (metrics.isCorrect) goodCount++
            sumJitterTragus += metrics.jitterTragusPx
            sumJitterC7 += metrics.jitterC7Px

            val record = String.format(
                Locale.US,
                "%d,%d,%.3f,%.2f,%.2f,%.2f,%.1f,%.1f,%.1f,%.1f,%.2f,%.2f,%s,%b\n",
                frameCount,
                now,
                elapsedSec,
                metrics.cva,
                metrics.rawInstantaneousCva,
                metrics.cvaStdDev,
                metrics.tragusXPx,
                metrics.tragusYPx,
                metrics.c7XPx,
                metrics.c7YPx,
                metrics.jitterTragusPx,
                metrics.jitterC7Px,
                metrics.posture,
                metrics.isCorrect
            )

            try {
                FileOutputStream(file, true).use { fos ->
                    fos.write(record.toByteArray())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing frame to live session CSV: ${e.message}")
            }
        }

        @Synchronized
        fun stopSession(): LiveSessionSummary? {
            if (!isRecordingSession) return null
            isRecordingSession = false
            val file = activeSessionFile ?: return null
            val now = System.currentTimeMillis()
            val duration = maxOf(0.001f, (now - sessionStartTime) / 1000f)
            val n = frameCount
            if (n == 0) return null

            val mean = cvaList.average().toFloat()
            val variance = cvaList.map { (it - mean) * (it - mean) }.average()
            val stdDev = kotlin.math.sqrt(variance).toFloat()
            val min = cvaList.minOrNull() ?: 0f
            val max = cvaList.maxOrNull() ?: 0f
            val pctGood = (goodCount.toFloat() / n) * 100f
            val avgJTragus = (sumJitterTragus / n).toFloat()
            val avgJC7 = (sumJitterC7 / n).toFloat()

            val summary = LiveSessionSummary(
                sessionFile = file,
                totalFrames = n,
                durationSec = duration,
                meanCva = mean,
                cvaStdDev = stdDev,
                minCva = min,
                maxCva = max,
                goodPosturePercentage = pctGood,
                avgTragusJitterPx = avgJTragus,
                avgC7JitterPx = avgJC7
            )

            Log.i(
                TAG,
                "Stopped live tracking session: frames=$n, duration=${"%.1f".format(duration)}s, " +
                    "meanCVA=${"%.1f".format(mean)}° ± ${"%.2f".format(stdDev)}°, goodPct=${"%.1f".format(pctGood)}%, " +
                    "avgJitter=[T:${"%.2f".format(avgJTragus)}px, C7:${"%.2f".format(avgJC7)}px], file=${file.name}"
            )
            return summary
        }
    }
}
