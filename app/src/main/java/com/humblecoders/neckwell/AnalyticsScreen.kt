package com.humblecoders.neckwell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humblecoders.neckwell.ui.theme.AccentTeal
import com.humblecoders.neckwell.ui.theme.AccentTealDark
import com.humblecoders.neckwell.ui.theme.AlertCoral
import com.humblecoders.neckwell.ui.theme.BackgroundGray
import com.humblecoders.neckwell.ui.theme.InfoBlue
import com.humblecoders.neckwell.ui.theme.TextDark
import com.humblecoders.neckwell.ui.theme.TextGray
import com.humblecoders.neckwell.ui.theme.WarningAmber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnalyticsScreen() {
    var todayData by remember { mutableStateOf<List<PostureData>>(emptyList()) }
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch { todayData = fetchTodayPostureData() } }
    LaunchedEffect(Unit) { refresh() }

    val hourlyData = remember(todayData) { getHourlyPostureQuality(todayData) }
    val distribution = remember(todayData) { getPostureDistribution(todayData) }
    val average = remember(todayData) {
        if (todayData.isEmpty()) 0 else todayData.map { calculatePostureScore(it.posture) }.average().toInt()
    }

    Column(Modifier.fillMaxSize().background(BackgroundGray)) {
        NeckWellHeader(
            title = "Analytics",
            subtitle = "Daily posture analysis",
            compact = true,
            action = {
                IconButton(onClick = ::refresh) {
                    Icon(Icons.Outlined.Refresh, "Refresh analytics", tint = Color.White)
                }
            }
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NeckWellCard {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(Icons.Outlined.QueryStats, AccentTealDark, size = 52)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Today's average", color = TextGray, fontSize = 13.sp)
                        Text("$average% posture score", color = TextDark, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("Based on ${todayData.size} recorded readings", color = TextGray, fontSize = 12.sp)
                    }
                }
            }

            AnalyticsCard(Icons.Outlined.BarChart, "Hourly posture quality", "Your score throughout the day") {
                HourlyBarChart(hourlyData)
            }
            AnalyticsCard(Icons.Outlined.EmojiEvents, "Posture distribution", "How today's readings break down") {
                PostureDistributionChart(distribution)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AnalyticsCard(icon: ImageVector, title: String, subtitle: String, content: @Composable () -> Unit) {
    NeckWellCard {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(icon, InfoBlue, size = 44)
                Spacer(Modifier.width(12.dp))
                SectionHeading(title, subtitle)
            }
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

@Composable
fun HourlyBarChart(hourlyData: List<Pair<String, Float>>) {
    if (hourlyData.isEmpty()) {
        Text(
            "No posture readings yet today",
            Modifier.fillMaxWidth().padding(34.dp),
            color = TextGray,
            textAlign = TextAlign.Center
        )
        return
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(hourlyData) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.fillMaxWidth().height(190.dp)) {
        val chartHeight = size.height - 28.dp.toPx()
        val slotWidth = size.width / hourlyData.size
        val barWidth = (slotWidth * 0.48f).coerceAtMost(34.dp.toPx())
        drawLine(Color(0xFFE4EAEE), Offset(0f, chartHeight), Offset(size.width, chartHeight), 1.dp.toPx())
        hourlyData.forEachIndexed { index, (_, score) ->
            val height = (score / 100f) * chartHeight * progress.value
            val color = when {
                score >= 80 -> AccentTeal
                score >= 60 -> WarningAmber
                else -> AlertCoral
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(index * slotWidth + (slotWidth - barWidth) / 2f, chartHeight - height),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        hourlyData.forEach { (time, _) ->
            Text(time, color = TextGray, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}

private data class DistributionStyle(val label: String, val color: Color)

@Composable
fun PostureDistributionChart(distribution: Map<String, Int>) {
    val postureTypes = listOf(
        DistributionStyle("Excellent", AccentTeal),
        DistributionStyle("Good", InfoBlue),
        DistributionStyle("Okay", WarningAmber),
        DistributionStyle("Poor", AlertCoral)
    )
    Row(
        Modifier.fillMaxWidth().height(205.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        postureTypes.forEachIndexed { index, style ->
            val percentage = distribution[style.label] ?: 0
            val targetHeight = if (percentage > 0) (percentage * 1.15f).coerceIn(20f, 120f) else 10f
            var animate by remember { mutableStateOf(false) }
            LaunchedEffect(distribution) { delay(index * 120L); animate = true }
            val height by animateDpAsState(if (animate) targetHeight.dp else 0.dp, tween(700), label = style.label)
            Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$percentage%", color = style.color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(7.dp))
                Box(Modifier.width(46.dp).height(height).background(style.color, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)))
                Spacer(Modifier.height(8.dp))
                Text(style.label, color = TextGray, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
