package ru.nikitagrgv.breader.ui.reader

import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.ReadingPosition
import ru.nikitagrgv.breader.data.favoriteKey
import ru.nikitagrgv.breader.data.SeekBarPosition
import ru.nikitagrgv.breader.format.BookContent
import ru.nikitagrgv.breader.format.InlineStyle
import ru.nikitagrgv.breader.format.Paragraph
import ru.nikitagrgv.breader.format.ParagraphKind
import ru.nikitagrgv.breader.format.PdfBook
import ru.nikitagrgv.breader.translation.BookSelection
import ru.nikitagrgv.breader.ui.translation.TranslationPreview
import ru.nikitagrgv.breader.ui.translation.TranslationState
import kotlin.math.roundToInt

/**
 * @param chromeVisible whether the bars over the text are shown; they're hidden while reading.
 * @param contentPadding insets for everything except the book text, which uses the whole screen.
 * @param translationPreview brief translations of the selected text, shown at the top; null to show none.
 * @param onSelectionChange gets every change of the selection in the book; null when it's cleared.
 * @param autoTranslate whether the selection is translated at once; the selection is reported again when it changes.
 * @param readingTimeMs how long the open book has been read, for the top bar.
 * @param onReadingTime gets the time the book is read for, in parts, while the reader is shown in the resumed app.
 * @param selectionFadeMs how long the place of the selection fades out after a dialog about it is closed; 0 for not showing it.
 * @param dialogOpen whether a dialog opened from the selection's actions is still over the book.
 */
@Composable
fun ReaderScreen(
    state: ReaderState,
    settings: ReaderSettings,
    seekBarPosition: SeekBarPosition,
    chromeVisible: Boolean,
    contentPadding: PaddingValues,
    onToggleChrome: () -> Unit,
    onHideChrome: () -> Unit,
    onPositionChange: (bookId: String, position: ReadingPosition, progress: Float) -> Unit,
    readingTimeMs: Long,
    onReadingTime: (bookId: String, ms: Long) -> Unit,
    translationPreview: TranslationState?,
    onSelectionChange: (BookSelection?) -> Unit,
    onTranslate: (BookSelection) -> Unit,
    autoTranslate: Boolean,
    onAutoTranslateChange: (Boolean) -> Unit,
    onLookUp: (BookSelection) -> Unit,
    onGrammar: (BookSelection) -> Unit,
    onQuickTranslate: (BookSelection) -> Unit,
    favoriteKeys: Set<String>,
    onFavorite: (BookSelection) -> Unit,
    moreSelectionActions: Boolean,
    onMoreSelectionActionsChange: (Boolean) -> Unit,
    selectionFadeMs: Int,
    dialogOpen: Boolean,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (state) {
                // Too short for anything to be worth showing, and "no book" may well be wrong.
                ReaderState.Starting -> Unit
                ReaderState.Empty -> ReaderMessage(
                    title = stringResource(R.string.reader_no_book),
                    text = stringResource(R.string.reader_no_book_hint),
                    contentPadding = contentPadding,
                    onOpenLibrary = onOpenLibrary,
                )
                is ReaderState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ReaderState.Failed -> ReaderMessage(
                    title = stringResource(R.string.reader_failed, state.title),
                    text = stringResource(R.string.reader_failed_hint),
                    contentPadding = contentPadding,
                    onOpenLibrary = onOpenLibrary,
                )
                // Keyed by the book, so the saved scroll state of one book is never applied to another.
                is ReaderState.Ready -> key(state.bookId) {
                    BookText(
                        state = state,
                        settings = settings,
                        seekBarPosition = seekBarPosition,
                        chromeVisible = chromeVisible,
                        contentPadding = contentPadding,
                        onToggleChrome = onToggleChrome,
                        onHideChrome = onHideChrome,
                        onPositionChange = { position, progress -> onPositionChange(state.bookId, position, progress) },
                        readingTimeMs = readingTimeMs,
                        onReadingTime = { onReadingTime(state.bookId, it) },
                        translationPreview = translationPreview,
                        onSelectionChange = onSelectionChange,
                        onTranslate = onTranslate,
                        autoTranslate = autoTranslate,
                        onAutoTranslateChange = onAutoTranslateChange,
                        onLookUp = onLookUp,
                        onGrammar = onGrammar,
                        onQuickTranslate = onQuickTranslate,
                        favoriteKeys = favoriteKeys,
                        onFavorite = onFavorite,
                        moreSelectionActions = moreSelectionActions,
                        onMoreSelectionActionsChange = onMoreSelectionActionsChange,
                        selectionFadeMs = selectionFadeMs,
                        dialogOpen = dialogOpen,
                    )
                }
            }
        }
    }
}

/** A sample paragraph that looks exactly like the book text, e.g. for previewing settings. */
@Composable
fun ReaderTextPreview(text: String, settings: ReaderSettings, modifier: Modifier = Modifier) {
    val looks = rememberParagraphLooks(settings)
    ParagraphText(Paragraph(text), index = null, looks.getValue(ParagraphKind.BODY), modifier)
}

@Composable
private fun BookText(
    state: ReaderState.Ready,
    settings: ReaderSettings,
    seekBarPosition: SeekBarPosition,
    chromeVisible: Boolean,
    contentPadding: PaddingValues,
    onToggleChrome: () -> Unit,
    onHideChrome: () -> Unit,
    onPositionChange: (ReadingPosition, Float) -> Unit,
    readingTimeMs: Long,
    onReadingTime: (Long) -> Unit,
    translationPreview: TranslationState?,
    onSelectionChange: (BookSelection?) -> Unit,
    onTranslate: (BookSelection) -> Unit,
    autoTranslate: Boolean,
    onAutoTranslateChange: (Boolean) -> Unit,
    onLookUp: (BookSelection) -> Unit,
    onGrammar: (BookSelection) -> Unit,
    onQuickTranslate: (BookSelection) -> Unit,
    favoriteKeys: Set<String>,
    onFavorite: (BookSelection) -> Unit,
    moreSelectionActions: Boolean,
    onMoreSelectionActionsChange: (Boolean) -> Unit,
    selectionFadeMs: Int,
    dialogOpen: Boolean,
) {
    val paragraphs = state.content.paragraphs
    // Saveable, so the position survives switching tabs and configuration changes.
    val listState = rememberLazyListState(state.initialPosition.paragraph, state.initialPosition.offset)
    val viewport = rememberSaveable(saver = PdfViewport.Saver) {
        PdfViewport(state.initialPosition.zoom, state.initialPosition.pan.toFloat())
    }
    val progress by remember(listState) { derivedStateOf { listState.readingProgress() } }
    // Not saveable on purpose: a restored selection would refer to texts that are composed anew.
    val selection = remember(state.content) { selectionIn(state.content) }
    val hasSelection by remember(selection) { derivedStateOf { selection.hasSelection } }
    val selectedText by remember(selection) { derivedStateOf { selection.text } }
    val fade = remember(selection) { SelectionFade() }
    // The position (0..1) the seek bar is dragged to; null while it isn't touched.
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    var topBarHeight by remember { mutableIntStateOf(0) }
    // The last ones known, for telling what the selection bars are likely to cover.
    var previewHeight by remember { mutableIntStateOf(0) }
    var selectionBarHeight by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val history = state.history
    val walker = remember(history, selection, listState, viewport, fade, scope) {
        SelectionHistoryWalker(history, selection, listState, viewport, fade, scope)
    }

    val currentOnToggleChrome by rememberUpdatedState(onToggleChrome)
    val currentOnHideChrome by rememberUpdatedState(onHideChrome)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    fun savePosition() {
        if (history.holdsPosition) return
        val currentProgress = progress ?: return
        val position = ReadingPosition(
            paragraph = listState.firstVisibleItemIndex,
            offset = listState.firstVisibleItemScrollOffset,
            zoom = viewport.zoom,
            pan = viewport.pan.roundToInt(),
        )
        currentOnPositionChange(position, currentProgress)
    }

    /** The dialog is there instead of the selection, whose place stays marked. */
    fun openDialog(open: (BookSelection) -> Unit) {
        selection.locate()?.let {
            open(it)
            if (selectionFadeMs > 0) fade.place = it
        }
        selection.clear()
    }

    /** The part of the screen that's left for the text with a selection on it, in px. */
    fun freeArea(): IntRange {
        val top = maxOf(previewHeight, if (chromeVisible) topBarHeight else 0)
        val bottom = with(density) {
            (if (selectionBarHeight > 0) selectionBarHeight else SELECTION_BAR_USUAL_HEIGHT.roundToPx()) +
                contentPadding.calculateBottomPadding().roundToPx() + SELECTED_LINE_ROOM.roundToPx()
        }
        return top..(listState.layoutInfo.viewportSize.height - bottom)
    }

    ReadingTimeTracker(onReadingTime)
    SelectionFadeEffect(fade, dialogOpen, selectionFadeMs)

    // The bars only get in the way while reading, so they're hidden once the user drags the text
    // (jumps made with the seek bar don't count). It also means reading on from wherever the text is now.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) {
                currentOnHideChrome()
                history.holdsPosition = false
            }
        }
    }
    // The position is stored once scrolling or seeking settles.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress || seekFraction != null || viewport.isBusy }.collect { busy ->
            if (!busy) savePosition()
        }
    }
    LaunchedEffect(listState, paragraphs.size) {
        snapshotFlow { seekFraction }.filterNotNull().collectLatest { fraction ->
            listState.scrollToItem((fraction * (paragraphs.size - 1)).roundToInt())
        }
    }
    DisposableEffect(listState) {
        onDispose { savePosition() }
    }
    // Every change is reported: whoever translates the selection waits for it to settle.
    LaunchedEffect(selection, walker) {
        try {
            snapshotFlow { walker.isSelectionOnItsWay to selection.locate() }
                // The moment between two selections isn't worth making everything about the selection go away.
                .filterNot { (onItsWay, located) -> onItsWay && located == null }
                .map { (_, located) -> located }
                .distinctUntilChangedBy { it?.start to it?.end }
                .collect { located ->
                    currentOnSelectionChange(located)
                    // A new selection is where the reader is now.
                    if (history.onSelectionChange(located?.range)) fade.place = null
                }
        } finally {
            // The selection goes away with the text, e.g. when another tab is opened.
            currentOnSelectionChange(null)
            history.onSelectionChange(null)
        }
    }
    // Translated anew, or only looked up in the dictionaries, without selecting it again.
    LaunchedEffect(autoTranslate) {
        selection.locate()?.let { currentOnSelectionChange(it) }
    }

    Box(Modifier.fillMaxSize()) {
        when (selection) {
            is PdfSelection -> PdfPages(
                selection = selection,
                listState = listState,
                viewport = viewport,
                fade = fade,
                onToggleChrome = onToggleChrome,
                onHideChrome = onHideChrome,
            )
            is TextSelection -> ParagraphList(
                paragraphs = paragraphs,
                settings = settings,
                selection = selection,
                listState = listState,
                fade = fade,
                onToggleChrome = onToggleChrome,
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = state.title,
                // Pages are what a PDF is made of, while in a book laid out by the reader they mean nothing.
                position = if (selection is PdfSelection) {
                    val page by remember(listState) { derivedStateOf { listState.middleItemIndex() + 1 } }
                    stringResource(R.string.page_number, page, paragraphs.size)
                } else {
                    progress?.let { stringResource(R.string.progress_percent, it * 100) }
                },
                readingTimeMs = readingTimeMs,
                modifier = Modifier.onSizeChanged { topBarHeight = it.height },
            )
        }

        SeekBarOverlay(
            position = seekBarPosition,
            visible = chromeVisible,
            topBarHeight = with(LocalDensity.current) { topBarHeight.toDp() },
            bottomBarHeight = contentPadding.calculateBottomPadding(),
            fraction = seekFraction ?: progress ?: 0f,
            onSeek = {
                seekFraction = it
                history.holdsPosition = false
            },
            onSeekFinished = { seekFraction = null },
        )

        // Over the top bar, which says nothing about the selection. Up to a third of the screen,
        // so the selected text is likely to stay in sight.
        val previewMaxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } / 3
        // Kept while the preview slides away, so it doesn't go blank.
        val lastPreview = remember { LastValue<TranslationState>() }
        if (translationPreview != null) lastPreview.value = translationPreview
        AnimatedVisibility(
            visible = translationPreview != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            (translationPreview ?: lastPreview.value)?.let {
                TranslationPreview(
                    state = it,
                    cornerSpace = DpSize(SelectionHistoryButtonsWidth, HISTORY_BUTTONS_ROW_HEIGHT),
                    modifier = Modifier
                        .heightIn(max = previewMaxHeight)
                        .onSizeChanged { size -> previewHeight = size.height },
                )
            }
        }

        // Above the navigation bar while it's shown; contentPadding has its height.
        AnimatedVisibility(
            visible = hasSelection || walker.isSelectionOnItsWay,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            SelectionBar(
                moreOpen = moreSelectionActions,
                onMoreOpenChange = onMoreSelectionActionsChange,
                onCopy = {
                    val text = selection.text
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) }
                    // Android 13+ confirms copying by itself.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                    }
                    selection.clear()
                },
                onTranslate = { openDialog(onTranslate) },
                // The selection stays: the quick translation is shown for it, the star is filled for it.
                onQuickTranslate = { selection.locate()?.let(onQuickTranslate) },
                favorite = favoriteKey(selectedText) in favoriteKeys,
                onFavorite = { selection.locate()?.let(onFavorite) },
                autoTranslate = autoTranslate,
                onAutoTranslateChange = onAutoTranslateChange,
                onLookUp = { openDialog(onLookUp) },
                onGrammar = { openDialog(onGrammar) },
                modifier = Modifier.onSizeChanged { selectionBarHeight = it.height },
            )
        }

        // Over everything else at the top, which keeps clear of them, so that they stay in the same place all the time.
        // Hidden while just reading: a tap brings them along with the bars.
        AnimatedVisibility(
            visible = chromeVisible || hasSelection || walker.isSelectionOnItsWay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(AbsoluteAlignment.TopRight)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Right))
                .absolutePadding(right = 4.dp, top = 2.dp),
        ) {
            val walk = history.history
            SelectionHistoryButtons(
                stepsBack = walk.stepsBack,
                stepsForward = walk.stepsForward,
                onBack = { walker.walk(history.history.back(), selectionFadeMs, freeArea()) },
                onForward = { walker.walk(history.history.forward(), selectionFadeMs, freeArea()) },
                onReturn = { walker.walk(history.history.toHome(), selectionFadeMs, freeArea()) },
            )
        }
    }
}

private fun selectionIn(content: BookContent): ReaderSelection =
    if (content is PdfBook) PdfSelection(content) else TextSelection(SelectionState(), content.paragraphs)

/** The text of a book laid out by the reader, by the settings. */
@Composable
private fun ParagraphList(
    paragraphs: List<Paragraph>,
    settings: ReaderSettings,
    selection: TextSelection,
    listState: LazyListState,
    fade: SelectionFade,
    onToggleChrome: () -> Unit,
) {
    val looks = rememberParagraphLooks(settings)
    val density = LocalDensity.current
    val blankLine = with(density) { settings.fontSize.toDp() } * settings.lineHeight
    val currentOnToggleChrome by rememberUpdatedState(onToggleChrome)

    // Lines much longer than 70 characters are hard to follow back to the start of the next one, so on wide
    // screens (tablets, landscape) the text keeps to a column in the middle. Phones are narrower than that.
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val maxTextWidth = with(density) { settings.fontSize.toDp() } * MAX_LINE_EMS
    val sideMargin = maxOf(settings.margins, (windowWidth - maxTextWidth) / 2)

    CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides NoTextContextMenu) {
        SelectionContainer(state = selection.state, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                // Only the camera cutout is avoided: the system bars are hidden while reading.
                contentPadding = WindowInsets.displayCutout
                    .add(WindowInsets(left = sideMargin, top = TEXT_VERTICAL_PADDING, right = sideMargin, bottom = TEXT_VERTICAL_PADDING))
                    .asPaddingValues(),
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selection) {
                        var hadSelection = false
                        detectTapGestures(
                            // A tap that clears the selection shouldn't toggle the bars as well.
                            onPress = { hadSelection = selection.hasSelection },
                            onTap = { if (!hadSelection) currentOnToggleChrome() },
                        )
                    },
            ) {
                items(count = paragraphs.size, contentType = { paragraphs[it].kind }) { index ->
                    val paragraph = paragraphs[index]
                    // Capped: some books stack many empty lines, which would leave half a screen empty.
                    val blankLines = paragraph.blankLinesBefore.coerceAtMost(MAX_BLANK_LINES)
                    ParagraphText(
                        paragraph = paragraph,
                        index = index,
                        look = looks.getValue(paragraph.kind),
                        modifier = if (blankLines > 0) Modifier.padding(top = blankLine * blankLines) else Modifier,
                        fade = fade,
                    )
                }
            }
        }
    }
}

/**
 * [BookSeekBar] over the text along the screen edge at [position], clear of the top and bottom bars.
 * It comes and goes with them, sliding from its edge.
 */
@Composable
private fun BoxScope.SeekBarOverlay(
    position: SeekBarPosition,
    visible: Boolean,
    topBarHeight: Dp,
    bottomBarHeight: Dp,
    fraction: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    val vertical = position.isVertical
    // Toward its own edge: up or left is negative.
    val hiddenOffset: (Int) -> Int = when (position) {
        SeekBarPosition.LEFT, SeekBarPosition.TOP -> { size -> -size }
        SeekBarPosition.RIGHT, SeekBarPosition.BOTTOM -> { size -> size }
    }
    // Left and right are physical sides, the same in right-to-left languages.
    val placement = when (position) {
        SeekBarPosition.LEFT -> Modifier
            .align(AbsoluteAlignment.TopLeft)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Left))
            .absolutePadding(left = 8.dp, top = topBarHeight + 12.dp, bottom = bottomBarHeight + 12.dp)
        SeekBarPosition.RIGHT -> Modifier
            .align(AbsoluteAlignment.TopRight)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Right))
            .absolutePadding(right = 8.dp, top = topBarHeight + 12.dp, bottom = bottomBarHeight + 12.dp)
        SeekBarPosition.TOP -> Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = 12.dp, end = 12.dp, top = topBarHeight + 8.dp)
        SeekBarPosition.BOTTOM -> Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(start = 12.dp, end = 12.dp, bottom = bottomBarHeight + 8.dp)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + if (vertical) slideInHorizontally(initialOffsetX = hiddenOffset) else slideInVertically(initialOffsetY = hiddenOffset),
        exit = fadeOut() + if (vertical) slideOutHorizontally(targetOffsetX = hiddenOffset) else slideOutVertically(targetOffsetY = hiddenOffset),
        modifier = placement.then(if (vertical) Modifier.fillMaxHeight() else Modifier.fillMaxWidth()),
    ) {
        BookSeekBar(
            fraction = fraction,
            vertical = vertical,
            onSeek = onSeek,
            onSeekFinished = onSeekFinished,
            modifier = if (vertical) Modifier.fillMaxHeight() else Modifier.fillMaxWidth(),
        )
    }
}

private const val MAX_BLANK_LINES = 2

/** The height of the bar of actions on the selection before it's first shown. */
private val SELECTION_BAR_USUAL_HEIGHT = 120.dp

/** How far from the bottom of the free area a selection's first line still counts as being in it. */
private val SELECTED_LINE_ROOM = 48.dp

/** What's next to the buttons going through the selections has to be this high for them to stay on it. */
private val HISTORY_BUTTONS_ROW_HEIGHT = 48.dp

/** About 70 characters: an average character with the spaces is half the font size wide. */
private const val MAX_LINE_EMS = 35f
private val TEXT_VERTICAL_PADDING = 12.dp

/** Not a state on purpose: it's only read while its value is gone, and setting it shouldn't recompose anything. */
private class LastValue<T : Any> {
    var value: T? = null
}

/** Suppresses the floating text toolbar: the reader shows [SelectionBar] instead. */
private object NoTextContextMenu : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        awaitCancellation()
    }
}

private fun LazyListState.readingProgress(): Float? {
    val total = layoutInfo.totalItemsCount
    return when {
        total == 0 -> null // Not laid out yet.
        !canScrollForward -> 1f
        else -> firstVisibleItemIndex.toFloat() / total
    }
}

/** The item in the middle of the screen, which is what's being read when several are visible. */
private fun LazyListState.middleItemIndex(): Int {
    val layout = layoutInfo
    val middle = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
    return layout.visibleItemsInfo.lastOrNull { it.offset <= middle }?.index ?: firstVisibleItemIndex
}

/**
 * @param index the paragraph's index in the book, if it's from the book.
 * @param fade the place to mark, for a paragraph from the book.
 */
@Composable
private fun ParagraphText(
    paragraph: Paragraph,
    index: Int?,
    look: ParagraphLook,
    modifier: Modifier = Modifier,
    fade: SelectionFade? = null,
) {
    val text = remember(paragraph, index) { paragraph.toAnnotatedString(index) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        style = look.style,
        onTextLayout = { layout = it },
        modifier = modifier
            .fillMaxWidth()
            .padding(look.padding)
            // Inside the padding: the layout knows nothing about it. Only drawn again as the fading goes on.
            .drawWithCache {
                val part = index?.let { fade?.place?.partOf(it) }
                val path = part?.let { (start, end) -> layout?.getPathForRange(start, end) }
                onDrawBehind {
                    if (path != null && fade != null) drawPath(path, SelectionFadeColor, alpha = fade.alpha)
                }
            },
    )
}

private fun Paragraph.toAnnotatedString(index: Int?): AnnotatedString = buildAnnotatedString {
    append(text)
    for (span in spans) {
        val style = when (span.style) {
            InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
        }
        addStyle(style, span.start, span.end)
    }
    if (index != null) tagParagraph(index)
}

private class ParagraphLook(val style: TextStyle, val padding: PaddingValues)

/** Text styles of all paragraph kinds; sizes and spacings are relative to the font size from [settings]. */
@Composable
private fun rememberParagraphLooks(settings: ReaderSettings): Map<ParagraphKind, ParagraphLook> {
    val color = MaterialTheme.colorScheme.onBackground
    val density = LocalDensity.current
    return remember(settings, color, density) {
        val em = with(density) { settings.fontSize.toDp() }
        val body = TextStyle(
            color = color,
            fontSize = settings.fontSize,
            fontFamily = settings.fontFamily,
            lineHeight = settings.lineHeight.em,
            textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
            textIndent = TextIndent(firstLine = 1.5.em),
            hyphens = if (settings.hyphenation) Hyphens.Auto else Hyphens.None,
            lineBreak = LineBreak.Paragraph,
        )
        val centered = body.copy(textAlign = TextAlign.Center, textIndent = TextIndent.None, hyphens = Hyphens.None)
        mapOf(
            ParagraphKind.BODY to ParagraphLook(body, PaddingValues(vertical = em * 0.15f)),
            ParagraphKind.HEADING to ParagraphLook(
                centered.copy(fontSize = settings.fontSize * 1.3f, fontWeight = FontWeight.Bold, lineHeight = 1.25.em),
                PaddingValues(top = em * 1.5f, bottom = em * 0.75f),
            ),
            ParagraphKind.SUBTITLE to ParagraphLook(
                centered.copy(fontWeight = FontWeight.Bold),
                PaddingValues(vertical = em * 0.5f),
            ),
            ParagraphKind.VERSE to ParagraphLook(
                body.copy(textAlign = TextAlign.Start, textIndent = TextIndent(restLine = 1.5.em), hyphens = Hyphens.None),
                PaddingValues(start = em * 2),
            ),
            ParagraphKind.QUOTE to ParagraphLook(
                body.copy(fontStyle = FontStyle.Italic),
                PaddingValues(start = em * 2, end = em, top = em * 0.15f, bottom = em * 0.15f),
            ),
        )
    }
}

@Composable
private fun ReaderTopBar(title: String, position: String?, readingTimeMs: Long, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                // The buttons going through the selections are shown over the right end.
                .absolutePadding(left = 16.dp, right = 16.dp + SelectionHistoryButtonsWidth, top = 12.dp, bottom = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (position != null) {
                Text(
                    text = listOfNotNull(position, formatReadingTime(readingTimeMs)).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ReaderMessage(title: String, text: String, contentPadding: PaddingValues, onOpenLibrary: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(32.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onOpenLibrary) {
            Text(stringResource(R.string.open_library))
        }
    }
}
