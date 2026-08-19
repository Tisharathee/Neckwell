package com.humblecoders.neckwell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.humblecoders.neckwell.ui.theme.MintSurface
import com.humblecoders.neckwell.ui.theme.Purple
import com.humblecoders.neckwell.ui.theme.TextDark
import com.humblecoders.neckwell.ui.theme.TextGray
import com.humblecoders.neckwell.ui.theme.WarningAmber as WarningAmberColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun HomeScreen() {
    var isConnected by remember { mutableStateOf(true) }
    var currentPosture by remember { mutableStateOf<PostureData?>(null) }
    var todayData by remember { mutableStateOf<List<PostureData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun refreshData() {
        scope.launch {
            loading = true
            currentPosture = fetchLatestPosture()
            todayData = fetchTodayPostureData()
            loading = false
        }
    }

    LaunchedEffect(Unit) { refreshData() }

    Column(Modifier.fillMaxSize().background(BackgroundGray)) {
        NeckWellHeader(
            title = "NeckWell",
            subtitle = "Posture Monitoring",
            action = {
                IconButton(onClick = ::refreshData) {
                    Icon(Icons.Outlined.Refresh, "Refresh posture data", tint = Color.White)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionCard(isConnected = isConnected, onToggle = { isConnected = !isConnected })
            PostureCard(currentPosture = currentPosture, loading = loading)
            SummaryCard(todayData)
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ConnectionCard(isConnected: Boolean, onToggle: () -> Unit) {
    NeckWellCard {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(
                icon = if (isConnected) Icons.Outlined.Link else Icons.Outlined.LinkOff,
                tint = if (isConnected) AccentTealDark else TextGray,
                size = 50
            )
            Column(Modifier.weight(1f)) {
                Text("Connection Status", color = TextGray, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(if (isConnected) AccentTeal else TextGray, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isConnected) "Device Connected" else "Device Disconnected",
                        color = if (isConnected) AccentTealDark else TextDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            OutlinedButton(
                onClick = onToggle,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (isConnected) AlertCoral else AccentTealDark
                ),
                border = BorderStroke(1.dp, if (isConnected) AlertCoral else AccentTealDark),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Icon(
                    if (isConnected) Icons.Outlined.LinkOff else Icons.Outlined.Link,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isConnected) "Disconnect" else "Connect", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PostureCard(currentPosture: PostureData?, loading: Boolean) {
    val posture = currentPosture?.posture?.ifBlank { "No reading" } ?: "No reading"
    val score = calculatePostureScore(currentPosture?.posture.orEmpty())
    val isHealthy = score >= 80
    val statusColor by animateColorAsState(
        if (isHealthy) AccentTeal else if (score >= 60) WarningAmberColor else AlertCoral,
        tween(500), label = "postureColor"
    )

    NeckWellCard {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(36.dp).height(1.dp).background(AccentTeal.copy(alpha = 0.5f)))
                Spacer(Modifier.width(12.dp))
                Text("Current Posture", color = TextDark, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(36.dp).height(1.dp).background(AccentTeal.copy(alpha = 0.5f)))
            }
            Spacer(Modifier.height(20.dp))

            if (loading) {
                Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentTeal, strokeWidth = 5.dp)
                }
                Text("Updating your posture…", color = TextGray)
            } else {
                PostureGauge(score = score, color = statusColor)
                Spacer(Modifier.height(12.dp))
                Text(
                    posture,
                    color = statusColor,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    when {
                        score >= 80 -> "Great posture! Keep it up."
                        score >= 60 -> "A small adjustment will help."
                        score > 0 -> "Bring your head back over your shoulders."
                        else -> "Waiting for a posture reading."
                    },
                    color = TextGray,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text("$score%", color = TextDark, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                Text("Posture Score", color = TextGray, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PostureGauge(score: Int, color: Color) {
    val progress by animateFloatAsState(score / 100f, tween(900), label = "postureProgress")
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 15.dp.toPx()
            drawArc(
                color = Color(0xFFE4EAEC), startAngle = 135f, sweepAngle = 270f,
                useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color, startAngle = 135f, sweepAngle = 270f * progress,
                useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round)
            )
            val angle = Math.toRadians((135f + 270f * progress).toDouble())
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = color,
                radius = 9.dp.toPx(),
                center = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * radius,
                    center.y + kotlin.math.sin(angle).toFloat() * radius
                )
            )
        }
        Box(
            Modifier.size(154.dp).background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, color.copy(alpha = 0.09f))),
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.AccessibilityNew, null, tint = color, modifier = Modifier.size(86.dp))
        }
    }
}

@Composable
private fun SummaryCard(todayData: List<PostureData>) {
    val goodPercent = calculateGoodPosturePercentage(todayData)
    val alerts = countAlerts(todayData)
    val activeMinutes = calculateActiveMinutes(todayData)
    NeckWellCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("TODAY'S SUMMARY", color = TextDark, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.CalendarToday, null, tint = TextGray, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date()), color = TextGray)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE5EBEE)))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryMetric(Icons.Outlined.TrendingUp, "$goodPercent%", "Good\nposture", AccentTealDark, Modifier.weight(1f))
                SummaryMetric(Icons.Outlined.WarningAmber, "$alerts", "Poor posture\nevents", WarningAmberColor, Modifier.weight(1f))
                SummaryMetric(Icons.Outlined.QueryStats, "${todayData.size}", "Readings\ntoday", InfoBlue, Modifier.weight(1f))
                SummaryMetric(Icons.Outlined.AccessTime, "${activeMinutes}m", "Active time\ntracked", Purple, Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().background(MintSurface, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconTile(Icons.Outlined.Lightbulb, size = 42)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Keep your chin parallel to the ground", color = AccentTealDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("This reduces neck strain and improves alignment.", color = TextGray, fontSize = 12.sp)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = TextGray)
            }
        }
    }
}

@Composable
private fun SummaryMetric(icon: ImageVector, value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(icon, color, size = 42)
        Spacer(Modifier.height(9.dp))
        Text(value, color = color, fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(label, color = TextGray, fontSize = 11.sp, lineHeight = 15.sp, textAlign = TextAlign.Center)
    }
}

private fun calculateActiveMinutes(data: List<PostureData>): Int {
    val timestamps = data.mapNotNull { it.timestamp }
    if (timestamps.size < 2) return 0
    return max(1, ((timestamps.maxOrNull()!! - timestamps.minOrNull()!!) / 60L).toInt())
}
