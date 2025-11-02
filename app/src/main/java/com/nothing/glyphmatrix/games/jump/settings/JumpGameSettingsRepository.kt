package com.nothing.glyphmatrix.games.jump.settings

import android.content.Context
import kotlin.math.max
import kotlin.math.min

object JumpGameSettingsRepository {
    private const val PREF_NAME = "jump_game_settings"
    private const val KEY_HORIZONTAL_SENSITIVITY = "horizontal_sensitivity"
    private const val DEFAULT_HORIZONTAL_SENSITIVITY = 0.4f
    private const val MIN_SENSITIVITY = 0.1f
    private const val MAX_SENSITIVITY = 1.0f

    fun getHorizontalSensitivity(context: Context): Float {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_HORIZONTAL_SENSITIVITY, DEFAULT_HORIZONTAL_SENSITIVITY)
            .coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
    }

    fun setHorizontalSensitivity(context: Context, value: Float) {
        val sanitized = value.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        context.applicationContext
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_HORIZONTAL_SENSITIVITY, sanitized)
            .apply()
    }

    fun defaultHorizontalSensitivity(): Float = DEFAULT_HORIZONTAL_SENSITIVITY
    fun minHorizontalSensitivity(): Float = MIN_SENSITIVITY
    fun maxHorizontalSensitivity(): Float = MAX_SENSITIVITY
}
