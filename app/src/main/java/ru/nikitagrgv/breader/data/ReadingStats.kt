package ru.nikitagrgv.breader.data

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * What the time spent at each percent of a book tells about how fast it's read.
 * @param timeByPercent see [Book.timeByPercent].
 * @param totalMs how long the book has been read in all.
 * @param progress the read fraction of the book.
 * @param wordCount the number of words in the book, if they're counted.
 */
class ReadingStats(timeByPercent: List<Long>, val totalMs: Long, progress: Float, val wordCount: Int?) {
    constructor(book: Book) : this(book.timeByPercent, book.readingTimeMs, book.progress, book.wordCount)

    /** The time spent at each percent, in ms; zeros where nothing was read, or not since this was kept. */
    val times: List<Long> = List(PERCENTS) { timeByPercent.getOrElse(it) { 0L } }

    /** The time kept by percent, which may be less than the book's total: it's kept since it was added. */
    val trackedMs: Long = times.sum()

    /** The percent the reader is at, which isn't finished yet. */
    val currentPercent: Int = percentOf(progress)

    val wordsRead: Int? = wordCount?.let { (it * progress).roundToInt() }

    private val wordsPerPercent: Double? = wordCount?.takeIf { it > 0 }?.let { it.toDouble() / PERCENTS }

    /**
     * The words per hour at each percent; null where it can't be told. A percent read faster than anyone reads was
     * skipped through, or jumped over, rather than read, and the one the reader is at isn't finished yet.
     */
    val speeds: List<Double?> = times.mapIndexed { percent, ms ->
        val words = wordsPerPercent ?: return@mapIndexed null
        if (percent == currentPercent || !isRead(ms, words)) null else words / ms * MS_PER_HOUR
    }

    /** Words per hour over all the percents with a speed; null if there are none. */
    val averageSpeed: Double? = run {
        val words = wordsPerPercent ?: return@run null
        val read = times.filterIndexed { percent, _ -> speeds[percent] != null }
        if (read.isEmpty()) null else words * read.size / read.sum() * MS_PER_HOUR
    }

    /** How long the rest of the book takes at [averageSpeed]; null if it isn't known. */
    val timeLeftMs: Long? = run {
        val speed = averageSpeed ?: return@run null
        val count = wordCount ?: return@run null
        (count * (1 - progress) / speed * MS_PER_HOUR).roundToLong().coerceAtLeast(0)
    }

    companion object {
        private const val MS_PER_HOUR = 3_600_000.0

        /** Much faster than anyone reads: a few times the speed of a fast reader, which is 400 words a minute. */
        private const val MAX_WORDS_PER_MINUTE = 1_000.0

        private fun isRead(ms: Long, words: Double): Boolean = ms > 0 && words / ms * 60_000 <= MAX_WORDS_PER_MINUTE
    }
}
