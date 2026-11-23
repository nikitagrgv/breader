package ru.nikitagrgv.breader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.Book
import ru.nikitagrgv.breader.data.Library
import ru.nikitagrgv.breader.format.BookParser
import ru.nikitagrgv.breader.ui.DotSeparatedText
import ru.nikitagrgv.breader.ui.KeepListPlace
import ru.nikitagrgv.breader.ui.ReorderableItem
import ru.nikitagrgv.breader.ui.rememberReorderState
import ru.nikitagrgv.breader.ui.reorderableItem
import ru.nikitagrgv.breader.ui.reader.formatCount
import ru.nikitagrgv.breader.ui.reader.formatReadingTime
import ru.nikitagrgv.breader.ui.reader.wordsProgress
import kotlin.math.roundToInt

/** Lives in the Scaffold's FAB slot, so snackbars push it up instead of covering it. */
@Composable
fun AddBooksButton(onAddBooks: (List<Uri>) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onAddBooks(uris)
    }
    FloatingActionButton(onClick = { picker.launch(BookParser.PICKER_MIME_TYPES) }) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_books))
    }
}

/**
 * The books, the favorite ones first, under a heading of their own. Each group is in the user's order, which is
 * changed by dragging the books.
 * @param onReorder gets the IDs of a group of books in their new order.
 */
@Composable
fun LibraryScreen(
    library: Library?,
    contentPadding: PaddingValues,
    onOpenBook: (Book) -> Unit,
    onRemoveBook: (Book) -> Unit,
    onShowStats: (Book) -> Unit,
    onFavoriteChange: (Book, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val books = library?.books.orEmpty()
    val byId = remember(books) { books.associateBy { it.id } }
    val favoriteIds = remember(books) { books.filter { it.favorite }.map { it.id } }
    val otherIds = remember(books) { books.filterNot { it.favorite }.map { it.id } }
    // Each group is dragged within itself.
    val favoritesOrder = rememberReorderState(favoriteIds, onReorder)
    val othersOrder = rememberReorderState(otherIds, onReorder)
    val listState = rememberLazyListState()
    // So that a starred book doesn't take the list along to the favorites, nor a dragged one.
    KeepListPlace(listState, favoritesOrder.order + othersOrder.order)

    Box(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        // Nothing to show until the library is loaded, which takes a moment.
        if (library == null) return@Box

        Column(Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (library.books.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.library_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // Bottom padding keeps the last item clear of the FAB.
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 88.dp)) {
                    // The headings are only needed to tell the groups apart.
                    val grouped = favoriteIds.isNotEmpty() && otherIds.isNotEmpty()
                    if (grouped) item(key = FAVORITES_HEADING) { SectionHeader(R.string.library_favorites, Modifier.animateItem()) }
                    for ((reorder, heading) in listOf(favoritesOrder to null, othersOrder to OTHERS_HEADING)) {
                        if (grouped && heading != null) item(key = heading) { SectionHeader(R.string.library_others, Modifier.animateItem()) }
                        items(reorder.order, key = { it }) { id ->
                            val book = byId[id] ?: return@items
                            BookItem(
                                book = book,
                                isOpen = book.id == library.currentBookId,
                                onOpen = { onOpenBook(book) },
                                onRemove = { onRemoveBook(book) },
                                onShowStats = { onShowStats(book) },
                                onFavoriteChange = { onFavoriteChange(book, it) },
                                item = reorderableItem(reorder, id),
                            )
                        }
                    }
                }
            }
        }
    }
}

// Unlike the book IDs, which are UUIDs.
private const val FAVORITES_HEADING = "heading:favorites"
private const val OTHERS_HEADING = "heading:others"

@Composable
private fun SectionHeader(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun BookItem(
    book: Book,
    isOpen: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onShowStats: () -> Unit,
    onFavoriteChange: (Boolean) -> Unit,
    item: ReorderableItem,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val started = book.progress > 0f
    val progress = if (started) stringResource(R.string.progress_percent, book.progress * 100) else null
    val words = book.wordCount?.let { total ->
        if (started) {
            wordsProgress((book.progress * total).roundToInt(), total)
        } else {
            pluralStringResource(R.plurals.words_total, total, formatCount(total))
        }
    }
    ListItem(
        headlineContent = { Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    text = listOfNotNull(book.author, book.format.name).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Where the reader is, in the words as well, so that it's clear how much is left.
                DotSeparatedText(listOfNotNull(progress, words, formatReadingTime(book.readingTimeMs)))
            }
        },
        leadingContent = item.handle,
        trailingContent = {
            Row {
                IconButton(onClick = { onFavoriteChange(!book.favorite) }) {
                    Icon(
                        painterResource(if (book.favorite) R.drawable.ic_star else R.drawable.ic_star_outline),
                        contentDescription = stringResource(if (book.favorite) R.string.remove_from_favorites else R.string.add_to_favorites),
                        tint = if (book.favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.book_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reading_stats)) },
                            onClick = {
                                menuExpanded = false
                                onShowStats()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_from_library)) },
                            onClick = {
                                menuExpanded = false
                                onRemove()
                            },
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = when {
                // Lifted off the list.
                item.dragged -> MaterialTheme.colorScheme.surfaceContainerHigh
                isOpen -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            },
        ),
        modifier = item.modifier
            .clickable(onClick = onOpen)
            .semantics { customActions = item.actions },
    )
}
