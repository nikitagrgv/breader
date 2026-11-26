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
import ru.nikitagrgv.breader.data.Book
import ru.nikitagrgv.breader.data.Favorite
import ru.nikitagrgv.breader.data.FavoriteKind
import ru.nikitagrgv.breader.data.FavoritePlace
import ru.nikitagrgv.breader.data.favoriteKey
import ru.nikitagrgv.breader.dictionary.DictionarySource
import ru.nikitagrgv.breader.dictionary.Kaikki
import ru.nikitagrgv.breader.dictionary.inUse
import ru.nikitagrgv.breader.ui.dictionary.DictionaryManagerDialog
import ru.nikitagrgv.breader.ui.dictionary.DictionarySheet
import ru.nikitagrgv.breader.ui.dictionary.DictionaryViewModel
import ru.nikitagrgv.breader.translation.BookSelection
import ru.nikitagrgv.breader.ui.favorites.FavoriteOrigin
import ru.nikitagrgv.breader.ui.favorites.FavoriteResult
import ru.nikitagrgv.breader.ui.favorites.FavoriteSheet
import ru.nikitagrgv.breader.ui.favorites.FavoritesScreen
import ru.nikitagrgv.breader.ui.favorites.FavoritesViewModel
import ru.nikitagrgv.breader.ui.favorites.RemoveFavoritesDialog
import ru.nikitagrgv.breader.ui.favorites.origin
import ru.nikitagrgv.breader.ui.library.AddBooksButton
import ru.nikitagrgv.breader.ui.library.LibraryScreen
import ru.nikitagrgv.breader.ui.reader.ReaderScreen
import ru.nikitagrgv.breader.ui.reader.ReaderState
import ru.nikitagrgv.breader.ui.reader.bookId
import ru.nikitagrgv.breader.ui.reader.toReaderSettings
import ru.nikitagrgv.breader.ui.settings.SettingsScreen
import ru.nikitagrgv.breader.ui.stats.ReadingStatsSheet
import ru.nikitagrgv.breader.ui.translation.GrammarSheet
import ru.nikitagrgv.breader.ui.translation.GrammarViewModel
import ru.nikitagrgv.breader.ui.translation.MachineTranslationSheet
import ru.nikitagrgv.breader.ui.translation.MachineTranslationViewModel
import ru.nikitagrgv.breader.ui.translation.TranslationSheet
import ru.nikitagrgv.breader.ui.translation.TranslationViewModel
import ru.nikitagrgv.breader.ui.translation.describe
import ru.nikitagrgv.breader.ui.words.WordStatsDialog
import ru.nikitagrgv.breader.ui.words.WordStatsViewModel

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
    val machineViewModel: MachineTranslationViewModel = viewModel(factory = MachineTranslationViewModel.Factory)
    val machineTranslation by machineViewModel.state.collectAsStateWithLifecycle()
    val grammarViewModel: GrammarViewModel = viewModel(factory = GrammarViewModel.Factory)
    val grammar by grammarViewModel.state.collectAsStateWithLifecycle()
    val dictionaryViewModel: DictionaryViewModel = viewModel(factory = DictionaryViewModel.Factory)
    val dictionaryLookup by dictionaryViewModel.lookup.collectAsStateWithLifecycle()
    val dictionaries by dictionaryViewModel.installed.collectAsStateWithLifecycle()
    val dictionarySuggestions by dictionaryViewModel.suggestions.collectAsStateWithLifecycle()
    val dictionariesInUse = remember(dictionaries, settings.dictionaries) { settings.dictionaries.inUse(dictionaries) }
    val hasDictionaries = dictionariesInUse.isNotEmpty()
    var managingDictionaries by rememberSaveable { mutableStateOf(false) }
    // The book whose reading statistics are shown.
    var statsBookId by rememberSaveable { mutableStateOf<String?>(null) }
    // The book whose words are shown by frequency.
    var wordStatsBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val wordStatsViewModel: WordStatsViewModel = viewModel(factory = WordStatsViewModel.Factory)
    val favoritesViewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory)
    val favorites by favoritesViewModel.favorites.collectAsStateWithLifecycle()
    val favoriteKinds by favoritesViewModel.kinds.collectAsStateWithLifecycle()
    val savingFavorites by favoritesViewModel.saving.collectAsStateWithLifecycle()
    // The favorite whose star was pressed again, with what to remove of it: null for the whole of it. To be confirmed.
    var removingFavorite by remember { mutableStateOf<Pair<Favorite, FavoriteKind?>?>(null) }
    // The key of the favorite shown in detail.
    var shownFavoriteKey by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val readerState by viewModel.reader.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val jump by viewModel.jump.collectAsStateWithLifecycle()
    val readerSettings = remember(settings) { settings.toReaderSettings() }
    val openBook = library?.books?.find { it.id == readerState.bookId }

    /** Where a selection in the open book is, and what's around it, for keeping it in the favorites. */
    fun originOf(selection: BookSelection) = FavoriteOrigin(
        excerpt = selection.excerpt(settings.translation.contextWords),
        place = openBook?.let { FavoritePlace(it.id, it.fingerprint, it.title, selection.range) },
    )

    /** Tells what went wrong, or that the text is kept otherwise than it would be at best. */
    fun showFavoriteResult(result: FavoriteResult) {
        val message = when (result) {
            is FavoriteResult.Saved -> when {
                result.wanted != null -> null
                result.kind == FavoriteKind.MACHINE -> resources.getString(R.string.favorite_saved_machine)
                result.kind == null -> resources.getString(R.string.favorite_saved_plain)
                else -> null
            }
            FavoriteResult.NotInDictionaries ->
                resources.getString(if (hasDictionaries) R.string.favorite_not_found else R.string.favorites_need_dictionary)
            FavoriteResult.ModelsNotSetUp -> resources.getString(R.string.translation_setup_needed)
            is FavoriteResult.TranslationFailed ->
                resources.getString(R.string.favorite_translation_failed, result.error.describe(resources))
        }
        if (message != null) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /** Keeps the text with the best there is, or asks whether to remove it, if it's kept. */
    fun toggleFavorite(text: String, origin: FavoriteOrigin?) {
        val key = favoriteKey(text)
        val kept = favorites.find { it.key == key }
        if (kept != null) {
            removingFavorite = kept to null
        } else {
            favoritesViewModel.add(text, origin, kind = null, settings.translation, ::showFavoriteResult)
        }
    }

    /** Keeps the text with [kind] too, or asks whether to remove that, if it's kept with it. */
    fun toggleFavoriteKind(text: String, origin: FavoriteOrigin?, kind: FavoriteKind) {
        val key = favoriteKey(text)
        val kept = favorites.find { it.key == key }
        if (kept != null && kind in kept.kinds) {
            removingFavorite = kept to kind
        } else {
            favoritesViewModel.add(text, origin, kind, settings.translation, ::showFavoriteResult)
        }
    }

    /** The book a favorite was selected in: the same one, or the same file added again, if it's in the library. */
    fun bookOf(place: FavoritePlace): Book? {
        val books = library?.books ?: return null
        return books.find { it.id == place.bookId }
            ?: place.fingerprint?.let { fingerprint -> books.find { it.fingerprint == fingerprint } }
    }
    var tab by rememberSaveable { mutableStateOf(MainTab.READER) }
    // Hidden to begin with, as the app opens on the book being read.
    var readerChromeVisible by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    /** Opens the book a favorite was selected in where it was selected, if the book is in the library. */
    fun goToPlace(place: FavoritePlace) {
        val book = bookOf(place)
        if (book == null) {
            Toast.makeText(context, resources.getString(R.string.favorite_book_missing, place.bookTitle), Toast.LENGTH_LONG).show()
            return
        }
        shownFavoriteKey = null
        viewModel.jumpTo(book, place.range)
        readerChromeVisible = false
        tab = MainTab.READER
    }
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
                    onShowStats = { statsBookId = it.id },
                    onShowWords = { wordStatsBookId = it.id },
                    onFavoriteChange = viewModel::setFavorite,
                    onReorder = viewModel::reorderBooks,
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
                    readingTimeMs = openBook?.readingTimeMs ?: 0,
                    wordCount = openBook?.wordCount,
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
                    onLookUp = { selection ->
                        dictionaryViewModel.lookUp(selection.excerpt(contextWords = 0).fragment, originOf(selection))
                    },
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
                    onMachineTranslate = { selection ->
                        machineViewModel.translate(selection.excerpt(contextWords = 0).fragment, settings.translation)
                    },
                    favoriteKinds = favoriteKinds,
                    savingFavorites = savingFavorites,
                    onFavorite = { selection -> toggleFavorite(selection.excerpt(contextWords = 0).fragment, originOf(selection)) },
                    onFavoriteKind = { selection, kind ->
                        toggleFavoriteKind(selection.excerpt(contextWords = 0).fragment, originOf(selection), kind)
                    },
                    moreSelectionActions = settings.moreSelectionActions,
                    onMoreSelectionActionsChange = viewModel::setMoreSelectionActions,
                    selectionFadeMs = settings.selectionFadeMs,
                    selectionHistorySize = settings.selectionHistorySize,
                    // Going from a dictionary entry to managing the dictionaries still keeps the book covered.
                    dialogOpen = translation != null || grammar != null || dictionaryLookup != null || managingDictionaries ||
                        machineTranslation != null,
                    jump = jump,
                    onJumpDone = viewModel::onJumpDone,
                    onShowStats = { statsBookId = readerState.bookId },
                    onOpenLibrary = { tab = MainTab.LIBRARY },
                )
                MainTab.FAVORITES -> FavoritesScreen(
                    favorites = favorites,
                    contentPadding = innerPadding,
                    onOpen = { favorite ->
                        // The meanings alone are shown by the dictionaries, which may have more by now.
                        if (favorite.kinds == setOf(FavoriteKind.DICTIONARY)) {
                            dictionaryViewModel.lookUp(favorite.word, favorite.origin)
                        } else {
                            shownFavoriteKey = favorite.key
                        }
                    },
                    onGoToPlace = { favorite -> favorite.place?.let(::goToPlace) },
                    onRemove = favoritesViewModel::remove,
                    onLookUp = { dictionaryViewModel.lookUp("") },
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
                    dictionariesInUse = dictionariesInUse,
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

    machineTranslation?.let { state ->
        MachineTranslationSheet(
            state = state,
            onServiceChange = { service ->
                machineViewModel.switchService(service)
                // The one picked here is the one wanted next time too.
                viewModel.updateTranslationSettings { it.copy(machineService = service) }
            },
            onRetry = machineViewModel::retry,
            onDismiss = machineViewModel::dismiss,
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

    shownFavoriteKey?.let { key -> favorites.find { it.key == key } }?.let { favorite ->
        FavoriteSheet(
            favorite = favorite,
            bookTitle = favorite.place?.let(::bookOf)?.title,
            saving = favorite.key in savingFavorites,
            onFavorite = { toggleFavorite(favorite.word, favorite.origin) },
            onFavoriteKind = { kind -> toggleFavoriteKind(favorite.word, favorite.origin, kind) },
            onOpenDictionary = { dictionaryViewModel.lookUp(favorite.word, favorite.origin) },
            onGoToPlace = { favorite.place?.let(::goToPlace) },
            onDismiss = { shownFavoriteKey = null },
        )
    }

    // Before the dictionary and the favorites, which are opened from it: windows made later are shown above.
    val wordStatsBook = wordStatsBookId?.let { id -> library?.books?.find { it.id == id } }
    if (wordStatsBook != null) {
        val state by wordStatsViewModel.state.collectAsStateWithLifecycle()
        val meanings by wordStatsViewModel.meanings.collectAsStateWithLifecycle()
        // A PDF can only be read while it's open; any other book isn't read again then.
        val openContent = (readerState as? ReaderState.Ready)?.takeIf { it.bookId == wordStatsBook.id }?.content
        LaunchedEffect(wordStatsBook.id, openContent) { wordStatsViewModel.count(wordStatsBook, openContent) }
        WordStatsDialog(
            book = wordStatsBook,
            state = state,
            meanings = meanings,
            showsMeanings = dictionariesInUse.any { it is DictionarySource.Downloaded },
            hideCommon = settings.hideCommonWords,
            onHideCommonChange = viewModel::setHideCommonWords,
            favoriteKinds = favoriteKinds,
            savingFavorites = savingFavorites,
            onLoadMeanings = wordStatsViewModel::loadMeanings,
            onLookUp = { dictionaryViewModel.lookUp(it) },
            onFavorite = { toggleFavorite(it, origin = null) },
            onFavoriteKind = { text, kind -> toggleFavoriteKind(text, origin = null, kind) },
            onOpenBook = {
                viewModel.openBook(wordStatsBook)
                readerChromeVisible = false
                tab = MainTab.READER
            },
            onDismiss = {
                wordStatsViewModel.close()
                wordStatsBookId = null
            },
        )
    }

    dictionaryLookup?.let { lookup ->
        DictionarySheet(
            lookup = lookup,
            suggestions = dictionarySuggestions,
            hasDictionaries = hasDictionaries,
            favoriteKinds = favoriteKinds[favoriteKey(lookup.text)],
            savingFavorite = favoriteKey(lookup.text) in savingFavorites,
            onFavorite = { toggleFavorite(lookup.text, lookup.origin) },
            onFavoriteKind = { kind -> toggleFavoriteKind(lookup.text, lookup.origin, kind) },
            onLookUp = { dictionaryViewModel.lookUp(it) },
            onSuggest = dictionaryViewModel::suggest,
            onOpenDictionaries = {
                dictionaryViewModel.dismissLookup()
                managingDictionaries = true
            },
            onDismiss = dictionaryViewModel::dismissLookup,
        )
    }

    removingFavorite?.let { (favorite, kind) ->
        // Removing the only thing it's kept with removes it.
        val part = kind?.takeIf { favorite.kinds != setOf(it) }
        RemoveFavoritesDialog(
            word = favorite.word,
            count = 1,
            kind = part,
            onConfirm = {
                if (part != null) favoritesViewModel.remove(favorite.key, part) else favoritesViewModel.remove(setOf(favorite.key))
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
            settings = settings.dictionaries,
            onSettingsChange = viewModel::updateDictionarySettings,
            onDismiss = { managingDictionaries = false },
        )
    }

    statsBookId?.let { id ->
        // Follows the library, so the numbers keep up while it's open.
        val book = library?.books?.find { it.id == id }
        if (book != null) {
            ReadingStatsSheet(
                book = book,
                onShowWords = {
                    statsBookId = null
                    wordStatsBookId = id
                },
                onDismiss = { statsBookId = null },
            )
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
