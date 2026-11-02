package ru.nikitagrgv.breader.data

import android.net.Uri
import ru.nikitagrgv.breader.format.BookFormat
import ru.nikitagrgv.breader.translation.BookRange
import ru.nikitagrgv.breader.translation.TextPosition

/**
 * Where the reader stopped: the first visible paragraph and the scroll offset inside it, in px.
 * The paragraph index stays valid when the font size changes; only the offset becomes approximate.
 * In a PDF, the paragraphs are the pages.
 */
data class ReadingPosition(
    val paragraph: Int = 0,
    val offset: Int = 0,
    /** How much a PDF's pages are enlarged, from fitting the screen's width. */
    val zoom: Float = 1f,
    /** How far an enlarged PDF is moved sideways, in px. */
    val pan: Int = 0,
)

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
    /** How long the book has been read: it was open in the reader and the app was in use. */
    val readingTimeMs: Long = 0,
    /** What was selected in the book, the oldest first. */
    val selectionHistory: List<BookRange> = emptyList(),
)

/** Four numbers per range, as there may be a lot of them. */
internal fun List<BookRange>.toInts(): List<Int> =
    flatMap { listOf(it.start.paragraph, it.start.offset, it.end.paragraph, it.end.offset) }

/** Leaves out what can't be a range, so that a damaged history can't take the book away with it. */
internal fun rangesFromInts(ints: List<Int>): List<BookRange> =
    ints.chunked(4)
        .filter { it.size == 4 && it.all { number -> number >= 0 } }
        .map { BookRange(TextPosition(it[0], it[1]), TextPosition(it[2], it[3])) }
        .filter { it.start < it.end }

data class Library(
    val books: List<Book> = emptyList(),
    /** The book open in the reader; it's reopened on the next launch. */
    val currentBookId: String? = null,
)
