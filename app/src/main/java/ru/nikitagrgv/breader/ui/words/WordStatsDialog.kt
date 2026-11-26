package ru.nikitagrgv.breader.ui.words

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.Book
import ru.nikitagrgv.breader.data.FavoriteKind
import ru.nikitagrgv.breader.data.favoriteKey
import ru.nikitagrgv.breader.format.CommonWords
import ru.nikitagrgv.breader.format.WordCount
import ru.nikitagrgv.breader.format.WordFrequency
import ru.nikitagrgv.breader.ui.DotSeparatedText
import ru.nikitagrgv.breader.ui.favorites.FavoriteButton
import ru.nikitagrgv.breader.ui.reader.formatCount
import java.text.NumberFormat
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToInt

/**
 * The words of a book, the most frequent first, a page at a time: there are thousands of them. Each one is shown with
 * how many times it's there, its meanings from the downloaded dictionaries, and how much of the text it covers together
 * with the words above it.
 * @param meanings see [WordStatsViewModel.meanings].
 * @param showsMeanings whether there are downloaded dictionaries to look the words up in.
 * @param onLoadMeanings gets the words on the page shown.
 * @param onLookUp looks a word up in all the dictionaries.
 * @param onFavorite adds a word to the favorites, or removes it.
 * @param onOpenBook opens the book in the reader, for a PDF, which is only read then.
 */
@Composable
fun WordStatsDialog(
    book: Book,
    state: WordStatsState?,
    meanings: Map<String, String>,
    showsMeanings: Boolean,
    hideCommon: Boolean,
    onHideCommonChange: (Boolean) -> Unit,
    favoriteKinds: Map<String, Set<FavoriteKind>>,
    savingFavorites: Set<String>,
    onLoadMeanings: (List<WordCount>) -> Unit,
    onLookUp: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onFavoriteKind: (String, FavoriteKind) -> Unit,
    onOpenBook: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                    }
                    Column(Modifier.padding(start = 4.dp, end = 12.dp)) {
                        Text(stringResource(R.string.word_stats), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Another book's, until this one's are counted.
                when (val current = state?.takeIf { it.bookId == book.id }) {
                    is WordStatsState.Ready -> if (current.frequency.words.isEmpty()) {
                        Message(stringResource(R.string.word_stats_empty))
                    } else {
                        WordStatsContent(
                            frequency = current.frequency,
                            commonWords = current.commonWords,
                            meanings = meanings,
                            showsMeanings = showsMeanings,
                            hideCommon = hideCommon,
                            onHideCommonChange = onHideCommonChange,
                            favoriteKinds = favoriteKinds,
                            savingFavorites = savingFavorites,
                            onLoadMeanings = onLoadMeanings,
                            onLookUp = onLookUp,
                            onFavorite = onFavorite,
                            onFavoriteKind = onFavoriteKind,
                        )
                    }
                    is WordStatsState.Failed -> when (current.reason) {
                        WordStatsFailure.PDF_NOT_OPEN -> Message(stringResource(R.string.word_stats_pdf_not_open)) {
                            FilledTonalButton(onClick = onOpenBook) { Text(stringResource(R.string.word_stats_open_book)) }
                        }
                        WordStatsFailure.UNREADABLE -> Message(stringResource(R.string.word_stats_failed))
                    }
                    is WordStatsState.Counting, null -> Message(stringResource(R.string.word_stats_counting)) {
                        val progress = current?.progress
                        if (progress != null) {
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, below: (@Composable () -> Unit)? = null) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (below != null) {
            Spacer(Modifier.height(24.dp))
            below()
        }
    }
}

@Composable
private fun WordStatsContent(
    frequency: WordFrequency,
    commonWords: CommonWords?,
    meanings: Map<String, String>,
    showsMeanings: Boolean,
    hideCommon: Boolean,
    onHideCommonChange: (Boolean) -> Unit,
    favoriteKinds: Map<String, Set<FavoriteKind>>,
    savingFavorites: Set<String>,
    onLoadMeanings: (List<WordCount>) -> Unit,
    onLookUp: (String) -> Unit,
    onFavorite: (String) -> Unit,
    onFavoriteKind: (String, FavoriteKind) -> Unit,
) {
    val hidden = if (hideCommon && commonWords != null) commonWords.words else emptySet()
    val list = remember(frequency, hidden) { frequency.without(hidden) }
    val pages = ((list.words.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    // The pages are other ones when the common words are hidden or shown, so it's the first one again then.
    var page by rememberSaveable(hideCommon) { mutableIntStateOf(0) }
    val shownPage = page.coerceIn(0, pages - 1)
    val start = shownPage * PAGE_SIZE
    val end = minOf(start + PAGE_SIZE, list.words.size)
    if (showsMeanings) LaunchedEffect(list, shownPage) { onLoadMeanings(list.words.subList(start, end)) }
    // Each page starts at the top.
    val listState = rememberSaveable(shownPage, hideCommon, saver = LazyListState.Saver) { LazyListState() }

    Column(Modifier.fillMaxSize()) {
        DotSeparatedText(
            parts = listOf(
                pluralStringResource(R.plurals.words_total, frequency.total, formatCount(frequency.total)),
                pluralStringResource(R.plurals.word_stats_different, frequency.words.size, formatCount(frequency.words.size)),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (commonWords != null) {
            val examples = remember(frequency, commonWords) {
                frequency.words.filter { it.key in commonWords.words }.take(COMMON_EXAMPLES).joinToString { it.word }
            }
            val commonShare = remember(frequency, commonWords) { frequency.share(commonWords.words) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = hideCommon, role = Role.Checkbox, onValueChange = onHideCommonChange)
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Checkbox(checked = hideCommon, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                Column {
                    Text(stringResource(R.string.word_stats_hide_common), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(
                            R.string.word_stats_hide_common_summary,
                            examples,
                            formatShare(commonShare),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
        HorizontalDivider()

        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            if (shownPage == 0) {
                item(key = EXPLANATION) {
                    Explanation(hidden = hidden.isNotEmpty(), showsMeanings = showsMeanings)
                }
            }
            itemsIndexed(list.words.subList(start, end), key = { _, word -> word.key }) { offset, word ->
                val index = start + offset
                Column {
                    WordRow(
                        rank = index + 1,
                        word = word,
                        share = list.share(index),
                        coverage = list.coverage(index),
                        meaning = meanings[word.key],
                        favoriteKinds = favoriteKinds[favoriteKey(word.word)],
                        savingFavorite = favoriteKey(word.word) in savingFavorites,
                        onClick = { onLookUp(word.word) },
                        onFavorite = { onFavorite(word.word) },
                        onFavoriteKind = { onFavoriteKind(word.word, it) },
                    )
                    // Where knowing the words above gets one this far into understanding the text.
                    MILESTONES.lastOrNull { list.coverage(index - 1) < it && it <= list.coverage(index) }?.let { Milestone(it) }
                }
            }
        }

        if (pages > 1) {
            HorizontalDivider()
            PageBar(
                page = shownPage,
                pages = pages,
                coverage = stringResource(
                    R.string.word_stats_page_coverage,
                    formatCoverage(list.coverage(start)),
                    formatCoverage(list.coverage(end - 1)),
                ),
                onPageChange = { page = it.coerceIn(0, pages - 1) },
            )
        }
    }
}

@Composable
private fun Explanation(hidden: Boolean, showsMeanings: Boolean) {
    Text(
        text = listOfNotNull(
            stringResource(R.string.word_stats_explanation),
            stringResource(R.string.word_stats_explanation_hidden).takeIf { hidden },
            stringResource(R.string.word_stats_no_dictionaries).takeUnless { showsMeanings },
        ).joinToString(" "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * @param share of the text that is this word.
 * @param coverage the share of the text that is this word or the ones above.
 * @param meaning null until it's looked up; empty if the dictionaries don't have it.
 */
@Composable
private fun WordRow(
    rank: Int,
    word: WordCount,
    share: Double,
    coverage: Double,
    meaning: String?,
    favoriteKinds: Set<FavoriteKind>?,
    savingFavorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onFavoriteKind: (FavoriteKind) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(word.word, style = MaterialTheme.typography.titleMedium)
            DotSeparatedText(
                parts = listOf(
                    stringResource(R.string.word_stats_rank, rank),
                    pluralStringResource(R.plurals.word_stats_times, word.count, formatCount(word.count)),
                    formatShare(share),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!meaning.isNullOrEmpty()) {
                Text(
                    text = meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // In a column of its own, to find the place where it reaches the share wanted.
        Text(
            text = formatCoverage(coverage),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.End,
            modifier = Modifier
                .padding(start = 8.dp)
                .widthIn(min = 56.dp),
        )
        FavoriteButton(kinds = favoriteKinds, saving = savingFavorite, onClick = onFavorite, onKindClick = onFavoriteKind)
    }
}

@Composable
private fun Milestone(share: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
        Text(
            text = stringResource(R.string.word_stats_milestone, NumberFormat.getPercentInstance().format(share)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
    }
}

/** Going a page back or forth, or through many of them by dragging. */
@Composable
private fun PageBar(page: Int, pages: Int, coverage: String, onPageChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        IconButton(onClick = { onPageChange(page - 1) }, enabled = page > 0) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.previous_page))
        }
        Column(Modifier.weight(1f)) {
            val pageText = stringResource(R.string.word_stats_page, page + 1, pages)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DotSeparatedText(
                    parts = listOf(pageText, coverage),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Without steps: there may be hundreds of pages, and a tick for each would make a mess of the track.
            Slider(
                value = page.toFloat(),
                onValueChange = { onPageChange(it.roundToInt()) },
                valueRange = 0f..(pages - 1).toFloat(),
                modifier = Modifier.semantics { contentDescription = pageText },
            )
        }
        IconButton(onClick = { onPageChange(page + 1) }, enabled = page < pages - 1) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.next_page))
        }
    }
}

/** With two significant digits, as it's from tens of percent for the commonest words to thousandths for the rarest. */
private fun formatShare(share: Double): String {
    val percent = share * 100
    val digits = if (percent <= 0) 1 else (1 - floor(log10(percent))).toInt().coerceIn(1, MAX_SHARE_DIGITS)
    return NumberFormat.getPercentInstance().apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(share)
}

/**
 * Rounded down, so that 100% is only where the whole text is covered, at the last word. The fraction a bit off, like
 * 0.29999999999999998 for 3/10, is rounded to what it is first.
 */
private fun formatCoverage(share: Double): String = NumberFormat.getPercentInstance().apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}.format(floor(share * 1000 + 1e-9) / 1000)

private const val PAGE_SIZE = 50
private const val COMMON_EXAMPLES = 5
private const val MAX_SHARE_DIGITS = 6

/** Unlike the keys of the words, it has a space. */
private const val EXPLANATION = "word stats explanation"

/** Shares of the text that are worth marking: knowing 95-98% of the words of a text is said to be enough to read it. */
private val MILESTONES = listOf(0.5, 0.75, 0.9, 0.95, 0.98)
