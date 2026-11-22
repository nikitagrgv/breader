package ru.nikitagrgv.breader.ui.reader

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.nikitagrgv.breader.format.BookContent
import ru.nikitagrgv.breader.format.PdfBook
import ru.nikitagrgv.breader.translation.BookRange
import ru.nikitagrgv.breader.translation.TextPosition

/**
 * The text as it's searched: in lower case, with all kinds of apostrophes and quotes made plain, and "ё" as "е", which
 * Russian books use either way. It's as long as the text, so the offsets of what's found in it are the text's.
 */
internal fun foldForSearch(text: String): String {
    val folded = CharArray(text.length)
    for (index in text.indices) {
        val char = text[index].lowercaseChar()
        folded[index] = when (char) {
            '’', '‘', 'ʼ', '`', '´' -> '\''
            '“', '”', '„', '«', '»' -> '"'
            '‐', '‑', '–' -> '-'
            'ё' -> 'е'
            else -> char
        }
    }
    return String(folded)
}

/** Adds where the folded [query] is in the paragraph [index], whose text is [text], up to [limit] in all. */
internal fun findIn(text: String, index: Int, query: String, matches: MutableList<BookRange>, limit: Int) {
    if (query.isEmpty()) return
    val folded = foldForSearch(text)
    var from = 0
    while (matches.size < limit) {
        val at = folded.indexOf(query, from)
        if (at < 0) return
        matches += BookRange(TextPosition(index, at), TextPosition(index, at + query.length))
        from = at + query.length
    }
}

/** Searching the open book for a text, one paragraph at a time, as a match can't run from one to another. */
@Stable
internal class BookSearch(private val content: BookContent) {
    /** In the order of the book. */
    var matches by mutableStateOf<List<BookRange>>(emptyList())
        private set

    /** The index of the match being shown; -1 if there's none. */
    var current by mutableIntStateOf(-1)
        private set

    /** Whether the book is still being searched, which takes a while in a PDF whose pages are read as it goes. */
    var searching by mutableStateOf(false)
        private set

    /** Whether there are more matches than are kept. */
    val truncated: Boolean get() = matches.size >= MAX_MATCHES

    val currentMatch: BookRange? get() = matches.getOrNull(current)

    /**
     * Finds [query] in the book. The match shown first is the first one from the paragraph [from] on, where the reader
     * is, or the first one in the book if there's none after it.
     */
    suspend fun run(query: String, from: Int) {
        val folded = foldForSearch(query.trim())
        matches = emptyList()
        current = -1
        if (folded.isEmpty()) return
        searching = true
        try {
            val found = ArrayList<BookRange>()
            if (content is PdfBook) {
                // Unread pages are read on the way, so the matches come as it goes.
                for (page in content.pageSizes.indices) {
                    val text = content.pageText(page) ?: continue
                    val before = found.size
                    findIn(text, page, folded, found, MAX_MATCHES)
                    if (found.size > before) show(found.toList(), from)
                    if (found.size >= MAX_MATCHES) break
                }
            } else {
                withContext(Dispatchers.Default) {
                    for ((index, paragraph) in content.paragraphs.withIndex()) {
                        findIn(paragraph.text, index, folded, found, MAX_MATCHES)
                        if (found.size >= MAX_MATCHES) break
                    }
                }
            }
            show(found, from)
            if (current < 0 && found.isNotEmpty()) current = 0
        } finally {
            searching = false
        }
    }

    fun next() {
        if (matches.isNotEmpty()) current = (current + 1).mod(matches.size)
    }

    fun previous() {
        if (matches.isNotEmpty()) current = (current - 1).mod(matches.size)
    }

    /** Takes the matches found so far; the first match is chosen once there's one at or after [from]. */
    private fun show(found: List<BookRange>, from: Int) {
        matches = found
        if (current < 0) current = found.indexOfFirst { it.start.paragraph >= from }
    }

    private companion object {
        const val MAX_MATCHES = 5_000
    }
}
