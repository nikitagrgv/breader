package ru.nikitagrgv.breader.ui.reader

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.nikitagrgv.breader.format.Paragraph
import ru.nikitagrgv.breader.format.PdfBook
import ru.nikitagrgv.breader.format.PdfPageText
import ru.nikitagrgv.breader.format.PdfRect
import ru.nikitagrgv.breader.translation.BookRange
import ru.nikitagrgv.breader.translation.BookSelection
import ru.nikitagrgv.breader.translation.TextPosition

/** A selected range of a PDF; [end] is exclusive and always after [start]. */
internal data class PdfTextRange(val start: TextPosition, val end: TextPosition) {
    /** The range that has both this one and [other] in it. */
    fun union(other: PdfTextRange) = PdfTextRange(minOf(start, other.start), maxOf(end, other.end))

    /**
     * The range with one of its ends moved. The ends don't cross and don't stop at the very edge of a page, as there
     * would be nothing selected on that page to hold the handle. A range with only spaces in it isn't taken either.
     */
    fun withEndMoved(isStart: Boolean, to: TextPosition, paragraphs: List<Paragraph>): PdfTextRange {
        val moved = if (isStart) {
            val atPageEnd = to.offset >= paragraphs[to.paragraph].text.length && to.paragraph < paragraphs.lastIndex
            copy(start = if (atPageEnd) TextPosition(to.paragraph + 1, 0) else to)
        } else {
            val atPageStart = to.offset == 0 && to.paragraph > 0
            copy(end = if (atPageStart) TextPosition(to.paragraph - 1, paragraphs[to.paragraph - 1].text.length) else to)
        }
        if (moved.start >= moved.end) return this
        val text = BookSelection(paragraphs, moved.start, moved.end).excerpt(contextWords = 0).fragment
        return if (text.isBlank()) this else moved
    }
}

/** The selection in the pages of a PDF, which is made by [PdfPages]. */
@Stable
internal class PdfSelection(val book: PdfBook) : ReaderSelection {
    var range by mutableStateOf<PdfTextRange?>(null)
        private set

    override val paragraphs: List<Paragraph> get() = book.paragraphs
    override val hasSelection: Boolean get() = range != null
    override val text: String get() = locate()?.excerpt(contextWords = 0)?.fragment.orEmpty()

    override fun locate(): BookSelection? = range?.let { BookSelection(book.paragraphs, it.start, it.end) }

    override fun clear() {
        range = null
    }

    fun select(range: PdfTextRange) {
        this.range = range
    }

    /** The pages may not have been read yet; the context of a selection comes from the neighboring ones as well. */
    override suspend fun prepare(range: BookRange) {
        for (page in (range.start.paragraph - 1)..(range.end.paragraph + 1)) {
            if (page in book.pageSizes.indices) book.text(page)
        }
    }

    override fun select(target: BookSelection, partly: Boolean): Boolean {
        select(PdfTextRange(target.start, target.end))
        return true
    }

    fun moveEnd(isStart: Boolean, to: TextPosition) {
        range = range?.withEndMoved(isStart, to, book.paragraphs)
    }

    /** The rectangles of the selected lines of a page, whose text is [text]. */
    fun rectsOn(page: Int, text: PdfPageText): List<PdfRect> = locate()?.rectsOn(page, text).orEmpty()
}

/** The rectangles of the lines of this selection on a page, whose text is [text]. */
internal fun BookSelection.rectsOn(page: Int, text: PdfPageText): List<PdfRect> {
    val (start, end) = partOf(page) ?: return emptyList()
    return text.rects(start, end)
}
