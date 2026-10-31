package ru.nikitagrgv.breader.data

import android.net.Uri
import ru.nikitagrgv.breader.format.BookFormat

/**
 * Where the reader stopped: the first visible paragraph and the scroll offset inside it, in px.
 * The paragraph index stays valid when the font size changes; only the offset becomes approximate.
 */
data class ReadingPosition(val paragraph: Int = 0, val offset: Int = 0)

data class Book(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val format: BookFormat,
    val title: String,
    val author: String?,
    val addedAt: Long,
    val position: ReadingPosition = ReadingPosition(),
    /** Read fraction of the book, 0..1. */
    val progress: Float = 0f,
)

data class Library(
    val books: List<Book> = emptyList(),
    /** The book open in the reader; it's reopened on the next launch. */
    val currentBookId: String? = null,
)
