package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.EngineMode
import com.example.data.model.OutputFormat
import com.example.data.model.StudioSettings
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(name = "voxclone_studio_prefs")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENGINE_MODE = stringPreferencesKey("engine_mode")
        val API_ENDPOINT = stringPreferencesKey("api_endpoint")
        val API_KEY = stringPreferencesKey("api_key")
        val DEFAULT_LANGUAGE = stringPreferencesKey("default_language")
        val SIMILARITY = floatPreferencesKey("similarity")
        val SPEED = floatPreferencesKey("speed")
        val TEMPERATURE = floatPreferencesKey("temperature")
        val OUTPUT_FORMAT = stringPreferencesKey("output_format")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_SCRIPT = stringPreferencesKey("last_script")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        StudioSettings(
            engineMode = prefs[Keys.ENGINE_MODE]?.let { name ->
                EngineMode.entries.firstOrNull { it.name == name }
            } ?: EngineMode.ON_DEVICE_TTS,
            apiEndpoint = prefs[Keys.API_ENDPOINT] ?: "http://10.0.2.2:8000",
            apiKey = prefs[Keys.API_KEY] ?: "",
            defaultLanguage = prefs[Keys.DEFAULT_LANGUAGE] ?: "en",
            similarity = prefs[Keys.SIMILARITY] ?: 0.78f,
            speed = prefs[Keys.SPEED] ?: 1.0f,
            temperature = prefs[Keys.TEMPERATURE] ?: 0.65f,
            outputFormat = prefs[Keys.OUTPUT_FORMAT]?.let { ext ->
                OutputFormat.entries.firstOrNull { it.name == ext }
            } ?: OutputFormat.WAV,
            themeMode = prefs[Keys.THEME_MODE]?.let { name ->
                ThemeMode.entries.firstOrNull { it.name == name }
            } ?: ThemeMode.DARK
        )
    }

    val lastScriptFlow: Flow<String> = context.studioDataStore.data.map { prefs ->
        prefs[Keys.LAST_SCRIPT] ?: ""
    }

    suspend fun updateSettings(transform: (StudioSettings) -> StudioSettings) {
        context.studioDataStore.edit { prefs ->
            val current = StudioSettings(
                engineMode = prefs[Keys.ENGINE_MODE]?.let { n -> EngineMode.entries.firstOrNull { it.name == n } }
                    ?: EngineMode.ON_DEVICE_TTS,
                apiEndpoint = prefs[Keys.API_ENDPOINT] ?: "http://10.0.2.2:8000",
                apiKey = prefs[Keys.API_KEY] ?: "",
                defaultLanguage = prefs[Keys.DEFAULT_LANGUAGE] ?: "en",
                similarity = prefs[Keys.SIMILARITY] ?: 0.78f,
                speed = prefs[Keys.SPEED] ?: 1.0f,
                temperature = prefs[Keys.TEMPERATURE] ?: 0.65f,
                outputFormat = prefs[Keys.OUTPUT_FORMAT]?.let { n -> OutputFormat.entries.firstOrNull { it.name == n } }
                    ?: OutputFormat.WAV,
                themeMode = prefs[Keys.THEME_MODE]?.let { n -> ThemeMode.entries.firstOrNull { it.name == n } }
                    ?: ThemeMode.DARK
            )
            val updated = transform(current)
            prefs[Keys.ENGINE_MODE] = updated.engineMode.name
            prefs[Keys.API_ENDPOINT] = updated.apiEndpoint
            prefs[Keys.API_KEY] = updated.apiKey
            prefs[Keys.DEFAULT_LANGUAGE] = updated.defaultLanguage
            prefs[Keys.SIMILARITY] = updated.similarity
            prefs[Keys.SPEED] = updated.speed
            prefs[Keys.TEMPERATURE] = updated.temperature
            prefs[Keys.OUTPUT_FORMAT] = updated.outputFormat.name
            prefs[Keys.THEME_MODE] = updated.themeMode.name
        }
    }

    suspend fun saveLastScript(script: String) {
        context.studioDataStore.edit { prefs ->
            prefs[Keys.LAST_SCRIPT] = script
        }
    }
}
