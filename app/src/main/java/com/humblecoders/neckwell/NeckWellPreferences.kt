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
    private const val KEY_CVA_THRESHOLD = "cva_threshold"

    private var sharedPrefs: SharedPreferences? = null

    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _hapticFeedbackEnabled = MutableStateFlow(true)
    val hapticFeedbackEnabled: StateFlow<Boolean> = _hapticFeedbackEnabled.asStateFlow()

    private val _soundAlertsEnabled = MutableStateFlow(false)
    val soundAlertsEnabled: StateFlow<Boolean> = _soundAlertsEnabled.asStateFlow()

    private val _cvaThreshold = MutableStateFlow(PostureAnalyzer.CVA_THRESHOLD_DEFAULT)
    val cvaThreshold: StateFlow<Float> = _cvaThreshold.asStateFlow()

    fun init(context: Context) {
        if (sharedPrefs == null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPrefs = prefs
            _notificationsEnabled.value = prefs.getBoolean(KEY_NOTIFICATIONS, true)
            _hapticFeedbackEnabled.value = prefs.getBoolean(KEY_HAPTIC, true)
            _soundAlertsEnabled.value = prefs.getBoolean(KEY_SOUND, false)
            val savedThreshold = prefs.getFloat(KEY_CVA_THRESHOLD, PostureAnalyzer.CVA_THRESHOLD_DEFAULT)
                .coerceIn(PostureAnalyzer.CVA_THRESHOLD_MIN, PostureAnalyzer.CVA_THRESHOLD_MAX)
            _cvaThreshold.value = savedThreshold
            PostureAnalyzer.cvaThreshold = savedThreshold
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

    fun setCvaThreshold(context: Context, threshold: Float) {
        init(context)
        val clamped = threshold.coerceIn(PostureAnalyzer.CVA_THRESHOLD_MIN, PostureAnalyzer.CVA_THRESHOLD_MAX)
        sharedPrefs?.edit()?.putFloat(KEY_CVA_THRESHOLD, clamped)?.apply()
        _cvaThreshold.value = clamped
        PostureAnalyzer.cvaThreshold = clamped
    }
}
