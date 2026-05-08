package com.humblecoders.neckwell

import android.Manifest
import android.content.pm.PackageManager
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

    // Shared state between analyzer and UI
    var statusText by remember { mutableStateOf("Turn sideways to the camera") }
    var cvaValue by remember { mutableFloatStateOf(0f) }
    var stableFrames by remember { mutableIntStateOf(0) }
    var calibrationTriggered by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var captureProgress by remember { mutableFloatStateOf(0f) }
    var capturedBaseline by remember { mutableStateOf<Baseline?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            CameraPreviewWithAnalysis(
                onMetrics = { metrics ->
                    if (metrics == null) {
                        statusText = "No person detected"
                        stableFrames = 0
                    } else {
                        statusText = metrics.reason
                        cvaValue = metrics.cva
                        stableFrames = if (metrics.isCorrect) stableFrames + 1 else 0
                        if (stableFrames >= REQUIRED_STABLE_FRAMES && !calibrationTriggered) {
                            calibrationTriggered = true
                            statusText = "Hold still — capturing baseline…"
                            scope.launch {
                                val baseline = BaselineCapture.capture { progress ->
                                    captureProgress = progress
                                }
                                capturedBaseline = baseline
                                statusText = "Saving baseline…"
                                val result = saveBaselineToFirestore(baseline)
                                statusText = if (result.isSuccess)
                                    "Calibration complete ✓  pitch=${"%.1f".format(baseline.pitch)}°  roll=${"%.1f".format(baseline.roll)}°"
                                else
                                    "Saved locally (Firestore failed: ${result.exceptionOrNull()?.message})"
                            }
                        }
                    }
                }
            )

            // Status overlay
            Card(
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xCC000000))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(statusText, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("CVA: ${"%.1f".format(cvaValue)}°", color = Color.White, fontSize = 14.sp)
                    Text("Stable: $stableFrames / $REQUIRED_STABLE_FRAMES", color = Color.White, fontSize = 14.sp)
                    if (calibrationTriggered && capturedBaseline == null) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { captureProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)
            ) { Text(if (calibrationTriggered) "Done" else "Cancel") }
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
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