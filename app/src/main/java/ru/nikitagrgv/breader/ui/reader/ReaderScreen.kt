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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.ReadingPosition
import ru.nikitagrgv.breader.data.SeekBarPosition
import ru.nikitagrgv.breader.format.InlineStyle
import ru.nikitagrgv.breader.format.Paragraph
import ru.nikitagrgv.breader.format.ParagraphKind
import ru.nikitagrgv.breader.translation.BookSelection
import kotlin.math.roundToInt

/**
 * @param chromeVisible whether the bars over the text are shown; they're hidden while reading.
 * @param contentPadding insets for everything except the book text, which uses the whole screen.
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
    onTranslate: (BookSelection) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (state) {
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
                        onTranslate = onTranslate,
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
    onTranslate: (BookSelection) -> Unit,
) {
    val paragraphs = state.content.paragraphs
    val looks = rememberParagraphLooks(settings)
    // Saveable, so the position survives switching tabs and configuration changes.
    val listState = rememberLazyListState(state.initialPosition.paragraph, state.initialPosition.offset)
    val progress by remember(listState) { derivedStateOf { listState.readingProgress() } }
    // Not saveable on purpose: a restored selection would refer to texts that are composed anew.
    val selectionState = remember { SelectionState() }
    val hasSelection by remember(selectionState) {
        derivedStateOf { selectionState.selectedTexts.any { it.isNotEmpty() } }
    }
    // The position (0..1) the seek bar is dragged to; null while it isn't touched.
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    var topBarHeight by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val currentOnToggleChrome by rememberUpdatedState(onToggleChrome)
    val currentOnHideChrome by rememberUpdatedState(onHideChrome)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    fun savePosition() {
        val currentProgress = progress ?: return
        val position = ReadingPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        currentOnPositionChange(position, currentProgress)
    }

    // The bars only get in the way while reading, so they're hidden once the user drags the text
    // (jumps made with the seek bar don't count).
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) currentOnHideChrome()
        }
    }
    // The position is stored once scrolling or seeking settles.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress || seekFraction != null }.collect { busy ->
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

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides NoTextContextMenu) {
            SelectionContainer(state = selectionState, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    // Only the camera cutout is avoided: the system bars are hidden while reading.
                    contentPadding = WindowInsets.displayCutout
                        .add(WindowInsets(settings.pagePadding, settings.pagePadding, settings.pagePadding, settings.pagePadding))
                        .asPaddingValues(),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            var hadSelection = false
                            detectTapGestures(
                                // A tap that clears the selection shouldn't toggle the bars as well.
                                onPress = { hadSelection = hasSelection },
                                onTap = { if (!hadSelection) currentOnToggleChrome() },
                            )
                        },
                ) {
                    items(count = paragraphs.size, contentType = { paragraphs[it].kind }) { index ->
                        val paragraph = paragraphs[index]
                        ParagraphText(paragraph, index, looks.getValue(paragraph.kind))
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                title = state.title,
                progress = progress,
                modifier = Modifier.onSizeChanged { topBarHeight = it.height },
            )
        }

        SeekBarOverlay(
            position = seekBarPosition,
            visible = chromeVisible,
            topBarHeight = with(LocalDensity.current) { topBarHeight.toDp() },
            bottomBarHeight = contentPadding.calculateBottomPadding(),
            fraction = seekFraction ?: progress ?: 0f,
            onSeek = { seekFraction = it },
            onSeekFinished = { seekFraction = null },
        )

        AnimatedVisibility(
            visible = hasSelection,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            SelectionBar(
                onCopy = {
                    val text = selectionState.selectedText()
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) }
                    // Android 13+ confirms copying by itself.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                    }
                    selectionState.clear()
                },
                onTranslate = {
                    selectionState.locate(paragraphs)?.let(onTranslate)
                    selectionState.clear()
                },
                onClear = selectionState::clear,
            )
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

/** Selected parts of the paragraphs, one per line. */
private fun SelectionState.selectedText(): String = selectedTexts.joinToString("\n") { it.text }

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

/** @param index the paragraph's index in the book, if it's from the book. */
@Composable
private fun ParagraphText(paragraph: Paragraph, index: Int?, look: ParagraphLook, modifier: Modifier = Modifier) {
    val text = remember(paragraph, index) { paragraph.toAnnotatedString(index) }
    Text(
        text = text,
        style = look.style,
        modifier = modifier
            .fillMaxWidth()
            .padding(look.padding),
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
private fun ReaderTopBar(title: String, progress: Float?, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (progress != null) {
                Text(
                    text = stringResource(R.string.progress_percent, (progress * 100).roundToInt()),
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
