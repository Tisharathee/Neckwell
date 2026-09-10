package com.humblecoders.neckwell

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object AlertManager {
    private const val TAG = "NeckWell_AlertManager"
    const val CHANNEL_ID = "neckwell_posture_alerts"
    private const val CHANNEL_NAME = "Posture Alerts"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when poor posture is detected"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun triggerBadPostureNotification(
        context: Context,
        title: String = "Posture Alert",
        message: String = "Poor posture detected. Please straighten your neck."
    ): Boolean {
        NeckWellPreferences.init(context)
        if (!NeckWellPreferences.notificationsEnabled.value) {
            Log.d(TAG, "Notification skipped: notifications are disabled in preferences")
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                Log.w(TAG, "Notification skipped: POST_NOTIFICATIONS permission not granted")
                return false
            }
        }

        val notificationManagerCompat = NotificationManagerCompat.from(context)
        if (!notificationManagerCompat.areNotificationsEnabled()) {
            Log.w(TAG, "Notification warning: Notifications are blocked at system or channel level for this app")
        }

        createNotificationChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        return try {
            notificationManagerCompat.notify(NOTIFICATION_ID, notification)
            Log.i(TAG, "Notification triggered successfully: title='$title', message='$message'")
            println("[NeckWell] Notification fired: $title - $message")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Missing POST_NOTIFICATIONS permission when posting notification", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification: ${e.message}", e)
            false
        }
    }

    fun triggerHapticFeedback(context: Context, durationMs: Long = 400L) {
        NeckWellPreferences.init(context)
        if (!NeckWellPreferences.hapticFeedbackEnabled.value) {
            Log.d(TAG, "Haptic feedback skipped: disabled in preferences")
            return
        }

        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator == null || !vibrator.hasVibrator()) {
                Log.w(TAG, "Haptic feedback requested, but device has no vibrator motor available")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attributes = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                    vibrator.vibrate(effect, attributes)
                } else {
                    vibrator.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
            Log.i(TAG, "Haptic feedback triggered successfully (duration: ${durationMs}ms)")
            println("[NeckWell] Haptic feedback fired: ${durationMs}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger haptic feedback: ${e.message}", e)
        }
    }

    fun playToneAlert(
        context: Context? = null,
        toneType: Int = ToneGenerator.TONE_PROP_BEEP,
        durationMs: Int = 250
    ) {
        if (context != null) {
            NeckWellPreferences.init(context)
            if (!NeckWellPreferences.soundAlertsEnabled.value) return
        }
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGenerator.startTone(toneType, durationMs)
            Log.i(TAG, "Tone alert played (type: $toneType, duration: ${durationMs}ms)")
            println("[NeckWell] Tone alert played: $durationMs ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play tone alert: ${e.message}", e)
        }
    }
}
