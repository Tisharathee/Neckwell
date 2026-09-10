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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val TAG = "NeckWell_Settings"

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    NeckWellPreferences.init(context)

    // Subscribe to live system listeners (e.g. Battery) on mount, cleanly unsubscribe on unmount
    DisposableEffect(context) {
        DeviceStatusManager.registerSystemListeners(context)
        onDispose {
            DeviceStatusManager.unregisterSystemListeners(context)
        }
    }

    val notificationsEnabled by NeckWellPreferences.notificationsEnabled.collectAsState()
    val hapticFeedback by NeckWellPreferences.hapticFeedbackEnabled.collectAsState()
    val soundAlerts by NeckWellPreferences.soundAlertsEnabled.collectAsState()
    val deviceStatus by DeviceStatusManager.deviceStatus.collectAsState()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        NeckWellPreferences.setNotificationsEnabled(context, isGranted)
        if (isGranted) {
            Log.i(TAG, "Notification permission granted by user. Triggering confirmation notification.")
            println("[NeckWell] Notification permission granted - triggering confirmation alert")
            AlertManager.triggerBadPostureNotification(
                context,
                title = "Notifications Active",
                message = "NeckWell posture notifications are now enabled."
            )
            Toast.makeText(context, "Notifications enabled", Toast.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "Notification permission denied by user.")
            println("[NeckWell] Notification permission denied")
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
                    if (checked) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasPermission) {
                                Log.i(TAG, "Requesting POST_NOTIFICATIONS permission from user")
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@SettingsToggleItem
                            }
                        }
                        NeckWellPreferences.setNotificationsEnabled(context, true)
                        Log.i(TAG, "Push notifications enabled. Triggering confirmation notification.")
                        println("[NeckWell] Push notifications enabled - triggering confirmation alert")
                        AlertManager.triggerBadPostureNotification(
                            context,
                            title = "Notifications Active",
                            message = "NeckWell posture notifications are now enabled."
                        )
                    } else {
                        NeckWellPreferences.setNotificationsEnabled(context, false)
                        Log.i(TAG, "Push notifications disabled by user.")
                        println("[NeckWell] Push notifications disabled")
                    }
                }

                SettingsToggleItem(
                    icon = Icons.Outlined.Vibration,
                    title = "Haptic feedback",
                    description = "Vibrate on poor posture detection",
                    checked = hapticFeedback
                ) { checked ->
                    NeckWellPreferences.setHapticFeedbackEnabled(context, checked)
                    if (checked) {
                        Log.i(TAG, "Haptic feedback enabled: triggering verification pulse")
                        println("[NeckWell] Haptic feedback enabled - triggering test vibration")
                        AlertManager.triggerHapticFeedback(context, 150L)
                    } else {
                        Log.i(TAG, "Haptic feedback disabled by user")
                        println("[NeckWell] Haptic feedback disabled")
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
                        Log.i(TAG, "Sound alerts enabled: playing verification beep")
                        AlertManager.playToneAlert(context, ToneGenerator.TONE_PROP_BEEP, 150)
                    } else {
                        Log.i(TAG, "Sound alerts disabled by user")
                    }
                }
            }

            SettingsSection("Device") {
                val connectionLabel = when (deviceStatus.connectionState) {
                    DeviceConnectionState.CONNECTED -> "Connected"
                    DeviceConnectionState.CONNECTING -> "Connecting…"
                    DeviceConnectionState.RECONNECTING -> "Reconnecting…"
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
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://neckwell.com/privacy")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not open privacy policy link: ${e.message}")
                        Toast.makeText(context, "Privacy Policy: All sensor data is processed locally on your device.", Toast.LENGTH_LONG).show()
                    }
                }

                SettingsClickableItem(
                    icon = Icons.Outlined.Email,
                    title = "Contact support",
                    description = "Get help with NeckWell"
                ) {
                    val supportEmail = "support@neckwell.com"
                    val subject = "NeckWell Support Request"
                    val body = "Device: ${deviceStatus.deviceName}\nApp Version: 1.0.0\n\nPlease describe your issue:\n"

                    try {
                        val mailtoIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:$supportEmail")
                            putExtra(Intent.EXTRA_SUBJECT, subject)
                            putExtra(Intent.EXTRA_TEXT, body)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(mailtoIntent)
                        Log.i(TAG, "Contact Support action fired: opened mail client for $supportEmail")
                        println("[NeckWell] Contact Support action fired: opened mail client")
                    } catch (e: Exception) {
                        Log.w(TAG, "Primary mailto intent failed (${e.message}), attempting fallback chooser", e)
                        try {
                            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "message/rfc822"
                                putExtra(Intent.EXTRA_EMAIL, arrayOf(supportEmail))
                                putExtra(Intent.EXTRA_SUBJECT, subject)
                                putExtra(Intent.EXTRA_TEXT, body)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(fallbackIntent, "Send Support Email").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            Log.i(TAG, "Contact Support action fired via fallback chooser for $supportEmail")
                            println("[NeckWell] Contact Support fallback chooser fired")
                        } catch (fallbackEx: Exception) {
                            Log.e(TAG, "Contact Support failed: unable to open email client on device", fallbackEx)
                            println("[NeckWell] Contact Support failed: ${fallbackEx.message}")
                            try {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Support Email", supportEmail)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "No email app found. Copied $supportEmail to clipboard.", Toast.LENGTH_LONG).show()
                            } catch (clipEx: Exception) {
                                Toast.makeText(context, "Please email us at $supportEmail", Toast.LENGTH_LONG).show()
                            }
                        }
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
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
