package ru.nikitagrgv.breader.ui.reader

import androidx.annotation.FontRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.ReaderFont

/**
 * The family of [ReaderFont] for text of [size]. The bundled fonts are variable: their regular and bold, upright
 * and italic, are set on their axes, and so is the optical size, which makes the letters suit the text size.
 */
fun ReaderFont.fontFamily(size: TextUnit): FontFamily = when (this) {
    ReaderFont.SANS -> FontFamily.SansSerif
    ReaderFont.SERIF -> FontFamily.Serif
    ReaderFont.LITERATA -> variableFamily(R.font.literata, R.font.literata_italic, size, opticalSizing = true)
    ReaderFont.ATKINSON -> variableFamily(R.font.atkinson_hyperlegible, R.font.atkinson_hyperlegible_italic, size, opticalSizing = false)
}

private fun variableFamily(@FontRes upright: Int, @FontRes italic: Int, size: TextUnit, opticalSizing: Boolean): FontFamily {
    fun font(@FontRes file: Int, weight: FontWeight, style: FontStyle): Font {
        val settings = listOfNotNull(FontVariation.weight(weight.weight), if (opticalSizing) FontVariation.opticalSizing(size) else null)
        return Font(file, weight, style, variationSettings = FontVariation.Settings(*settings.toTypedArray()))
    }
    return FontFamily(
        font(upright, FontWeight.Normal, FontStyle.Normal),
        font(upright, FontWeight.Bold, FontStyle.Normal),
        font(italic, FontWeight.Normal, FontStyle.Italic),
        font(italic, FontWeight.Bold, FontStyle.Italic),
    )
}
