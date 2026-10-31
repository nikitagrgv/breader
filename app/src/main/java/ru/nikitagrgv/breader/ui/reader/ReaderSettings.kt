package ru.nikitagrgv.breader.ui.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How the book text looks. Fixed for now, will be user-configurable. */
@Immutable
data class ReaderSettings(
    val fontSize: TextUnit = 18.sp,
    /** Line height relative to the font size. */
    val lineHeight: Float = 1.4f,
    val pagePadding: Dp = 12.dp,
)
