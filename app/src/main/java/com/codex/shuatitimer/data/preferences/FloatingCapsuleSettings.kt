package com.codex.shuatitimer.data.preferences

import android.content.Context

/**
 * Small synchronous preferences used by the foreground service.
 *
 * DataStore remains the source for normal UI settings. The overlay service needs to decide
 * whether it may create a window during startup, so this opt-in is intentionally kept in a
 * dedicated SharedPreferences file that can be read without blocking the service main thread.
 */
class FloatingCapsuleSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    var enabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    fun savedPosition(): Position? {
        if (!preferences.contains(KEY_POSITION_X) || !preferences.contains(KEY_POSITION_Y)) {
            return null
        }
        return Position(
            x = preferences.getInt(KEY_POSITION_X, 0),
            y = preferences.getInt(KEY_POSITION_Y, 0)
        )
    }

    fun savePosition(x: Int, y: Int) {
        preferences.edit()
            .putInt(KEY_POSITION_X, x)
            .putInt(KEY_POSITION_Y, y)
            .apply()
    }

    data class Position(val x: Int, val y: Int)

    private companion object {
        const val PREFERENCES_NAME = "floating_timer_capsule"
        const val KEY_ENABLED = "enabled"
        const val KEY_POSITION_X = "position_x"
        const val KEY_POSITION_Y = "position_y"
    }
}
