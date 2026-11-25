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
    /**
     * How much of [readingTimeMs] was spent at each percent of the book, i.e. while the position was in it; [PERCENTS]
     * of them, or none before anything is read. Kept since this was added, so it may add up to less than the total.
     */
    val timeByPercent: List<Long> = emptyList(),
    /** The number of words in the book; null until they're counted, which is done when it's opened. */
    val wordCount: Int? = null,
    /** What was selected in the book, the oldest first. */
    val selectionHistory: List<BookRange> = emptyList(),
    /** Starred by the user: such books are listed first in the library. */
    val favorite: Boolean = false,
    /**
     * What tells the book's file apart from any other, see [BookFiles.fingerprint]: the same book added again, under
     * another name or from another place, has the same one. Null until it's taken, which is done in the background.
     */
    val fingerprint: String? = null,
)

/** What [Book.timeByPercent] splits a book into. */
const val PERCENTS = 100

/** The part of [Book.timeByPercent] that the position [progress] (0..1) is in. */
fun percentOf(progress: Float): Int = (progress * PERCENTS).toInt().coerceIn(0, PERCENTS - 1)

/** [Book.timeByPercent] with [ms] more at [percent]. */
internal fun List<Long>.plusTime(percent: Int, ms: Long): List<Long> {
    val times = if (size == PERCENTS) toMutableList() else MutableList(PERCENTS) { getOrElse(it) { 0L } }
    times[percent] += ms
    return times
}

/**
 * The items with the [keys] put in this order into the places that they take; the rest stay where they are, and the
 * [keys] that no item has are ignored. So the favorite books can be reordered among themselves without moving the others.
 */
internal fun <T> List<T>.reordered(keys: List<String>, key: (T) -> String): List<T> {
    val byKey = associateBy(key)
    val moved = keys.distinct().mapNotNull { byKey[it] }
    val movedKeys = moved.mapTo(HashSet(), key)
    val queue = moved.iterator()
    return map { if (key(it) in movedKeys) queue.next() else it }
}

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
    /** In the user's order; new books come first. */
    val books: List<Book> = emptyList(),
    /** The book open in the reader; it's reopened on the next launch. */
    val currentBookId: String? = null,
)
