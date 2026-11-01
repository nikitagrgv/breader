package ru.nikitagrgv.breader.ui.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nikitagrgv.breader.data.AppSettings

/** How the book text looks. */
@Immutable
data class ReaderSettings(
    val fontSize: TextUnit = AppSettings.DEFAULT_FONT_SIZE.sp,
    val fontFamily: FontFamily = FontFamily.SansSerif,
    /** Line height relative to the font size. */
    val lineHeight: Float = AppSettings.DEFAULT_LINE_SPACING,
    /** Space at the left and right of the text; on wide screens, there's more to keep the lines readable. */
    val margins: Dp = AppSettings.DEFAULT_MARGINS.dp,
    /** Stretch the spaces so that lines end evenly at the right edge. */
    val justify: Boolean = true,
    /** Split words at line ends; when off, lines are wrapped by whole words only. */
    val hyphenation: Boolean = false,
)

fun AppSettings.toReaderSettings() = ReaderSettings(
    fontSize = fontSize.sp,
    fontFamily = font.fontFamily(fontSize.sp),
    lineHeight = lineSpacing,
    margins = margins.dp,
    justify = justify,
    hyphenation = hyphenation,
)
