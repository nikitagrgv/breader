package ru.nikitagrgv.breader

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nikitagrgv.breader.ui.MainScreen
import ru.nikitagrgv.breader.ui.MainViewModel
import ru.nikitagrgv.breader.ui.theme.BreaderTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Nothing is drawn until the settings are loaded (a few ms), so the wrong theme never flashes.
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            settings?.let { current ->
                val darkTheme = current.theme.isDark
                // The app theme doesn't follow the system one, so the bar icon colors are set explicitly.
                DisposableEffect(darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                    )
                    onDispose {}
                }
                BreaderTheme(theme = current.theme) {
                    MainScreen(viewModel, current)
                }
            }
        }
    }

    private companion object {
        // The defaults used by enableEdgeToEdge() for the 3-button navigation bar.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
