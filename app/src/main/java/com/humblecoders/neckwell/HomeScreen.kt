package com.humblecoders.neckwell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humblecoders.neckwell.ui.theme.NeckWellAccent
import com.humblecoders.neckwell.ui.theme.NeckWellAccentDark
import com.humblecoders.neckwell.ui.theme.NeckWellAccentSurface
import com.humblecoders.neckwell.ui.theme.NeckWellBackground
import com.humblecoders.neckwell.ui.theme.NeckWellSurface
import com.humblecoders.neckwell.ui.theme.NeckWellSurfaceBright
import com.humblecoders.neckwell.ui.theme.NeckWellTextPrimary
import com.humblecoders.neckwell.ui.theme.NeckWellTextSecondary
import com.humblecoders.neckwell.ui.theme.NeckWellTextMuted
import com.humblecoders.neckwell.ui.theme.NeckWellDivider
import com.humblecoders.neckwell.ui.theme.AlertCoral
import com.humblecoders.neckwell.ui.theme.InfoBlue
import com.humblecoders.neckwell.ui.theme.Purple
import com.humblecoders.neckwell.ui.theme.WarningAmber as WarningAmberColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.platform.LocalContext

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    NeckWellPreferences.init(context)

    val deviceStatus by DeviceStatusManager.deviceStatus.collectAsState()
    var currentPosture by remember { mutableStateOf<PostureData?>(null) }
    var todayData by remember { mutableStateOf<List<PostureData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var lastAlertedTimestamp by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    fun refreshData() {
        scope.launch {
            loading = true
            val latest = fetchLatestPosture()
            currentPosture = latest
            todayData = fetchTodayPostureData()
            loading = false

            // Trigger alert if latest posture is poor
            if (latest?.posture == "Poor" || latest?.posture == "Very poor") {
                val ts = latest.timestamp ?: 0L
                if (ts > lastAlertedTimestamp) {
                    lastAlertedTimestamp = ts
                    AlertManager.triggerBadPostureNotification(
                        context,
                        title = "Poor Posture Detected",
                        message = "Current posture is ${latest.posture}. Please align your neck over your shoulders."
                    )
                    AlertManager.triggerHapticFeedback(context, 400L)
                }
            }
        }
    }

    // Subscribe to live event listeners on mount, cleanly unsubscribe on unmount to prevent memory leaks
    DisposableEffect(context) {
        DeviceStatusManager.startListening()
        DeviceStatusManager.registerSystemListeners(context)

        val postureListener = listenToPostureData(
            onDataUpdated = { today, latest ->
                todayData = today
                currentPosture = latest
                loading = false

                // Trigger alert only when a NEW poor posture reading arrives
                if (latest != null && (latest.posture == "Poor" || latest.posture == "Very poor")) {
                    val ts = latest.timestamp ?: 0L
                    if (ts > lastAlertedTimestamp) {
                        lastAlertedTimestamp = ts
                        AlertManager.triggerBadPostureNotification(
                            context,
                            title = "Poor Posture Detected",
                            message = "Current posture is ${latest.posture}. Please align your neck over your shoulders."
                        )
                        AlertManager.triggerHapticFeedback(context, 400L)
                    }
                } else if (latest != null && latest.timestamp != null) {
                    if (latest.timestamp > lastAlertedTimestamp) {
                        lastAlertedTimestamp = latest.timestamp
                    }
                }
            },
            onError = {
                loading = false
            }
        )

        onDispose {
            postureListener.remove()
            DeviceStatusManager.unregisterSystemListeners(context)
            DeviceStatusManager.stopListening()
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // Time-of-day greeting
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    Column(Modifier.fillMaxSize().background(NeckWellBackground)) {
        // ── Greeting Header ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NeckWellBackground)
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "$greeting, Tanishka",
                        color = NeckWellTextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Let's check your posture",
                        color = NeckWellTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeviceConnectionBadge(connectionState = deviceStatus.connectionState)
                    IconButton(onClick = ::refreshData) {
                        Icon(Icons.Outlined.Refresh, "Refresh posture data", tint = NeckWellTextSecondary)
                    }
                }
                // Profile avatar
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(NeckWellAccent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("T", color = NeckWellAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PostureScoreCard(currentPosture = currentPosture, loading = loading)
            StatCardsGrid(todayData)
            ConnectionCard(
                deviceStatus = deviceStatus,
                onToggle = { DeviceStatusManager.toggleConnection() }
            )
            WeeklyBarChartStrip(todayData)
            TipBanner()
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DeviceConnectionBadge(
    connectionState: DeviceConnectionState,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (connectionState) {
        DeviceConnectionState.CONNECTED -> NeckWellAccent to "Connected"
        DeviceConnectionState.CONNECTING -> WarningAmberColor to "Connecting…"
        DeviceConnectionState.RECONNECTING -> WarningAmberColor to "Reconnecting…"
        DeviceConnectionState.DISCONNECTED -> AlertCoral to "Disconnected"
    }

    Row(
        modifier = modifier
            .background(NeckWellSurface, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = label,
            color = NeckWellTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PostureScoreCard(currentPosture: PostureData?, loading: Boolean) {
    val posture = currentPosture?.posture?.ifBlank { "No reading" } ?: "No reading"
    val score = calculatePostureScore(currentPosture?.posture.orEmpty())
    val isHealthy = score >= 80
    val statusColor by animateColorAsState(
        if (isHealthy) NeckWellAccent else if (score >= 60) WarningAmberColor else AlertCoral,
        tween(500), label = "postureColor"
    )

    NeckWellCard {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loading) {
                Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeckWellAccent, strokeWidth = 4.dp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Updating your posture…", color = NeckWellTextSecondary)
            } else {
                // Circular score ring
                PostureScoreRing(score = score, color = statusColor)
                Spacer(Modifier.height(14.dp))
                Text(
                    posture,
                    color = statusColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        score >= 80 -> "Great posture today — keep it up!"
                        score >= 60 -> "A small adjustment will help."
                        score > 0 -> "Bring your head back over your shoulders."
                        else -> "Waiting for a posture reading."
                    },
                    color = NeckWellTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PostureScoreRing(score: Int, color: Color) {
    val progress by animateFloatAsState(score / 100f, tween(900), label = "scoreProgress")
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            // Background ring
            drawArc(
                color = NeckWellSurfaceBright,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            // Progress ring
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score",
                color = NeckWellTextPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "/100",
                color = NeckWellTextMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatCardsGrid(todayData: List<PostureData>) {
    val goodPercent = calculateGoodPosturePercentage(todayData)
    val alerts = countAlerts(todayData)
    val activeMinutes = calculateActiveMinutes(todayData)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                icon = Icons.Outlined.TrendingUp,
                value = "$goodPercent%",
                label = "Good Posture",
                color = NeckWellAccent,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Outlined.WarningAmber,
                value = "$alerts",
                label = "Poor Events",
                color = AlertCoral,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                icon = Icons.Outlined.QueryStats,
                value = "${todayData.size}",
                label = "Readings",
                color = InfoBlue,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Outlined.AccessTime,
                value = "${activeMinutes}m",
                label = "Active Time",
                color = Purple,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    NeckWellCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconTile(icon, color, size = 38)
                Text(value, color = NeckWellTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, color = NeckWellTextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ConnectionCard(deviceStatus: DeviceStatus, onToggle: () -> Unit) {
    val isConnected = deviceStatus.connectionState == DeviceConnectionState.CONNECTED
    val isConnecting = deviceStatus.connectionState == DeviceConnectionState.CONNECTING ||
            deviceStatus.connectionState == DeviceConnectionState.RECONNECTING

    NeckWellCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconTile(
                    icon = when {
                        isConnected -> Icons.Outlined.Link
                        isConnecting -> Icons.Outlined.Link
                        else -> Icons.Outlined.LinkOff
                    },
                    tint = when {
                        isConnected -> NeckWellAccent
                        isConnecting -> WarningAmberColor
                        else -> NeckWellTextSecondary
                    },
                    size = 48
                )

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Connection Status",
                        color = NeckWellTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(
                                    when (deviceStatus.connectionState) {
                                        DeviceConnectionState.CONNECTED -> NeckWellAccent
                                        DeviceConnectionState.CONNECTING -> WarningAmberColor
                                        DeviceConnectionState.RECONNECTING -> WarningAmberColor
                                        DeviceConnectionState.DISCONNECTED -> NeckWellTextSecondary
                                    },
                                    CircleShape
                                )
                        )
                        Text(
                            when (deviceStatus.connectionState) {
                                DeviceConnectionState.CONNECTED -> "Device Connected"
                                DeviceConnectionState.CONNECTING -> "Connecting to Device…"
                                DeviceConnectionState.RECONNECTING -> "Reconnecting to Device…"
                                DeviceConnectionState.DISCONNECTED -> "Device Disconnected"
                            },
                            color = when {
                                isConnected -> NeckWellAccent
                                isConnecting -> WarningAmberColor
                                else -> NeckWellTextPrimary
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                val buttonColor = when {
                    isConnected -> AlertCoral
                    isConnecting -> AlertCoral
                    else -> NeckWellAccent
                }

                OutlinedButton(
                    onClick = onToggle,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = buttonColor),
                    border = BorderStroke(1.dp, buttonColor),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        if (isConnected) Icons.Outlined.LinkOff else Icons.Outlined.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            isConnected -> "Disconnect"
                            isConnecting -> "Cancel"
                            else -> "Connect"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(NeckWellDivider))
            Spacer(Modifier.height(12.dp))

            // Sub-status row: Live Device connection, WiFi state, and Battery level
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Device Connection Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val connColor = when {
                        isConnected -> NeckWellAccent
                        isConnecting -> WarningAmberColor
                        else -> NeckWellTextSecondary
                    }
                    val connIcon = if (isConnected) Icons.Outlined.Link else Icons.Outlined.LinkOff
                    Icon(connIcon, contentDescription = "Connection state", tint = connColor, modifier = Modifier.size(16.dp))
                    Text(
                        when (deviceStatus.connectionState) {
                            DeviceConnectionState.CONNECTED -> "Sensor Online"
                            DeviceConnectionState.CONNECTING -> "Connecting…"
                            DeviceConnectionState.RECONNECTING -> "Reconnecting…"
                            DeviceConnectionState.DISCONNECTED -> "Sensor Offline"
                        },
                        color = connColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // WiFi Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val (wifiIcon, wifiTint, wifiLabel) = when (deviceStatus.wifiState) {
                        WifiState.CONNECTED -> Triple(Icons.Outlined.Wifi, NeckWellAccent, "WiFi Connected")
                        WifiState.CONNECTING -> Triple(Icons.Outlined.Wifi, WarningAmberColor, "Connecting…")
                        WifiState.FAILED -> Triple(Icons.Outlined.WifiOff, AlertCoral, "WiFi Failed")
                        WifiState.DISCONNECTED -> Triple(Icons.Outlined.WifiOff, NeckWellTextSecondary, "WiFi Offline")
                    }
                    Icon(wifiIcon, contentDescription = "WiFi status", tint = wifiTint, modifier = Modifier.size(16.dp))
                    Text(wifiLabel, color = wifiTint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                // Battery Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val batteryColor = if (deviceStatus.isLowBattery) AlertCoral else NeckWellAccent
                    val batteryIcon = if (deviceStatus.isLowBattery) Icons.Outlined.BatteryAlert else Icons.Outlined.BatteryFull
                    Icon(batteryIcon, contentDescription = "Battery status", tint = batteryColor, modifier = Modifier.size(16.dp))
                    Text(
                        "${deviceStatus.batteryLevel}%" + if (deviceStatus.isLowBattery) " (Low)" else "",
                        color = batteryColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyBarChartStrip(todayData: List<PostureData>) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val todayScore = if (todayData.isNotEmpty()) calculateGoodPosturePercentage(todayData) else 0
    // Placeholder values for other days — in a real app this would pull from Firebase history
    val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val todayIndex = when (dayOfWeek) {
        Calendar.MONDAY -> 0; Calendar.TUESDAY -> 1; Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3; Calendar.FRIDAY -> 4; Calendar.SATURDAY -> 5
        else -> 6
    }

    NeckWellCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("This Week", color = NeckWellTextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEachIndexed { index, day ->
                    val barScore = if (index == todayIndex) todayScore else 0
                    val barHeight = max(6, (barScore * 0.8).toInt())
                    val barColor = if (index == todayIndex) NeckWellAccent else NeckWellSurfaceBright
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .width(28.dp)
                                .height(barHeight.dp)
                                .background(barColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            day,
                            color = if (index == todayIndex) NeckWellAccent else NeckWellTextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (index == todayIndex) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TipBanner() {
    NeckWellCard {
        Row(
            Modifier.fillMaxWidth()
                .background(NeckWellAccentSurface, RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(Icons.Outlined.Lightbulb, NeckWellAccent, size = 42)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Keep your chin parallel to the ground",
                    color = NeckWellAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "This reduces neck strain and improves alignment.",
                    color = NeckWellTextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = NeckWellTextSecondary)
        }
    }
}
