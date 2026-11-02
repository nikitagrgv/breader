package ru.nikitagrgv.breader.data

import java.util.Locale

enum class AppTheme(val isDark: Boolean) {
    DARK(isDark = true),

    /** Black background, for OLED screens; the text isn't pure white, which looks blurred on black. */
    BLACK(isDark = true),
    LIGHT(isDark = false),

    /** Warm paper colors: less glare than white. */
    SEPIA(isDark = false),
}

/** The typeface of the book text. */
enum class ReaderFont {
    /** The system sans serif, Roboto. */
    SANS,

    /** The system serif, Noto Serif. */
    SERIF,

    /** A serif made for reading e-books, by Google for Play Books. */
    LITERATA,

    /** A sans serif made for low-vision readers by the Braille Institute; Latin letters only. */
    ATKINSON,
}

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
    val font: ReaderFont = ReaderFont.SANS,
    /** Line height of the book text relative to its size. */
    val lineSpacing: Float = DEFAULT_LINE_SPACING,
    /** Space at the left and right of the book text, in dp. */
    val margins: Int = DEFAULT_MARGINS,
    /**
     * Stretch the spaces so that lines end evenly at the right edge. Android places the selection
     * highlight on justified lines as if they weren't stretched, so it's off by a few words there.
     */
    val justify: Boolean = false,
    /** Split words at line ends; when off, lines are wrapped by whole words only. */
    val hyphenation: Boolean = false,
    val seekBarPosition: SeekBarPosition = SeekBarPosition.LEFT,
    /** Whether the extra row of the reader's selection actions is open; it stays as the user left it. */
    val moreSelectionActions: Boolean = false,
    /** How long the place of the selection takes to fade out after a dialog about it is closed; 0 for not showing it. */
    val selectionFadeMs: Int = DEFAULT_SELECTION_FADE_MS,
    /** How many selections each book's history keeps. */
    val selectionHistorySize: Int = DEFAULT_SELECTION_HISTORY_SIZE,
    val translation: TranslationSettings = TranslationSettings(),
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 18f
        const val MIN_FONT_SIZE = 12f
        const val MAX_FONT_SIZE = 32f
        const val DEFAULT_LINE_SPACING = 1.4f
        const val MIN_LINE_SPACING = 1f
        const val MAX_LINE_SPACING = 2f
        const val DEFAULT_MARGINS = 12
        const val MAX_MARGINS = 40
        const val MARGINS_STEP = 4
        const val DEFAULT_SELECTION_FADE_MS = 300
        const val MAX_SELECTION_FADE_MS = 2000
        const val SELECTION_FADE_STEP_MS = 50
        const val DEFAULT_SELECTION_HISTORY_SIZE = 200
        const val MIN_SELECTION_HISTORY_SIZE = 50
        const val MAX_SELECTION_HISTORY_SIZE = 1000
        const val SELECTION_HISTORY_SIZE_STEP = 50
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
    /** How many recently selected fragments keep their translations, so selecting one again needs no requests. */
    val cacheSize: Int = DEFAULT_CACHE_SIZE,
    /** Translate the selected text at once, showing it at the top of the reader; otherwise only on request. */
    val autoTranslate: Boolean = true,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && models.isNotEmpty()

    val effectiveTargetLanguage: String
        get() = targetLanguage.trim().ifEmpty { deviceLanguage() }

    companion object {
        const val DEFAULT_CONTEXT_WORDS = 30
        const val MAX_CONTEXT_WORDS = 100
        const val CONTEXT_WORDS_STEP = 5
        const val DEFAULT_CACHE_SIZE = 20
        const val MAX_CACHE_SIZE = 200
        const val CACHE_SIZE_STEP = 10

        fun deviceLanguage(): String = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)
    }
}
