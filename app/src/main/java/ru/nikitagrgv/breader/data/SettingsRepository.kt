package ru.nikitagrgv.breader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** User preferences. There must be a single instance per process (DataStore requirement). */
class SettingsRepository(context: Context) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.applicationContext.preferencesDataStoreFile("settings")
    }

    val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                theme = prefs[THEME]?.let { name -> AppTheme.entries.find { it.name == name } } ?: defaults.theme,
                fontSize = prefs[FONT_SIZE] ?: defaults.fontSize,
                justify = prefs[JUSTIFY] ?: defaults.justify,
                hyphenation = prefs[HYPHENATION] ?: defaults.hyphenation,
                seekBarPosition = prefs[SEEK_BAR_POSITION]
                    ?.let { name -> SeekBarPosition.entries.find { it.name == name } }
                    ?: defaults.seekBarPosition,
                translation = prefs.translationSettings(),
            )
        }

    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[THEME] = theme.name }
    }

    suspend fun setFontSize(size: Float) {
        dataStore.edit { it[FONT_SIZE] = size.coerceIn(AppSettings.MIN_FONT_SIZE, AppSettings.MAX_FONT_SIZE) }
    }

    suspend fun setJustify(enabled: Boolean) {
        dataStore.edit { it[JUSTIFY] = enabled }
    }

    suspend fun setHyphenation(enabled: Boolean) {
        dataStore.edit { it[HYPHENATION] = enabled }
    }

    suspend fun setSeekBarPosition(position: SeekBarPosition) {
        dataStore.edit { it[SEEK_BAR_POSITION] = position.name }
    }

    /** [transform] gets the stored settings, so quick successive changes don't overwrite each other. */
    suspend fun updateTranslation(transform: (TranslationSettings) -> TranslationSettings) {
        dataStore.edit { prefs -> prefs.setTranslationSettings(transform(prefs.translationSettings())) }
    }

    private fun Preferences.translationSettings(): TranslationSettings {
        val defaults = TranslationSettings()
        return TranslationSettings(
            apiKey = this[OPENROUTER_KEY] ?: defaults.apiKey,
            models = this[TRANSLATION_MODELS]?.split('\n')?.filter { it.isNotEmpty() } ?: defaults.models,
            sourceLanguage = this[SOURCE_LANGUAGE] ?: defaults.sourceLanguage,
            targetLanguage = this[TARGET_LANGUAGE] ?: defaults.targetLanguage,
            contextWords = this[CONTEXT_WORDS] ?: defaults.contextWords,
            cacheSize = this[TRANSLATION_CACHE_SIZE] ?: defaults.cacheSize,
        )
    }

    private fun MutablePreferences.setTranslationSettings(settings: TranslationSettings) {
        this[OPENROUTER_KEY] = settings.apiKey.trim()
        // A string rather than a string set: the order is the user's.
        this[TRANSLATION_MODELS] = settings.models.joinToString("\n")
        this[SOURCE_LANGUAGE] = settings.sourceLanguage.trim()
        this[TARGET_LANGUAGE] = settings.targetLanguage.trim()
        this[CONTEXT_WORDS] = settings.contextWords.coerceIn(0, TranslationSettings.MAX_CONTEXT_WORDS)
        this[TRANSLATION_CACHE_SIZE] = settings.cacheSize.coerceIn(0, TranslationSettings.MAX_CACHE_SIZE)
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val JUSTIFY = booleanPreferencesKey("justify")
        val HYPHENATION = booleanPreferencesKey("hyphenation")
        val SEEK_BAR_POSITION = stringPreferencesKey("seek_bar_position")
        val OPENROUTER_KEY = stringPreferencesKey("openrouter_key")
        val TRANSLATION_MODELS = stringPreferencesKey("translation_models")
        val SOURCE_LANGUAGE = stringPreferencesKey("source_language")
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val CONTEXT_WORDS = intPreferencesKey("context_words")
        val TRANSLATION_CACHE_SIZE = intPreferencesKey("translation_cache_size")
    }
}
