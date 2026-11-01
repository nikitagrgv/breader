package ru.nikitagrgv.breader.ui.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.nikitagrgv.breader.data.AppSettings

/** How the book text looks. */
@Immutable
data class ReaderSettings(
    val fontSize: TextUnit = AppSettings.DEFAULT_FONT_SIZE.sp,
    /** Line height relative to the font size. */
    val lineHeight: Float = 1.4f,
    val pagePadding: Dp = 12.dp,
    /** Stretch the spaces so that lines end evenly at the right edge. */
    val justify: Boolean = true,
    /** Split words at line ends; when off, lines are wrapped by whole words only. */
    val hyphenation: Boolean = false,
)

fun AppSettings.toReaderSettings() = ReaderSettings(fontSize = fontSize.sp, justify = justify, hyphenation = hyphenation)
