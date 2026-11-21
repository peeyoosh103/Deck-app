package com.example.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.models.AppSettings
import com.example.models.QuizQuestionCountOption
import com.example.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "memodeck_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_SHUFFLE = booleanPreferencesKey("default_shuffle")
        val DEFAULT_QUIZ_COUNT = stringPreferencesKey("default_quiz_count")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromNameOrDefault(prefs[Keys.THEME_MODE]),
            defaultShuffle = prefs[Keys.DEFAULT_SHUFFLE] ?: false,
            defaultQuizQuestionCount = QuizQuestionCountOption.fromNameOrDefault(prefs[Keys.DEFAULT_QUIZ_COUNT])
        )
    }

    suspend fun getSettingsOnce(): AppSettings = settingsFlow.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setDefaultShuffle(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_SHUFFLE] = enabled
        }
    }

    suspend fun setDefaultQuizQuestionCount(option: QuizQuestionCountOption) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_QUIZ_COUNT] = option.name
        }
    }

    suspend fun replaceSettings(settings: AppSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.DEFAULT_SHUFFLE] = settings.defaultShuffle
            prefs[Keys.DEFAULT_QUIZ_COUNT] = settings.defaultQuizQuestionCount.name
        }
    }

    suspend fun resetToDefaults() {
        context.settingsDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
