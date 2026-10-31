package ru.nikitagrgv.breader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ru.nikitagrgv.breader.ui.MainScreen
import ru.nikitagrgv.breader.ui.MainViewModel
import ru.nikitagrgv.breader.ui.theme.BreaderTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BreaderTheme {
                MainScreen(viewModel)
            }
        }
    }
}
