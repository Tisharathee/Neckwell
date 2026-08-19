package com.humblecoders.neckwell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.humblecoders.neckwell.ui.theme.AccentTealDark
import com.humblecoders.neckwell.ui.theme.BackgroundGray
import com.humblecoders.neckwell.ui.theme.BorderSoft
import com.humblecoders.neckwell.ui.theme.InfoBlue
import com.humblecoders.neckwell.ui.theme.MintSurface
import com.humblecoders.neckwell.ui.theme.TextDark
import com.humblecoders.neckwell.ui.theme.TextGray

@Composable
fun SettingsScreen(navController: NavController) {
    var notificationsEnabled by remember { mutableStateOf(true) }
    var hapticFeedback by remember { mutableStateOf(true) }
    var soundAlerts by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(BackgroundGray)) {
        NeckWellHeader("Settings", "Customize your experience", compact = true)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection("Notifications") {
                SettingsToggleItem(Icons.Outlined.NotificationsNone, "Push notifications", "Receive posture alerts and reminders", notificationsEnabled) { notificationsEnabled = it }
                SettingsToggleItem(Icons.Outlined.Vibration, "Haptic feedback", "Vibrate on poor posture detection", hapticFeedback) { hapticFeedback = it }
                SettingsToggleItem(Icons.Outlined.VolumeUp, "Sound alerts", "Play sound for posture warnings", soundAlerts) { soundAlerts = it }
            }

            SettingsSection("Device") {
                SettingsClickableItem(Icons.Outlined.Link, "Connected device", "NeckWell Sensor v2.1") { }
                SettingsClickableItem(Icons.Outlined.BatteryFull, "Battery status", "85% charged") { }
            }

            SettingsSection("Calibration") {
                SettingsClickableItem(Icons.Outlined.Tune, "Calibrate posture", "Set your neutral posture baseline") { navController.navigate("calibration") }
            }

            SettingsSection("About") {
                SettingsClickableItem(Icons.Outlined.Info, "App version", "1.0.0") { }
                SettingsClickableItem(Icons.Outlined.Description, "Privacy policy", "View our privacy policy") { }
                SettingsClickableItem(Icons.Outlined.Email, "Contact support", "Get help with NeckWell") { }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        content()
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    NeckWellCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, InfoBlue, size = 44)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = TextGray, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentTealDark,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = BorderSoft
                )
            )
        }
    }
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    NeckWellCard {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(icon, AccentTealDark, background = MintSurface, size = 44)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = TextGray, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextGray, modifier = Modifier.size(21.dp))
        }
    }
}
