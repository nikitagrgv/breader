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
import ru.nikitagrgv.breader.dictionary.OnlineDictionary
import ru.nikitagrgv.breader.translation.MachineTranslationService
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
                font = prefs[READER_FONT]?.let { name -> ReaderFont.entries.find { it.name == name } } ?: defaults.font,
                lineSpacing = prefs[LINE_SPACING] ?: defaults.lineSpacing,
                margins = prefs[MARGINS] ?: defaults.margins,
                justify = prefs[JUSTIFY] ?: defaults.justify,
                hyphenation = prefs[HYPHENATION] ?: defaults.hyphenation,
                seekBarPosition = prefs[SEEK_BAR_POSITION]
                    ?.let { name -> SeekBarPosition.entries.find { it.name == name } }
                    ?: defaults.seekBarPosition,
                moreSelectionActions = prefs[MORE_SELECTION_ACTIONS] ?: defaults.moreSelectionActions,
                selectionFadeMs = prefs[SELECTION_FADE_MS] ?: defaults.selectionFadeMs,
                selectionHistorySize = prefs[SELECTION_HISTORY_SIZE] ?: defaults.selectionHistorySize,
                hideCommonWords = prefs[HIDE_COMMON_WORDS] ?: defaults.hideCommonWords,
                translation = prefs.translationSettings(),
                dictionaries = prefs.dictionarySettings(),
            )
        }

    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { it[THEME] = theme.name }
    }

    suspend fun setFontSize(size: Float) {
        dataStore.edit { it[FONT_SIZE] = size.coerceIn(AppSettings.MIN_FONT_SIZE, AppSettings.MAX_FONT_SIZE) }
    }

    suspend fun setFont(font: ReaderFont) {
        dataStore.edit { it[READER_FONT] = font.name }
    }

    suspend fun setLineSpacing(spacing: Float) {
        dataStore.edit { it[LINE_SPACING] = spacing.coerceIn(AppSettings.MIN_LINE_SPACING, AppSettings.MAX_LINE_SPACING) }
    }

    suspend fun setMargins(margins: Int) {
        dataStore.edit { it[MARGINS] = margins.coerceIn(0, AppSettings.MAX_MARGINS) }
    }

    suspend fun setJustify(enabled: Boolean) {
        dataStore.edit { it[JUSTIFY] = enabled }
    }

    suspend fun setHyphenation(enabled: Boolean) {
        dataStore.edit { it[HYPHENATION] = enabled }
    }

    suspend fun setMoreSelectionActions(open: Boolean) {
        dataStore.edit { it[MORE_SELECTION_ACTIONS] = open }
    }

    suspend fun setSelectionFade(ms: Int) {
        dataStore.edit { it[SELECTION_FADE_MS] = ms.coerceIn(0, AppSettings.MAX_SELECTION_FADE_MS) }
    }

    suspend fun setSelectionHistorySize(size: Int) {
        dataStore.edit {
            it[SELECTION_HISTORY_SIZE] = size.coerceIn(0, AppSettings.MAX_SELECTION_HISTORY_SIZE)
        }
    }

    suspend fun setSeekBarPosition(position: SeekBarPosition) {
        dataStore.edit { it[SEEK_BAR_POSITION] = position.name }
    }

    suspend fun setHideCommonWords(hide: Boolean) {
        dataStore.edit { it[HIDE_COMMON_WORDS] = hide }
    }

    /** [transform] gets the stored settings, so quick successive changes don't overwrite each other. */
    suspend fun updateTranslation(transform: (TranslationSettings) -> TranslationSettings) {
        dataStore.edit { prefs -> prefs.setTranslationSettings(transform(prefs.translationSettings())) }
    }

    suspend fun updateDictionaries(transform: (DictionarySettings) -> DictionarySettings) {
        dataStore.edit { prefs ->
            val settings = transform(prefs.dictionarySettings())
            prefs[ONLINE_DICTIONARIES] = settings.online.joinToString(",") { it.name }
            prefs[YANDEX_KEY] = settings.yandexKey.trim()
            prefs[DISABLED_DICTIONARIES] = settings.disabled.joinToString("\n")
            prefs[DICTIONARY_ORDER] = settings.order.joinToString("\n")
        }
    }

    private fun Preferences.dictionarySettings(): DictionarySettings {
        val defaults = DictionarySettings()
        return DictionarySettings(
            online = this[ONLINE_DICTIONARIES]
                ?.split(',')
                ?.mapNotNullTo(HashSet()) { name -> OnlineDictionary.entries.find { it.name == name } }
                ?: defaults.online,
            yandexKey = this[YANDEX_KEY] ?: defaults.yandexKey,
            disabled = this[DISABLED_DICTIONARIES]?.split('\n')?.filterTo(HashSet()) { it.isNotEmpty() } ?: defaults.disabled,
            order = this[DICTIONARY_ORDER]?.split('\n')?.filter { it.isNotEmpty() } ?: defaults.order,
        )
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
            autoTranslate = this[AUTO_TRANSLATE] ?: defaults.autoTranslate,
            machineService = this[MACHINE_TRANSLATION]
                ?.let { name -> MachineTranslationService.entries.find { it.name == name } }
                ?: defaults.machineService,
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
        this[AUTO_TRANSLATE] = settings.autoTranslate
        this[MACHINE_TRANSLATION] = settings.machineService.name
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val READER_FONT = stringPreferencesKey("reader_font")
        val LINE_SPACING = floatPreferencesKey("line_spacing")
        val MARGINS = intPreferencesKey("margins")
        val JUSTIFY = booleanPreferencesKey("justify")
        val HYPHENATION = booleanPreferencesKey("hyphenation")
        val SEEK_BAR_POSITION = stringPreferencesKey("seek_bar_position")
        val MORE_SELECTION_ACTIONS = booleanPreferencesKey("more_selection_actions")
        val SELECTION_FADE_MS = intPreferencesKey("selection_fade_ms")
        val SELECTION_HISTORY_SIZE = intPreferencesKey("selection_history_size")
        val HIDE_COMMON_WORDS = booleanPreferencesKey("hide_common_words")
        val OPENROUTER_KEY = stringPreferencesKey("openrouter_key")
        val TRANSLATION_MODELS = stringPreferencesKey("translation_models")
        val SOURCE_LANGUAGE = stringPreferencesKey("source_language")
        val TARGET_LANGUAGE = stringPreferencesKey("target_language")
        val CONTEXT_WORDS = intPreferencesKey("context_words")
        val TRANSLATION_CACHE_SIZE = intPreferencesKey("translation_cache_size")
        val AUTO_TRANSLATE = booleanPreferencesKey("auto_translate")
        val MACHINE_TRANSLATION = stringPreferencesKey("machine_translation")
        val ONLINE_DICTIONARIES = stringPreferencesKey("online_dictionaries")
        val YANDEX_KEY = stringPreferencesKey("yandex_dictionary_key")
        val DISABLED_DICTIONARIES = stringPreferencesKey("disabled_dictionaries")

        // A string rather than a string set: the order is the user's.
        val DICTIONARY_ORDER = stringPreferencesKey("dictionary_order")
    }
}
