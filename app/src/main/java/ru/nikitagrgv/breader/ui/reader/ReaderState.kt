package ru.nikitagrgv.breader.ui.reader

import ru.nikitagrgv.breader.data.ReadingPosition
import ru.nikitagrgv.breader.format.BookContent

sealed interface ReaderState {
    /** The library is still being read (a few ms), so it isn't known yet whether there's a book to reopen. */
    data object Starting : ReaderState

    data object Empty : ReaderState

    data class Loading(val bookId: String, val title: String) : ReaderState

    data class Ready(
        val bookId: String,
        val title: String,
        val content: BookContent,
        /** Where to start when the book is shown for the first time. */
        val initialPosition: ReadingPosition,
        val history: SelectionHistoryState,
    ) : ReaderState

    data class Failed(val bookId: String, val title: String) : ReaderState
}

val ReaderState.bookId: String?
    get() = when (this) {
        ReaderState.Starting, ReaderState.Empty -> null
        is ReaderState.Loading -> bookId
        is ReaderState.Ready -> bookId
        is ReaderState.Failed -> bookId
    }
