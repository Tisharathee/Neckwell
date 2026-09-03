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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    NeckWellPreferences.init(context)

    val notificationsEnabled by NeckWellPreferences.notificationsEnabled.collectAsState()
    val hapticFeedback by NeckWellPreferences.hapticFeedbackEnabled.collectAsState()
    val soundAlerts by NeckWellPreferences.soundAlertsEnabled.collectAsState()
    val deviceStatus by DeviceStatusManager.deviceStatus.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        NeckWellPreferences.setNotificationsEnabled(context, isGranted)
        if (!isGranted) {
            Toast.makeText(context, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().background(BackgroundGray)) {
        NeckWellHeader("Settings", "Customize your experience", compact = true)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsSection("Notifications") {
                SettingsToggleItem(
                    icon = Icons.Outlined.NotificationsNone,
                    title = "Push notifications",
                    description = "Receive posture alerts and reminders",
                    checked = notificationsEnabled
                ) { checked ->
                    if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasPermission) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@SettingsToggleItem
                        }
                    }
                    NeckWellPreferences.setNotificationsEnabled(context, checked)
                }

                SettingsToggleItem(
                    icon = Icons.Outlined.Vibration,
                    title = "Haptic feedback",
                    description = "Vibrate on poor posture detection",
                    checked = hapticFeedback
                ) { checked ->
                    NeckWellPreferences.setHapticFeedbackEnabled(context, checked)
                    if (checked) {
                        AlertManager.triggerHapticFeedback(context, 100L)
                    }
                }

                SettingsToggleItem(
                    icon = Icons.Outlined.VolumeUp,
                    title = "Sound alerts",
                    description = "Play sound for posture warnings",
                    checked = soundAlerts
                ) { checked ->
                    NeckWellPreferences.setSoundAlertsEnabled(context, checked)
                    if (checked) {
                        AlertManager.playToneAlert(context, ToneGenerator.TONE_PROP_BEEP, 150)
                    }
                }
            }

            SettingsSection("Device") {
                val connectionLabel = when (deviceStatus.connectionState) {
                    DeviceConnectionState.CONNECTED -> "Connected"
                    DeviceConnectionState.CONNECTING -> "Connecting…"
                    DeviceConnectionState.DISCONNECTED -> "Disconnected"
                }
                SettingsClickableItem(
                    icon = Icons.Outlined.Link,
                    title = "Connected device",
                    description = "${deviceStatus.deviceName} • $connectionLabel"
                ) {
                    DeviceStatusManager.toggleConnection()
                    val targetMsg = if (deviceStatus.connectionState == DeviceConnectionState.CONNECTED) "Disconnecting device…" else "Connecting to sensor…"
                    Toast.makeText(context, targetMsg, Toast.LENGTH_SHORT).show()
                }

                val batteryColor = if (deviceStatus.isLowBattery) com.humblecoders.neckwell.ui.theme.AlertCoral else AccentTealDark
                SettingsClickableItem(
                    icon = Icons.Outlined.BatteryFull,
                    title = "Battery status",
                    description = "${deviceStatus.batteryLevel}% charged${if (deviceStatus.isLowBattery) " • Low Battery" else ""}",
                    iconTint = batteryColor
                ) {
                    Toast.makeText(context, "Battery: ${deviceStatus.batteryLevel}%", Toast.LENGTH_SHORT).show()
                }
            }

            SettingsSection("Calibration") {
                SettingsClickableItem(
                    icon = Icons.Outlined.Tune,
                    title = "Calibrate posture",
                    description = "Set your neutral posture baseline"
                ) {
                    navController.navigate("calibration")
                }
            }

            SettingsSection("About") {
                SettingsClickableItem(
                    icon = Icons.Outlined.Info,
                    title = "App version",
                    description = "1.0.0 (Build 1)"
                ) {
                    Toast.makeText(context, "NeckWell v1.0.0", Toast.LENGTH_SHORT).show()
                }

                SettingsClickableItem(
                    icon = Icons.Outlined.Description,
                    title = "Privacy policy",
                    description = "View our privacy policy"
                ) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://neckwell.com/privacy"))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Privacy Policy: All sensor data is processed locally on your device.", Toast.LENGTH_LONG).show()
                    }
                }

                SettingsClickableItem(
                    icon = Icons.Outlined.Email,
                    title = "Contact support",
                    description = "Get help with NeckWell"
                ) {
                    try {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:support@neckwell.com")
                            putExtra(Intent.EXTRA_SUBJECT, "NeckWell Support Request")
                            putExtra(Intent.EXTRA_TEXT, "Device: ${deviceStatus.deviceName}\nApp Version: 1.0.0\n\nPlease describe your issue:\n")
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Please email us at support@neckwell.com", Toast.LENGTH_LONG).show()
                    }
                }
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
    iconTint: Color = AccentTealDark,
    onClick: () -> Unit
) {
    NeckWellCard {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(icon, iconTint, background = MintSurface, size = 44)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = TextGray, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextGray, modifier = Modifier.size(21.dp))
        }
    }
}
