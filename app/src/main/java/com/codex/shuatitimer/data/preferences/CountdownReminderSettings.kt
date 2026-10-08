package com.codex.shuatitimer.data.preferences

import android.content.Context
import com.codex.shuatitimer.data.model.CountdownReminderMode

/** Read synchronously by the deadline receiver so displaying an alert needs no async load. */
class CountdownReminderSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("countdown_reminder", Context.MODE_PRIVATE)

    var mode: CountdownReminderMode
        get() = runCatching {
            CountdownReminderMode.valueOf(preferences.getString("mode", CountdownReminderMode.MESSAGE.name).orEmpty())
        }.getOrDefault(CountdownReminderMode.MESSAGE)
        set(value) {
            preferences.edit().putString("mode", value.name).apply()
        }
}
