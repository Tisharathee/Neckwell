package com.humblecoders.neckwell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun SettingsScreen(navController: NavController) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var hapticFeedback by remember { mutableStateOf(true) }
    var soundAlerts by remember { mutableStateOf(false) }

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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "⚙️ Settings",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Customize Your Experience",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Notifications Section
            Text(
                text = "Notifications",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            SettingsToggleItem(
                icon = "🔔",
                title = "Push Notifications",
                description = "Receive posture alerts and reminders",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )

            SettingsToggleItem(
                icon = "📳",
                title = "Haptic Feedback",
                description = "Vibrate on poor posture detection",
                checked = hapticFeedback,
                onCheckedChange = { hapticFeedback = it }
            )

            SettingsToggleItem(
                icon = "🔊",
                title = "Sound Alerts",
                description = "Play sound for posture warnings",
                checked = soundAlerts,
                onCheckedChange = { soundAlerts = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Device Section
            Text(
                text = "Device",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            SettingsClickableItem(
                icon = "🔗",
                title = "Connected Device",
                description = "NeckWell Sensor v2.1",
                onClick = { }
            )

            SettingsClickableItem(
                icon = "🔋",
                title = "Battery Status",
                description = "85% charged",
                onClick = { }
            )

            Spacer(modifier = Modifier.height(8.dp))
            // Calibration Section
            Text(
                text = "Calibration",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            SettingsClickableItem(
                icon = "🎯",
                title = "Calibrate Posture",
                description = "Set your neutral posture baseline",
                onClick = { navController.navigate("calibration") }
            )

            // About Section
            Text(
                text = "About",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )

            SettingsClickableItem(
                icon = "ℹ️",
                title = "App Version",
                description = "1.0.0",
                onClick = { }
            )

            SettingsClickableItem(
                icon = "📄",
                title = "Privacy Policy",
                description = "View our privacy policy",
                onClick = { }
            )

            SettingsClickableItem(
                icon = "📧",
                title = "Contact Support",
                description = "Get help with NeckWell",
                onClick = { }
            )
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: String,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 16.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF2196F3)
                )
            )
        }
    }
}

@Composable
fun SettingsClickableItem(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 28.sp,
                modifier = Modifier.padding(end = 16.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            Text(
                text = "›",
                fontSize = 24.sp,
                color = Color.Gray
            )
        }
    }
}