package ru.nikitagrgv.breader.ui

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.AppSettings
import ru.nikitagrgv.breader.data.Favorite
import ru.nikitagrgv.breader.data.favoriteKey
import ru.nikitagrgv.breader.dictionary.Kaikki
import ru.nikitagrgv.breader.ui.dictionary.DictionaryManagerDialog
import ru.nikitagrgv.breader.ui.dictionary.DictionarySheet
import ru.nikitagrgv.breader.ui.dictionary.DictionaryViewModel
import ru.nikitagrgv.breader.ui.favorites.FavoritesScreen
import ru.nikitagrgv.breader.ui.favorites.FavoritesViewModel
import ru.nikitagrgv.breader.ui.favorites.RemoveFavoritesDialog
import ru.nikitagrgv.breader.ui.library.AddBooksButton
import ru.nikitagrgv.breader.ui.library.LibraryScreen
import ru.nikitagrgv.breader.ui.reader.ReaderScreen
import ru.nikitagrgv.breader.ui.reader.ReaderState
import ru.nikitagrgv.breader.ui.reader.bookId
import ru.nikitagrgv.breader.ui.reader.toReaderSettings
import ru.nikitagrgv.breader.ui.settings.SettingsScreen
import ru.nikitagrgv.breader.ui.translation.GrammarSheet
import ru.nikitagrgv.breader.ui.translation.GrammarViewModel
import ru.nikitagrgv.breader.ui.translation.TranslationSheet
import ru.nikitagrgv.breader.ui.translation.TranslationViewModel

enum class MainTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    LIBRARY(R.string.tab_library, R.drawable.ic_library_books),
    READER(R.string.tab_reader, R.drawable.ic_book),
    FAVORITES(R.string.tab_favorites, R.drawable.ic_star),
    SETTINGS(R.string.tab_settings, R.drawable.ic_settings),
}

@Composable
fun MainScreen(viewModel: MainViewModel, settings: AppSettings) {
    val translationViewModel: TranslationViewModel = viewModel(factory = TranslationViewModel.Factory)
    val translation by translationViewModel.sheet.collectAsStateWithLifecycle()
    val translationPreview by translationViewModel.preview.collectAsStateWithLifecycle()
    val cachedTranslations by translationViewModel.cacheSize.collectAsStateWithLifecycle()
    val grammarViewModel: GrammarViewModel = viewModel(factory = GrammarViewModel.Factory)
    val grammar by grammarViewModel.state.collectAsStateWithLifecycle()
    val dictionaryViewModel: DictionaryViewModel = viewModel(factory = DictionaryViewModel.Factory)
    val dictionaryLookup by dictionaryViewModel.lookup.collectAsStateWithLifecycle()
    val dictionaries by dictionaryViewModel.installed.collectAsStateWithLifecycle()
    var managingDictionaries by rememberSaveable { mutableStateOf(false) }
    val favoritesViewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory)
    val favorites by favoritesViewModel.favorites.collectAsStateWithLifecycle()
    val favoriteKeys by favoritesViewModel.keys.collectAsStateWithLifecycle()
    // The favorite whose star was pressed again, to confirm removing it.
    var removingFavorite by remember { mutableStateOf<Favorite?>(null) }
    val context = LocalContext.current
    val readerState by viewModel.reader.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val readerSettings = remember(settings) { settings.toReaderSettings() }
    var tab by rememberSaveable { mutableStateOf(MainTab.READER) }
    // Hidden to begin with, as the app opens on the book being read.
    var readerChromeVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Keeps the saveable state (e.g. scroll positions) of a tab while another one is shown.
    val tabStates = rememberSaveableStateHolder()

    // While reading, the text takes the whole screen; a tap brings the bars back. A book being opened counts too, so
    // the bars don't show up just to slide away. Without a book to read, they're the way to go on from there.
    val immersive = tab == MainTab.READER && !readerChromeVisible &&
        readerState != ReaderState.Empty && readerState !is ReaderState.Failed
    SystemBarsVisibility(visible = !immersive)

    BackHandler(enabled = tab != MainTab.LIBRARY) { tab = MainTab.LIBRARY }

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
                MainTab.LIBRARY -> LibraryScreen(
                    library = library,
                    contentPadding = innerPadding,
                    onOpenBook = { book ->
                        viewModel.openBook(book)
                        readerChromeVisible = false
                        tab = MainTab.READER
                    },
                    onRemoveBook = viewModel::removeBook,
                )
                // The book text ignores innerPadding: the bars are drawn over it, so showing them doesn't move the text.
                MainTab.READER -> ReaderScreen(
                    state = readerState,
                    settings = readerSettings,
                    seekBarPosition = settings.seekBarPosition,
                    chromeVisible = readerChromeVisible,
                    contentPadding = innerPadding,
                    onToggleChrome = { readerChromeVisible = !readerChromeVisible },
                    onHideChrome = { readerChromeVisible = false },
                    onPositionChange = viewModel::savePosition,
                    readingTimeMs = library?.books?.find { it.id == readerState.bookId }?.readingTimeMs ?: 0,
                    onReadingTime = viewModel::addReadingTime,
                    translationPreview = translationPreview,
                    onSelectionChange = { selection ->
                        translationViewModel.select(
                            excerpt = selection?.excerpt(settings.translation.contextWords),
                            settings = settings.translation,
                        )
                    },
                    onTranslate = { selection ->
                        translationViewModel.openSheet(
                            excerpt = selection.excerpt(settings.translation.contextWords),
                            settings = settings.translation,
                        )
                    },
                    autoTranslate = settings.translation.autoTranslate,
                    onAutoTranslateChange = { enabled -> viewModel.updateTranslationSettings { it.copy(autoTranslate = enabled) } },
                    onLookUp = { selection -> dictionaryViewModel.lookUp(selection.excerpt(contextWords = 0).fragment) },
                    onGrammar = { selection ->
                        grammarViewModel.explain(
                            excerpt = selection.excerpt(settings.translation.contextWords),
                            settings = settings.translation,
                        )
                    },
                    onQuickTranslate = { selection ->
                        val excerpt = selection.excerpt(settings.translation.contextWords)
                        // Without models, the sheet tells how to set them up.
                        if (settings.translation.isConfigured) {
                            translationViewModel.quickTranslate(excerpt, settings.translation)
                        } else {
                            translationViewModel.openSheet(excerpt, settings.translation)
                        }
                    },
                    favoriteKeys = favoriteKeys,
                    onFavorite = { selection ->
                        val text = selection.excerpt(contextWords = 0).fragment
                        val favorite = favorites.find { it.key == favoriteKey(text) }
                        if (favorite != null) {
                            removingFavorite = favorite
                        } else {
                            favoritesViewModel.add(text) {
                                val message = if (dictionaries.isEmpty()) R.string.favorites_need_dictionary else R.string.favorite_not_found
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    moreSelectionActions = settings.moreSelectionActions,
                    onMoreSelectionActionsChange = viewModel::setMoreSelectionActions,
                    selectionFadeMs = settings.selectionFadeMs,
                    selectionHistorySize = settings.selectionHistorySize,
                    // Going from a dictionary entry to managing the dictionaries still keeps the book covered.
                    dialogOpen = translation != null || grammar != null || dictionaryLookup != null || managingDictionaries,
                    onOpenLibrary = { tab = MainTab.LIBRARY },
                )
                MainTab.FAVORITES -> FavoritesScreen(
                    favorites = favorites,
                    contentPadding = innerPadding,
                    onOpen = { favorite -> dictionaryViewModel.lookUp(favorite.word) },
                    onRemove = favoritesViewModel::remove,
                )
                MainTab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    contentPadding = innerPadding,
                    onThemeChange = viewModel::setTheme,
                    onFontSizeChange = viewModel::setFontSize,
                    onFontChange = viewModel::setFont,
                    onLineSpacingChange = viewModel::setLineSpacing,
                    onMarginsChange = viewModel::setMargins,
                    onJustifyChange = viewModel::setJustify,
                    onHyphenationChange = viewModel::setHyphenation,
                    onSeekBarPositionChange = viewModel::setSeekBarPosition,
                    onSelectionFadeChange = viewModel::setSelectionFade,
                    onSelectionHistorySizeChange = viewModel::setSelectionHistorySize,
                    onTranslationChange = viewModel::updateTranslationSettings,
                    cachedTranslations = cachedTranslations,
                    onClearTranslationCache = translationViewModel::clearCache,
                    dictionaries = dictionaries.size,
                    onOpenDictionaries = { managingDictionaries = true },
                )
            }
        }
    }

    translation?.let { state ->
        TranslationSheet(
            state = state,
            onRetry = translationViewModel::retry,
            onOpenSettings = {
                translationViewModel.dismissSheet()
                tab = MainTab.SETTINGS
            },
            onDismiss = translationViewModel::dismissSheet,
        )
    }

    grammar?.let { state ->
        GrammarSheet(
            state = state,
            onRetry = grammarViewModel::retry,
            onOpenSettings = {
                grammarViewModel.dismiss()
                tab = MainTab.SETTINGS
            },
            onDismiss = grammarViewModel::dismiss,
        )
    }

    dictionaryLookup?.let { lookup ->
        DictionarySheet(
            lookup = lookup,
            hasDictionaries = dictionaries.isNotEmpty(),
            onOpenDictionaries = {
                dictionaryViewModel.dismissLookup()
                managingDictionaries = true
            },
            onDismiss = dictionaryViewModel::dismissLookup,
        )
    }

    removingFavorite?.let { favorite ->
        RemoveFavoritesDialog(
            word = favorite.word,
            count = 1,
            onConfirm = {
                favoritesViewModel.remove(setOf(favorite.key))
                removingFavorite = null
            },
            onDismiss = { removingFavorite = null },
        )
    }

    if (managingDictionaries) {
        DictionaryManagerDialog(
            // The words are best explained in the language they're translated into.
            defaultEdition = Kaikki.editions.find { it.name.equals(settings.translation.effectiveTargetLanguage, ignoreCase = true) }
                ?: Kaikki.editions.first(),
            viewModel = dictionaryViewModel,
            onDismiss = { managingDictionaries = false },
        )
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
