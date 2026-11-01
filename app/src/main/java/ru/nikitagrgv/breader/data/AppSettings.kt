package ru.nikitagrgv.breader.data

import java.util.Locale

enum class AppTheme { DARK, LIGHT }

/** The screen edge along which the reader's scroll bar is shown. */
enum class SeekBarPosition {
    LEFT, RIGHT, TOP, BOTTOM;

    val isVertical: Boolean
        get() = this == LEFT || this == RIGHT
}

data class AppSettings(
    val theme: AppTheme = AppTheme.DARK,
    /** Book text size in sp. */
    val fontSize: Float = DEFAULT_FONT_SIZE,
    /**
     * Stretch the spaces so that lines end evenly at the right edge. Android places the selection
     * highlight on justified lines as if they weren't stretched, so it's off by a few words there.
     */
    val justify: Boolean = false,
    /** Split words at line ends; when off, lines are wrapped by whole words only. */
    val hyphenation: Boolean = false,
    val seekBarPosition: SeekBarPosition = SeekBarPosition.LEFT,
    val translation: TranslationSettings = TranslationSettings(),
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 18f
        const val MIN_FONT_SIZE = 12f
        const val MAX_FONT_SIZE = 32f
    }
}

/** Translation of the selected text by LLMs from OpenRouter. */
data class TranslationSettings(
    val apiKey: String = "",
    /** OpenRouter model IDs. Every one of them is asked, all at once. */
    val models: List<String> = emptyList(),
    /** Language of the books, in English as it's told to the models; blank to let them detect it. */
    val sourceLanguage: String = "",
    /** Language to translate into, in English; blank for the device language. */
    val targetLanguage: String = "",
    /** How many words before and after the selection are sent along with it, so the models see the context. */
    val contextWords: Int = DEFAULT_CONTEXT_WORDS,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && models.isNotEmpty()

    val effectiveTargetLanguage: String
        get() = targetLanguage.trim().ifEmpty { deviceLanguage() }

    companion object {
        const val DEFAULT_CONTEXT_WORDS = 30
        const val MAX_CONTEXT_WORDS = 100
        const val CONTEXT_WORDS_STEP = 5

        fun deviceLanguage(): String = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)
    }
}
