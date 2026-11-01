package ru.nikitagrgv.breader.data

enum class AppTheme { DARK, LIGHT }

data class AppSettings(
    val theme: AppTheme = AppTheme.DARK,
    /** Book text size in sp. */
    val fontSize: Float = DEFAULT_FONT_SIZE,
    /** Split words at line ends; when off, lines are wrapped by whole words only. */
    val hyphenation: Boolean = false,
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 18f
        const val MIN_FONT_SIZE = 12f
        const val MAX_FONT_SIZE = 32f
    }
}
