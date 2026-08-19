package com.humblecoders.neckwell

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun AnalyticsScreen() {
    var todayData by remember { mutableStateOf<List<PostureData>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val refreshData = {
        scope.launch {
            todayData = fetchTodayPostureData()
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    val hourlyData = remember(todayData) { getHourlyPostureQuality(todayData) }
    val distribution = remember(todayData) { getPostureDistribution(todayData) }

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
                        text = "Analytics",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Daily Posture Analysis",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hourly Posture Quality Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "📊",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Hourly Posture Quality",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HourlyBarChart(hourlyData = hourlyData)
                }
            }

            // Posture Distribution Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "🏆",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Posture Distribution",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    PostureDistributionChart(distribution = distribution)
                }
            }
        }
    }
}

@Composable
fun HourlyBarChart(hourlyData: List<Pair<String, Float>>) {
    if (hourlyData.isEmpty()) {
        Text(
            text = "No data available for today",
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            textAlign = TextAlign.Center,
            color = Color.Gray
        )
        return
    }

    val animationProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(hourlyData) {
        animationProgress.snapTo(0f)
        if (hourlyData.isNotEmpty()) {
            animationProgress.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(top = 16.dp)
    ) {
        val barWidth = size.width / (hourlyData.size * 2)
        val maxScore = 100f
        val chartHeight = size.height - 40.dp.toPx()

        hourlyData.forEachIndexed { index, (time, score) ->
            val finalBarHeight = (score / maxScore) * chartHeight
            val barHeight = finalBarHeight * animationProgress.value
            val x = (index * 2 + 0.5f) * barWidth

            val color = when {
                score >= 80 -> com.humblecoders.neckwell.ui.theme.AccentTeal
                score >= 60 -> Color(0xFFFFA726)
                else -> com.humblecoders.neckwell.ui.theme.AlertCoral
            }

            drawRect(
                color = color,
                topLeft = Offset(x, chartHeight - barHeight),
                size = Size(barWidth * 0.8f, barHeight)
            )
        }
    }

    // Time labels
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        hourlyData.forEach { (time, _) ->
            Text(
                text = time,
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PostureDistributionChart(distribution: Map<String, Int>) {
    val postureTypes = listOf(
        Triple("Excellent", "😊", com.humblecoders.neckwell.ui.theme.AccentTeal),
        Triple("Good", "🙂", com.humblecoders.neckwell.ui.theme.PrimaryBlue),
        Triple("Okay", "😐", Color(0xFFFFA726)),
        Triple("Poor", "😕", com.humblecoders.neckwell.ui.theme.AlertCoral)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        postureTypes.forEachIndexed { index, (label, emoji, color) ->
            val percentage = distribution[label] ?: 0
            val targetHeight = if (percentage > 0) (percentage * 1.2f).coerceIn(20f, 140f) else 20f

            var isAnimated by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(index * 150L) // Staggered animation
                isAnimated = true
            }

            val animatedHeight by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (isAnimated) targetHeight.dp else 0.dp,
                animationSpec = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(70.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(animatedHeight)
                        .background(color, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = emoji,
                    fontSize = 28.sp
                )

                Text(
                    text = "$percentage%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}