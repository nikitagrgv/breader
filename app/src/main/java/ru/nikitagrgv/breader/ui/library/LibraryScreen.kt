package ru.nikitagrgv.breader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.Book
import ru.nikitagrgv.breader.data.Library
import ru.nikitagrgv.breader.format.BookParser
import ru.nikitagrgv.breader.ui.DotSeparatedText
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

@Composable
fun LibraryScreen(
    library: Library?,
    contentPadding: PaddingValues,
    onOpenBook: (Book) -> Unit,
    onRemoveBook: (Book) -> Unit,
    onShowStats: (Book) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(library.books, key = { it.id }) { book ->
                        BookItem(
                            book = book,
                            isOpen = book.id == library.currentBookId,
                            onOpen = { onOpenBook(book) },
                            onRemove = { onRemoveBook(book) },
                            onShowStats = { onShowStats(book) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookItem(
    book: Book,
    isOpen: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onShowStats: () -> Unit,
    modifier: Modifier = Modifier,
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
        leadingContent = { Icon(painterResource(R.drawable.ic_book), contentDescription = null) },
        trailingContent = {
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
        },
        colors = if (isOpen) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        modifier = modifier.clickable(onClick = onOpen),
    )
}
