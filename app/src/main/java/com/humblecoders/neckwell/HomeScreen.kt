package com.humblecoders.neckwell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun HomeScreen() {
    var isConnected by remember { mutableStateOf(true) }
    var currentPosture by remember { mutableStateOf<PostureData?>(null) }
    var todayData by remember { mutableStateOf<List<PostureData>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val refreshData = {
        scope.launch {
            currentPosture = fetchLatestPosture()
            todayData = fetchTodayPostureData()
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2196F3))
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { refreshData() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(
                        text = "🔄",
                        fontSize = 24.sp
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "NeckWell",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Posture Monitoring",
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                
                Spacer(modifier = Modifier.size(48.dp))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Connection Status",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        if (isConnected) Color(0xFF4CAF50) else Color.Gray,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isConnected) "Device Connected" else "Device Disconnected",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Button(
                            onClick = { isConnected = !isConnected },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF5252)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isConnected) "Disconnect" else "Connect",
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Current Posture Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Current Posture",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (currentPosture == null) {
                        // Skeleton Loader
                        val infiniteTransition = rememberInfiniteTransition()
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 0.7f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .padding(16.dp)
                                .background(Color.LightGray.copy(alpha = alpha), androidx.compose.foundation.shape.CircleShape)
                        )
                        Box(modifier = Modifier.padding(top = 16.dp).height(24.dp).width(120.dp).background(Color.LightGray.copy(alpha = alpha), RoundedCornerShape(4.dp)))
                    } else {
                        // Animated Ring
                        val isGood = currentPosture?.posture?.contains("Good", ignoreCase = true) == true
                        val targetColor = if (isGood) com.humblecoders.neckwell.ui.theme.AccentTeal else com.humblecoders.neckwell.ui.theme.AlertCoral
                        val animatedColor by animateColorAsState(targetValue = targetColor, animationSpec = tween(1000))
                        
                        val infiniteTransition = rememberInfiniteTransition()
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                            Canvas(modifier = Modifier.size(120.dp)) {
                                val strokeWidth = 8.dp.toPx()
                                drawCircle(color = Color.LightGray.copy(alpha = 0.3f), style = Stroke(strokeWidth))
                                drawCircle(
                                    color = animatedColor.copy(alpha = 0.3f),
                                    radius = size.minDimension / 2 * pulseScale,
                                    style = Stroke(strokeWidth * 0.5f)
                                )
                                drawArc(
                                    color = animatedColor,
                                    startAngle = -90f,
                                    sweepAngle = if (isGood) 360f else 270f,
                                    useCenter = false,
                                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                            Text(text = getPostureEmoji(currentPosture?.posture ?: ""), fontSize = 48.sp)
                        }

                        Text(
                            text = currentPosture?.posture ?: "Good posture",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = animatedColor
                        )

                        Text(
                            text = "Score: ${calculatePostureScore(currentPosture?.posture ?: "")}%",
                            fontSize = 18.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // Today's Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Today's Summary",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${calculateGoodPosturePercentage(todayData)}%",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2196F3)
                            )
                            Text(
                                text = "Good Posture",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${countAlerts(todayData)}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2196F3)
                            )
                            Text(
                                text = "Alerts",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}