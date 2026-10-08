package com.codex.shuatitimer.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.codex.shuatitimer.data.model.Subject
import com.codex.shuatitimer.data.model.TimerUiStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Manages user preferences using DataStore.
 */
class UserPreferences(private val context: Context) {

    companion object {
        private val KEY_LAST_SUBJECT = stringPreferencesKey("last_subject")
        private val KEY_KEEP_SCREEN_ON = intPreferencesKey("keep_screen_on")
        private val KEY_LAST_COUNTDOWN_MINUTES = intPreferencesKey("last_countdown_minutes")
        private val KEY_LAST_PLANNED_QUESTIONS = intPreferencesKey("last_planned_questions")
        private val KEY_PLANNED_QUESTIONS_BY_MODULE = stringPreferencesKey("planned_questions_by_module")
        private val KEY_TIMER_UI_STYLE = stringPreferencesKey("timer_ui_style")
    }

    val lastSubject: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_SUBJECT] ?: Subject.ZILIAO_FENXI.displayName
    }

    val keepScreenOn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        (prefs[KEY_KEEP_SCREEN_ON] ?: 0) == 1
    }

    val lastCountdownMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_LAST_COUNTDOWN_MINUTES] ?: 30
    }

    val lastPlannedQuestions: Flow<Int?> = context.dataStore.data.map { prefs ->
        val v = prefs[KEY_LAST_PLANNED_QUESTIONS]
        if (v == null || v == 0) null else v
    }

    val plannedQuestionsByModule: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        runCatching {
            val json = JSONObject(prefs[KEY_PLANNED_QUESTIONS_BY_MODULE] ?: "{}")
            buildMap {
                json.keys().forEach { module ->
                    json.optInt(module, 0).takeIf { it > 0 }?.let { put(module, it) }
                }
            }
        }.getOrDefault(emptyMap())
    }

    val timerUiStyle: Flow<TimerUiStyle> = context.dataStore.data.map { prefs ->
        runCatching {
            TimerUiStyle.valueOf(prefs[KEY_TIMER_UI_STYLE] ?: TimerUiStyle.CLASSIC.name)
        }.getOrDefault(TimerUiStyle.CLASSIC)
    }

    suspend fun setLastSubject(subject: String) {
        context.dataStore.edit { it[KEY_LAST_SUBJECT] = subject }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_KEEP_SCREEN_ON] = if (enabled) 1 else 0 }
    }

    suspend fun setLastCountdownMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_LAST_COUNTDOWN_MINUTES] = minutes }
    }

    suspend fun setLastPlannedQuestions(count: Int?) {
        context.dataStore.edit {
            if (count != null && count > 0) {
                it[KEY_LAST_PLANNED_QUESTIONS] = count
            } else {
                it.remove(KEY_LAST_PLANNED_QUESTIONS)
            }
        }
    }

    suspend fun setPlannedQuestionsForModule(module: String, count: Int?) {
        context.dataStore.edit { prefs ->
            val json = runCatching {
                JSONObject(prefs[KEY_PLANNED_QUESTIONS_BY_MODULE] ?: "{}")
            }.getOrDefault(JSONObject())
            if (count != null && count > 0) json.put(module, count) else json.remove(module)
            prefs[KEY_PLANNED_QUESTIONS_BY_MODULE] = json.toString()
        }
    }

    suspend fun setTimerUiStyle(style: TimerUiStyle) {
        context.dataStore.edit { it[KEY_TIMER_UI_STYLE] = style.name }
    }
}
