package com.humblecoders.neckwell

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NeckWellPreferences {
    private const val PREFS_NAME = "neckwell_preferences"
    private const val KEY_NOTIFICATIONS = "notifications_enabled"
    private const val KEY_HAPTIC = "haptic_feedback_enabled"
    private const val KEY_SOUND = "sound_alerts_enabled"

    private var sharedPrefs: SharedPreferences? = null

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _hapticFeedbackEnabled = MutableStateFlow(true)
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    private val _soundAlertsEnabled = MutableStateFlow(false)
    val soundAlertsEnabled: StateFlow<Boolean> = _soundAlertsEnabled.asStateFlow()

    fun init(context: Context) {
        if (sharedPrefs == null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs = prefs
            _notificationsEnabled.value = prefs.getBoolean(KEY_NOTIFICATIONS, true)
            _hapticFeedbackEnabled.value = prefs.getBoolean(KEY_HAPTIC, true)
            _soundAlertsEnabled.value = prefs.getBoolean(KEY_SOUND, false)
        }
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        init(context)
        sharedPrefs?.edit()?.putBoolean(KEY_NOTIFICATIONS, enabled)?.apply()
        _notificationsEnabled.value = enabled
    }

    fun setHapticFeedbackEnabled(context: Context, enabled: Boolean) {
        init(context)
        sharedPrefs?.edit()?.putBoolean(KEY_HAPTIC, enabled)?.apply()
        _hapticFeedbackEnabled.value = enabled
    }

    fun setSoundAlertsEnabled(context: Context, enabled: Boolean) {
        init(context)
        sharedPrefs?.edit()?.putBoolean(KEY_SOUND, enabled)?.apply()
        _soundAlertsEnabled.value = enabled
    }
}
