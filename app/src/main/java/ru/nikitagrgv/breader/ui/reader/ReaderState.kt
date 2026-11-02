package ru.nikitagrgv.breader.ui.reader

import ru.nikitagrgv.breader.data.ReadingPosition
import ru.nikitagrgv.breader.format.BookContent

sealed interface ReaderState {
    data object Empty : ReaderState

    data class Loading(val bookId: String, val title: String) : ReaderState

    data class Ready(
        val bookId: String,
        val title: String,
        val content: BookContent,
        /** Where to start when the book is shown for the first time. */
        val initialPosition: ReadingPosition,
    ) : ReaderState

    data class Failed(val bookId: String, val title: String) : ReaderState
}

val ReaderState.bookId: String?
    get() = when (this) {
        ReaderState.Empty -> null
        is ReaderState.Loading -> bookId
        is ReaderState.Ready -> bookId
        is ReaderState.Failed -> bookId
    }
