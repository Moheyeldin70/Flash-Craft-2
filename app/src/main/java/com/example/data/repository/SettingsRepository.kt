package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.SrsAlgorithm
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "flashcraft_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ALGORITHM = stringPreferencesKey("srs_algorithm")
        val TARGET_RETENTION = floatPreferencesKey("target_retention")
        val NEW_CARDS_PER_DAY = intPreferencesKey("new_cards_per_day")
        val DAILY_REVIEW_GOAL = intPreferencesKey("daily_review_goal")
        val SHOW_INTERVAL_PREVIEWS = booleanPreferencesKey("show_interval_previews")
        val AUTO_PLAY_AUDIO = booleanPreferencesKey("auto_play_audio")
        val SHOW_HINTS_AUTO = booleanPreferencesKey("show_hints_auto")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val settings: Flow<UserSettings> = context.applicationContext.settingsDataStore.data.map { prefs ->
        UserSettings(
            algorithm = SrsAlgorithm.fromRaw(prefs[Keys.ALGORITHM]),
            targetRetention = (prefs[Keys.TARGET_RETENTION] ?: 0.90f).coerceIn(0.80f, 0.97f),
            newCardsPerDay = (prefs[Keys.NEW_CARDS_PER_DAY] ?: 20).coerceIn(5, 100),
            dailyReviewGoal = (prefs[Keys.DAILY_REVIEW_GOAL] ?: 30).coerceIn(5, 200),
            showIntervalPreviews = prefs[Keys.SHOW_INTERVAL_PREVIEWS] ?: true,
            autoPlayAudio = prefs[Keys.AUTO_PLAY_AUDIO] ?: false,
            showHintsAutomatically = prefs[Keys.SHOW_HINTS_AUTO] ?: false,
            themeMode = ThemeMode.fromRaw(prefs[Keys.THEME_MODE])
        )
    }

    suspend fun updateAlgorithm(algorithm: SrsAlgorithm) {
        context.applicationContext.settingsDataStore.edit { it[Keys.ALGORITHM] = algorithm.name }
    }

    suspend fun updateTargetRetention(retention: Float) {
        context.applicationContext.settingsDataStore.edit {
            it[Keys.TARGET_RETENTION] = retention.coerceIn(0.80f, 0.97f)
        }
    }

    suspend fun updateNewCardsPerDay(count: Int) {
        context.applicationContext.settingsDataStore.edit {
            it[Keys.NEW_CARDS_PER_DAY] = count.coerceIn(5, 100)
        }
    }

    suspend fun updateDailyReviewGoal(goal: Int) {
        context.applicationContext.settingsDataStore.edit {
            it[Keys.DAILY_REVIEW_GOAL] = goal.coerceIn(5, 200)
        }
    }

    suspend fun updateShowIntervalPreviews(enabled: Boolean) {
        context.applicationContext.settingsDataStore.edit {
            it[Keys.SHOW_INTERVAL_PREVIEWS] = enabled
        }
    }

    suspend fun updateAutoPlayAudio(enabled: Boolean) {
        context.applicationContext.settingsDataStore.edit {
            it[Keys.AUTO_PLAY_AUDIO] = enabled
        }
    }

    suspend fun updateShowHintsAutomatically(enabled: Boolean) {
        context.applicationContext.settingsDataStore.edit {
            it[Keys.SHOW_HINTS_AUTO] = enabled
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.applicationContext.settingsDataStore.edit {
            it[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun resetDefaults() {
        context.applicationContext.settingsDataStore.edit { it.clear() }
    }
}
