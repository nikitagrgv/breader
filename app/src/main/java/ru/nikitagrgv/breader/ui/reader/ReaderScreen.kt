package ru.nikitagrgv.breader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.ReadingPosition
import ru.nikitagrgv.breader.format.InlineStyle
import ru.nikitagrgv.breader.format.Paragraph
import ru.nikitagrgv.breader.format.ParagraphKind
import kotlin.math.roundToInt

/**
 * @param chromeVisible whether the bars over the text are shown; they're hidden while reading.
 * @param contentPadding insets for everything except the book text, which uses the whole screen.
 */
@Composable
fun ReaderScreen(
    state: ReaderState,
    settings: ReaderSettings,
    chromeVisible: Boolean,
    contentPadding: PaddingValues,
    onToggleChrome: () -> Unit,
    onHideChrome: () -> Unit,
    onPositionChange: (bookId: String, position: ReadingPosition, progress: Float) -> Unit,
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
                        chromeVisible = chromeVisible,
                        onToggleChrome = onToggleChrome,
                        onHideChrome = onHideChrome,
                        onPositionChange = { position, progress -> onPositionChange(state.bookId, position, progress) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookText(
    state: ReaderState.Ready,
    settings: ReaderSettings,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onHideChrome: () -> Unit,
    onPositionChange: (ReadingPosition, Float) -> Unit,
) {
    val paragraphs = state.content.paragraphs
    val looks = rememberParagraphLooks(settings)
    // Saveable, so the position survives switching tabs and configuration changes.
    val listState = rememberLazyListState(state.initialPosition.paragraph, state.initialPosition.offset)
    val progress by remember(listState) { derivedStateOf { listState.readingProgress() } }

    val currentOnToggleChrome by rememberUpdatedState(onToggleChrome)
    val currentOnHideChrome by rememberUpdatedState(onHideChrome)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    fun savePosition() {
        val currentProgress = progress ?: return
        val position = ReadingPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        currentOnPositionChange(position, currentProgress)
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            // The bars only get in the way while reading; the position is stored once the scroll settles.
            if (scrolling) currentOnHideChrome() else savePosition()
        }
    }
    DisposableEffect(listState) {
        onDispose { savePosition() }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            // Only the camera cutout is avoided: the system bars are hidden while reading.
            contentPadding = WindowInsets.displayCutout
                .add(WindowInsets(settings.pagePadding, settings.pagePadding, settings.pagePadding, settings.pagePadding))
                .asPaddingValues(),
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { currentOnToggleChrome() }) },
        ) {
            items(count = paragraphs.size, contentType = { paragraphs[it].kind }) { index ->
                val paragraph = paragraphs[index]
                ParagraphText(paragraph, looks.getValue(paragraph.kind))
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(title = state.title, progress = progress)
        }
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

@Composable
private fun ParagraphText(paragraph: Paragraph, look: ParagraphLook) {
    val text = remember(paragraph) { paragraph.toAnnotatedString() }
    Text(
        text = text,
        style = look.style,
        modifier = Modifier
            .fillMaxWidth()
            .padding(look.padding),
    )
}

private fun Paragraph.toAnnotatedString(): AnnotatedString {
    if (spans.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for (span in spans) {
            val style = when (span.style) {
                InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            }
            addStyle(style, span.start, span.end)
        }
    }
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
            textAlign = TextAlign.Justify,
            textIndent = TextIndent(firstLine = 1.5.em),
            hyphens = Hyphens.Auto,
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
private fun ReaderTopBar(title: String, progress: Float?) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
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
