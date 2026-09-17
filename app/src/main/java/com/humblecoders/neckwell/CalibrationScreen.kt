package com.humblecoders.neckwell

import android.Manifest
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import java.util.concurrent.Executors
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

private const val REQUIRED_STABLE_FRAMES = 30  // ~1 sec at 30fps

private fun playBeepTone(toneType: Int = ToneGenerator.TONE_PROP_BEEP, durationMs: Int = 250, context: android.content.Context? = null) {
    AlertManager.playToneAlert(context, toneType, durationMs)
}

@Composable
fun CalibrationScreen(navController: NavController) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPermission = it }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Camera posture detection state
    var statusText by remember { mutableStateOf("Turn sideways to the camera and sit straight") }
    var cvaValue by remember { mutableFloatStateOf(0f) }
    var lateralTiltValue by remember { mutableFloatStateOf(0f) }
    var stableFrames by remember { mutableIntStateOf(0) }
    var calibrationTriggered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var capturedBaseline by remember { mutableStateOf<Baseline?>(null) }
    var capturedRawCva by remember { mutableFloatStateOf(0f) }
    var capturedLateralTilt by remember { mutableFloatStateOf(0f) }
    var capturedImageFile by remember { mutableStateOf<java.io.File?>(null) }
    var liveMetrics by remember { mutableStateOf<PostureMetrics?>(null) }
    var capturedMetrics by remember { mutableStateOf<PostureMetrics?>(null) }
    var showTelemetryDetails by remember { mutableStateOf(false) }

    // ESP32 / Firebase sensor state
    var espStatus by remember { mutableStateOf("Idle") }
    var espProgress by remember { mutableIntStateOf(0) }
    var espPitch by remember { mutableFloatStateOf(0f) }
    var espRoll by remember { mutableFloatStateOf(0f) }
    var espBaselineSaved by remember { mutableStateOf(false) }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showFlash by remember { mutableStateOf(false) }

    // Listen to ESP32 calibration document in real time
    LaunchedEffect(Unit) {
        Firebase.firestore
            .collection("esp32")
            .document("calibration")
            .addSnapshotListener { snapshot, error ->
                if (snapshot == null || error != null) return@addSnapshotListener

                val newStatus = snapshot.getString("status") ?: "Idle"
                val newProgress = snapshot.getLong("progress")?.toInt() ?: 0
                val newPitch = snapshot.getDouble("pitch")?.toFloat() ?: 0f
                val newRoll = snapshot.getDouble("roll")?.toFloat() ?: 0f

                espStatus = newStatus
                espProgress = newProgress
                espPitch = newPitch
                espRoll = newRoll

                // When ESP32 confirms calibration is done, check sensor baseline quality before saving
                if (newStatus == "calibrated" && !espBaselineSaved && calibrationTriggered) {
                    // SENSOR QUALITY GATE: Verify sensor reading is within acceptable upright thresholds
                    val isRollAcceptable = kotlin.math.abs(newRoll) <= 15.0f
                    val isPitchAcceptable = newPitch >= -25.0f && newPitch <= 25.0f

                    if (!isRollAcceptable || !isPitchAcceptable) {
                        // Reject bad calibration
                        statusText = "Calibration rejected (Sensor roll: ${"%.1f".format(newRoll)}°, pitch: ${"%.1f".format(newPitch)}°). Sit straight and try again."
                        calibrationTriggered = false
                        stableFrames = 0
                        Firebase.firestore
                            .collection("esp32")
                            .document("calibration")
                            .update("status", "rejected")
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    } else {
                        espBaselineSaved = true
                        statusText = "ESP32 calibrated! Saving baseline…"
                        // Success beep tone
                        playBeepTone(ToneGenerator.TONE_PROP_ACK, 350)
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        showFlash = true
                        
                        scope.launch {
                            kotlinx.coroutines.delay(300)
                            showFlash = false
                        }

                        scope.launch {
                            // Build baseline from ESP32 sensor values
                            val sensorBaseline = Baseline(
                                pitch = newPitch,
                                roll = newRoll,
                                sampleCount = 1
                            )
                            capturedBaseline = sensorBaseline

                            val result = saveBaselineToFirestore(sensorBaseline)

                            statusText = if (result.isSuccess)
                                "Calibration complete ✓  pitch=${"%.1f".format(newPitch)}°  roll=${"%.1f".format(newRoll)}°"
                            else
                                "Saved locally (Firestore failed: ${result.exceptionOrNull()?.message})"

                            // Mark ESP32 doc as acknowledged so it doesn't re-trigger
                            Firebase.firestore
                                .collection("esp32")
                                .document("calibration")
                                .update("status", "acknowledged")
                        }
                    }
                }
            }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            CameraPreviewWithAnalysis(
                onMetrics = { metrics, frameBitmap ->
                    // Stop updating camera state once calibration has been triggered
                    if (calibrationTriggered) return@CameraPreviewWithAnalysis

                    liveMetrics = metrics
                    if (metrics == null) {
                        statusText = "Turn sideways — show ear & shoulder"
                        cvaValue = 0f
                        lateralTiltValue = 0f
                        stableFrames = 0
                    } else {
                        statusText = metrics.reason
                        cvaValue = metrics.cva
                        lateralTiltValue = metrics.lateralTilt

                        if (PostureAnalyzer.DEV_MODE) {
                            android.util.Log.d(
                                "CalibrationScreen",
                                "CVA=${"%.1f".format(metrics.cva)}°, NormCVA=${"%.1f".format(metrics.cvaNormalizedSpace)}°, TargetRange=[${PostureAnalyzer.CVA_MIN.toInt()}°..${PostureAnalyzer.CVA_MAX.toInt()}°], Posture=${metrics.posture}, isCorrect=${metrics.isCorrect}, stableFrames=$stableFrames/$REQUIRED_STABLE_FRAMES"
                            )
                        }

                        // Only increment stable frames when posture quality passes ALL gates (CVA, lateral tilt, alignment)
                        stableFrames = if (metrics.isCorrect) stableFrames + 1 else 0

                        if (stableFrames >= REQUIRED_STABLE_FRAMES) {
                            calibrationTriggered = true
                            capturedRawCva = metrics.cva
                            capturedLateralTilt = metrics.lateralTilt
                            capturedMetrics = metrics
                            statusText = "Good posture captured! Waiting for ESP32 to calibrate…"

                            // Save captured frame image and log raw telemetry asynchronously
                            val bitmapCopy = frameBitmap?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val savedFile = CalibrationLogger.saveCalibrationCapture(
                                    context = context,
                                    bitmap = bitmapCopy,
                                    metrics = metrics
                                )
                                capturedImageFile = savedFile
                            }

                            // Produce immediate beep sound on phone
                            playBeepTone(ToneGenerator.TONE_PROP_BEEP, 300)
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)

                            // Tell ESP32 to start calibration & beep buzzer via Firebase
                            Firebase.firestore
                                .collection("esp32")
                                .document("calibration")
                                .set(
                                    hashMapOf(
                                        "requested" to true,
                                        "status" to "pending",
                                        "progress" to 0,
                                        "buzzer" to true,
                                        "beep" to true,
                                        "rawCva" to metrics.cva
                                    )
                                )
                        }
                    }
                }
            )

            // Visual Debug Landmark Overlay (Live or Frozen on Capture)
            val displayMetrics = if (calibrationTriggered) capturedMetrics else liveMetrics
            PostureLandmarkOverlay(metrics = displayMetrics)

            // Flash overlay for micro-interaction
            androidx.compose.animation.AnimatedVisibility(
                visible = showFlash,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut()
            ) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)))
            }

            // Status overlay card
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xE6101820))
            ) {
                Column(Modifier.padding(14.dp)) {

                    androidx.compose.animation.Crossfade(targetState = statusText) { text ->
                        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(6.dp))

                    // Camera readings (CVA / Pitch and Lateral Tilt / Roll)
                    if (!calibrationTriggered) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Live CVA: ${"%.1f".format(cvaValue)}°",
                                color = if (PostureAnalyzer.isGoodPosture(cvaValue)) Color(0xFF4EE1A0) else Color(0xFFFF6B6B),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Target: ${PostureAnalyzer.CVA_MIN.toInt()}°–${PostureAnalyzer.CVA_MAX.toInt()}° (Raw: ${cvaValue}°)",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            "Lateral Tilt (Roll): ${"%.1f".format(lateralTiltValue)}°  |  Stable: $stableFrames / $REQUIRED_STABLE_FRAMES",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )

                        liveMetrics?.let { lm ->
                            if (lm.tragusXPx > 0f || lm.c7XPx > 0f) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Tragus: (${lm.tragusXPx.toInt()}, ${lm.tragusYPx.toInt()})  C7: (${lm.c7XPx.toInt()}, ${lm.c7YPx.toInt()})",
                                        color = Color.Cyan.copy(alpha = 0.9f),
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        "Frame: ${lm.imageWidth}x${lm.imageHeight}",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    } else if (capturedRawCva > 0f) {
                        // High-visibility captured CVA result card shown immediately upon capture
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            color = Color(0xFF133E32).copy(alpha = 0.92f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF4EE1A0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "CAPTURED CVA (VALIDATION)",
                                        color = Color(0xFF4EE1A0),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        "Raw: ${capturedRawCva}°",
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "CVA: ${"%.1f".format(capturedRawCva)}°",
                                    color = if (PostureAnalyzer.isGoodPosture(capturedRawCva)) Color.White else Color(0xFFFF6B6B),
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "Target Band: ${PostureAnalyzer.CVA_MIN.toInt()}°–${PostureAnalyzer.CVA_MAX.toInt()}°  •  Posture: ${capturedMetrics?.posture ?: PostureAnalyzer.classifyPosture(capturedRawCva)}",
                                    color = if (PostureAnalyzer.isGoodPosture(capturedRawCva)) Color(0xFF4EE1A0) else Color(0xFFFF6B6B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Angle with Horiz: ${"%.1f".format(capturedMetrics?.cvaPixelSpace ?: capturedRawCva)}°  •  Angle with Vert: ${"%.1f".format(capturedMetrics?.angleWithVertical ?: (90f - capturedRawCva))}°",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Aspect-Distorted Norm CVA: ${"%.1f".format(capturedMetrics?.cvaNormalizedSpace ?: capturedRawCva)}°  •  Lateral Tilt: ${"%.1f".format(capturedLateralTilt)}°",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )

                                Spacer(Modifier.height(6.dp))
                                HorizontalDivider(color = Color(0xFF4EE1A0).copy(alpha = 0.3f), thickness = 0.5.dp)
                                Spacer(Modifier.height(4.dp))

                                 // Raw landmark coordinates
                                capturedMetrics?.let { m ->
                                    Text(
                                        "Tragus (Ear): (${"%.1f".format(m.tragusXPx)}, ${"%.1f".format(m.tragusYPx)}) px  [norm: ${"%.3f".format(m.tragusXNorm)}, ${"%.3f".format(m.tragusYNorm)}]",
                                        color = Color.Cyan,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        "C7 Neck Base: (${"%.1f".format(m.c7XPx)}, ${"%.1f".format(m.c7YPx)}) px  [norm: ${"%.3f".format(m.c7XNorm)}, ${"%.3f".format(m.c7YNorm)}]",
                                        color = Color.Yellow,
                                        fontSize = 11.sp
                                    )
                                    if (m.shoulderYPx > m.c7YPx + 2f) {
                                        Text(
                                            "Shoulder Joint: (${"%.1f".format(m.shoulderXPx)}, ${"%.1f".format(m.shoulderYPx)}) px  (C7 elevated +20% toward ear)",
                                            color = Color(0xFFFFB74D),
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        "Frame Dimensions: ${m.imageWidth}x${m.imageHeight} px (Aspect: ${"%.2f".format(m.imageWidth.toFloat() / maxOf(1, m.imageHeight))})",
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 10.sp
                                    )
                                }

                                capturedImageFile?.let { file ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Logged: ${file.name}",
                                        color = Color(0xFF4EE1A0),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    // ESP32 sensor section
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.5f), thickness = 0.5.dp)
                    Spacer(Modifier.height(6.dp))

                    Text(
                        "ESP32 Status: $espStatus",
                        color = Color.Cyan,
                        fontSize = 13.sp
                    )

                    if (calibrationTriggered && espProgress > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Sensor Progress: $espProgress%",
                            color = Color.Green,
                            fontSize = 13.sp
                        )
                        LinearProgressIndicator(
                            progress = { espProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }

                    // Show sensor baseline values once ESP32 is done
                    if (espStatus == "calibrated" || espStatus == "acknowledged") {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Sensor Pitch: ${"%.2f".format(espPitch)}°",
                            color = Color.Yellow,
                            fontSize = 13.sp
                        )
                        Text(
                            "Sensor Roll: ${"%.2f".format(espRoll)}°",
                            color = Color.Yellow,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
            ) {
                Text(if (capturedBaseline != null) "Done" else "Cancel")
            }

        } else {
            // No camera permission UI
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission required", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

/**
 * Visual landmark overlay rendering Tragus (Cyan), C7 Base (Yellow), connecting vector (Green),
 * and horizontal reference line (Dashed White) with real-time degree readout.
 */
@Composable
private fun PostureLandmarkOverlay(
    metrics: PostureMetrics?,
    modifier: Modifier = Modifier
) {
    if (metrics == null || (metrics.tragusXNorm == 0f && metrics.c7XNorm == 0f)) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val viewW = size.width
        val viewH = size.height
        val imgW = if (metrics.imageWidth > 0) metrics.imageWidth.toFloat() else 1000f
        val imgH = if (metrics.imageHeight > 0) metrics.imageHeight.toFloat() else 1000f

        // Exact transform for PreviewView.ScaleType.FILL_CENTER
        val scale = maxOf(viewW / imgW, viewH / imgH)
        val scaledW = imgW * scale
        val scaledH = imgH * scale
        val offsetX = (viewW - scaledW) / 2f
        val offsetY = (viewH - scaledH) / 2f

        // Front camera image is mirrored horizontally in PreviewView
        val tragusScreenX = offsetX + (1f - metrics.tragusXNorm) * scaledW
        val tragusScreenY = offsetY + metrics.tragusYNorm * scaledH

        val c7ScreenX = offsetX + (1f - metrics.c7XNorm) * scaledW
        val c7ScreenY = offsetY + metrics.c7YNorm * scaledH

        val shoulderScreenX = offsetX + (1f - metrics.shoulderXNorm) * scaledW
        val shoulderScreenY = offsetY + metrics.shoulderYNorm * scaledH

        // 1. Horizontal reference line through C7 (dashed white)
        val dashPath = Path().apply {
            moveTo(0f, c7ScreenY)
            lineTo(viewW, c7ScreenY)
        }
        drawPath(
            path = dashPath,
            color = Color.White.copy(alpha = 0.65f),
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f), 0f)
            )
        )

        // 2. Dotted derivation offset line from shoulder joint up to C7 neck base
        if (shoulderScreenY > c7ScreenY + 2f) {
            val offsetPath = Path().apply {
                moveTo(shoulderScreenX, shoulderScreenY)
                lineTo(c7ScreenX, c7ScreenY)
            }
            drawPath(
                path = offsetPath,
                color = Color(0xFFFF9800).copy(alpha = 0.75f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                )
            )
            // Shoulder Joint Dot (Orange)
            drawCircle(
                color = Color(0xFFFF9800),
                radius = 5.dp.toPx(),
                center = Offset(shoulderScreenX, shoulderScreenY)
            )
        }

        // 3. Vector line from C7 to Tragus (Mint green)
        drawLine(
            color = Color(0xFF4EE1A0),
            start = Offset(c7ScreenX, c7ScreenY),
            end = Offset(tragusScreenX, tragusScreenY),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 4. Tragus Landmark (Cyan circle with outer halo)
        val tragusCenter = Offset(tragusScreenX, tragusScreenY)
        drawCircle(
            color = Color.Cyan.copy(alpha = 0.25f),
            radius = 16.dp.toPx(),
            center = tragusCenter
        )
        drawCircle(
            color = Color.Cyan,
            radius = 7.dp.toPx(),
            center = tragusCenter
        )

        // 5. C7 / Neck Base Landmark (Yellow circle with outer halo)
        val c7Center = Offset(c7ScreenX, c7ScreenY)
        drawCircle(
            color = Color.Yellow.copy(alpha = 0.25f),
            radius = 16.dp.toPx(),
            center = c7Center
        )
        drawCircle(
            color = Color.Yellow,
            radius = 7.dp.toPx(),
            center = c7Center
        )

        // 6. Draw text annotations directly on Canvas
        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 12.sp.toPx()
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
            }

            paint.color = android.graphics.Color.CYAN
            canvas.nativeCanvas.drawText("Tragus (Ear)", tragusScreenX + 24f, tragusScreenY - 8f, paint)

            paint.color = android.graphics.Color.YELLOW
            canvas.nativeCanvas.drawText("C7 (Neck Base)", c7ScreenX + 24f, c7ScreenY - 8f, paint)

            if (shoulderScreenY > c7ScreenY + 2f) {
                paint.color = android.graphics.Color.rgb(255, 165, 0)
                canvas.nativeCanvas.drawText("Shoulder Joint", shoulderScreenX + 18f, shoulderScreenY + 16f, paint)
            }

            paint.color = android.graphics.Color.rgb(78, 225, 160)
            paint.textSize = 14.sp.toPx()
            val midX = (tragusScreenX + c7ScreenX) / 2f
            val midY = (tragusScreenY + c7ScreenY) / 2f
            canvas.nativeCanvas.drawText("CVA: ${"%.1f".format(metrics.cva)}°", midX + 16f, midY - 10f, paint)
        }
    }
}

@Composable
private fun CameraPreviewWithAnalysis(onMetrics: (PostureMetrics?, android.graphics.Bitmap?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    val helper = remember {
        PoseLandmarkerHelper(
            context = context,
            onResult = { result, bitmap ->
                val w = bitmap?.width ?: 0
                val h = bitmap?.height ?: 0
                onMetrics(PostureAnalyzer.analyze(result, w, h), bitmap)
            },
            onError = { /* log if needed */ }
        )
    }

    DisposableEffect(Unit) { onDispose { helper.close(); executor.shutdown() } }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(executor) { imageProxy -> helper.detect(imageProxy) }
                    }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        analysis
                    )
                } catch (e: Exception) { e.printStackTrace() }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}