package ru.nikitagrgv.breader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.ui.library.AddBooksButton
import ru.nikitagrgv.breader.ui.library.LibraryScreen
import ru.nikitagrgv.breader.ui.reader.ReaderScreen
import ru.nikitagrgv.breader.ui.reader.ReaderState

enum class MainTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    LIBRARY(R.string.tab_library, R.drawable.ic_library_books),
    READER(R.string.tab_reader, R.drawable.ic_book),
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val readerState by viewModel.reader.collectAsStateWithLifecycle()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MainTab.LIBRARY) }
    var readerChromeVisible by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Keeps the saveable state (e.g. scroll positions) of a tab while the other one is shown.
    val tabStates = rememberSaveableStateHolder()

    // While reading, the text takes the whole screen; a tap brings the bars back.
    val immersive = tab == MainTab.READER && readerState is ReaderState.Ready && !readerChromeVisible
    SystemBarsVisibility(visible = !immersive)

    BackHandler(enabled = tab == MainTab.READER) { tab = MainTab.LIBRARY }

    LibraryEventsEffect(viewModel, snackbarHostState)

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !immersive,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                NavigationBar {
                    MainTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = {
                                if (item == MainTab.READER && tab == MainTab.READER) readerChromeVisible = false
                                tab = item
                            },
                            icon = { Icon(painterResource(item.icon), contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab == MainTab.LIBRARY) AddBooksButton(onAddBooks = viewModel::addBooks)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        tabStates.SaveableStateProvider(tab.name) {
            when (tab) {
                MainTab.LIBRARY -> {
                    val library by viewModel.library.collectAsStateWithLifecycle()
                    LibraryScreen(
                        library = library,
                        contentPadding = innerPadding,
                        onOpenBook = { book ->
                            viewModel.openBook(book)
                            readerChromeVisible = false
                            tab = MainTab.READER
                        },
                        onRemoveBook = viewModel::removeBook,
                    )
                }
                // The book text ignores innerPadding: the bars are drawn over it, so showing them doesn't move the text.
                MainTab.READER -> ReaderScreen(
                    state = readerState,
                    settings = readerSettings,
                    chromeVisible = readerChromeVisible,
                    contentPadding = innerPadding,
                    onToggleChrome = { readerChromeVisible = !readerChromeVisible },
                    onHideChrome = { readerChromeVisible = false },
                    onPositionChange = viewModel::savePosition,
                    onOpenLibrary = { tab = MainTab.LIBRARY },
                )
            }
        }
    }
}

@Composable
private fun LibraryEventsEffect(viewModel: MainViewModel, snackbarHostState: SnackbarHostState) {
    val resources = LocalResources.current
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            // A new message replaces the current one instead of waiting in the queue.
            snackbarHostState.currentSnackbarData?.dismiss()
            this@LaunchedEffect.launch {
                when (event) {
                    is LibraryEvent.BookRemoved -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.book_removed, event.book.title),
                            actionLabel = resources.getString(R.string.undo),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(event)
                    }
                    is LibraryEvent.ImportFinished -> {
                        val lines = buildList {
                            if (event.failed.isNotEmpty()) {
                                add(resources.getString(R.string.import_failed, event.failed.joinToString()))
                            }
                            if (event.duplicates.isNotEmpty()) {
                                add(resources.getString(R.string.import_duplicates, event.duplicates.joinToString()))
                            }
                        }
                        snackbarHostState.showSnackbar(lines.joinToString("\n"), duration = SnackbarDuration.Long)
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemBarsVisibility(visible: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    // Re-applied on every resume: the system may bring the bars back while the app is in the background.
    LifecycleResumeEffect(visible, window) {
        WindowCompat.getInsetsController(window, view).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (visible) show(WindowInsetsCompat.Type.systemBars()) else hide(WindowInsetsCompat.Type.systemBars())
        }
        onPauseOrDispose { }
    }
}
