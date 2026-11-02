package ru.nikitagrgv.breader.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ru.nikitagrgv.breader.data.AppTheme

/** Dark and light follow the wallpaper colors where the system has them; black and sepia have their own. */
@Composable
fun BreaderTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dark = if (dynamic) dynamicDarkColorScheme(context) else darkColorScheme()
    val colorScheme = when (theme) {
        AppTheme.DARK -> dark
        AppTheme.BLACK -> dark.black()
        AppTheme.LIGHT -> if (dynamic) dynamicLightColorScheme(context) else lightColorScheme()
        AppTheme.SEPIA -> SepiaColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * Black backgrounds, which turn the pixels of OLED screens off. The text is off-white: pure white on black
 * seems to glow and blur, especially to the many people with astigmatism.
 */
private fun ColorScheme.black(): ColorScheme {
    val text = Color(0xFFD9D9D9)
    return copy(
        background = Color.Black,
        onBackground = text,
        surface = Color.Black,
        onSurface = text,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0B0B),
        surfaceContainer = Color(0xFF121212),
        surfaceContainerHigh = Color(0xFF1A1A1A),
        surfaceContainerHighest = Color(0xFF232323),
    )
}

/** Dark brown on warm paper: less glare than black on white, at a contrast of about 10:1. */
private val SepiaColors = lightColorScheme(
    primary = Color(0xFF7B5B36),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9D4B2),
    onPrimaryContainer = Color(0xFF2C1A06),
    secondary = Color(0xFF6F5D45),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5D6BA),
    onSecondaryContainer = Color(0xFF261A0A),
    tertiary = Color(0xFF5E6A3E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7BC),
    onTertiaryContainer = Color(0xFF1B2104),
    background = Color(0xFFF4ECD8),
    onBackground = Color(0xFF433422),
    surface = Color(0xFFF4ECD8),
    onSurface = Color(0xFF433422),
    surfaceVariant = Color(0xFFE6DABF),
    onSurfaceVariant = Color(0xFF6B5A43),
    surfaceContainerLowest = Color(0xFFFBF5E8),
    surfaceContainerLow = Color(0xFFF0E6CF),
    surfaceContainer = Color(0xFFEADFC5),
    surfaceContainerHigh = Color(0xFFE4D8BC),
    surfaceContainerHighest = Color(0xFFDED1B3),
    outline = Color(0xFF8C7B62),
    outlineVariant = Color(0xFFD3C4A6),
)
