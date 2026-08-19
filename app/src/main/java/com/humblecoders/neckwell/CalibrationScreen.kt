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

private const val REQUIRED_STABLE_FRAMES = 30  // ~1 sec at 30fps

private fun playBeepTone(toneType: Int = ToneGenerator.TONE_PROP_BEEP, durationMs: Int = 250) {
    try {
        val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGenerator.startTone(toneType, durationMs)
    } catch (e: Exception) {
        e.printStackTrace()
    }
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
                onMetrics = { metrics ->
                    // Stop updating camera state once calibration has been triggered
                    if (calibrationTriggered) return@CameraPreviewWithAnalysis

                    if (metrics == null) {
                        statusText = "No person detected"
                        stableFrames = 0
                    } else {
                        statusText = metrics.reason
                        cvaValue = metrics.cva
                        lateralTiltValue = metrics.lateralTilt
                        // Only increment stable frames when posture quality passes ALL gates (CVA, lateral tilt, alignment)
                        stableFrames = if (metrics.isCorrect) stableFrames + 1 else 0

                        if (stableFrames >= REQUIRED_STABLE_FRAMES) {
                            calibrationTriggered = true
                            statusText = "Good posture captured! Waiting for ESP32 to calibrate…"

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
                                        "beep" to true
                                    )
                                )
                        }
                    }
                }
            )

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
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xCC000000))
            ) {
                Column(Modifier.padding(16.dp)) {

                    androidx.compose.animation.Crossfade(targetState = statusText) { text ->
                        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))

                    // Camera readings (CVA / Pitch and Lateral Tilt / Roll)
                    if (!calibrationTriggered) {
                        Text(
                            "CVA (Pitch): ${"%.1f".format(cvaValue)}°",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            "Lateral Tilt (Roll): ${"%.1f".format(lateralTiltValue)}°",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            "Stable: $stableFrames / $REQUIRED_STABLE_FRAMES",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // ESP32 sensor section
                    HorizontalDivider(color = Color.Gray, thickness = 0.5.dp)
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
                Text(if (capturedBaseline != null) "Done" else if (calibrationTriggered) "Cancel" else "Cancel")
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

@Composable
private fun CameraPreviewWithAnalysis(onMetrics: (PostureMetrics?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    val helper = remember {
        PoseLandmarkerHelper(
            context = context,
            onResult = { result -> onMetrics(PostureAnalyzer.analyze(result)) },
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