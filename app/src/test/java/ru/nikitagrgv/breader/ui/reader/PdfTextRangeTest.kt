package ru.nikitagrgv.breader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nikitagrgv.breader.format.Paragraph
import ru.nikitagrgv.breader.translation.TextPosition

class PdfTextRangeTest {
    private val pages = listOf(Paragraph("One two three"), Paragraph("Four five"), Paragraph("Six"))

    private fun range(startPage: Int, start: Int, endPage: Int, end: Int) =
        PdfTextRange(TextPosition(startPage, start), TextPosition(endPage, end))

    @Test
    fun `union covers both ranges`() {
        val word = range(1, 5, 1, 9)
        assertEquals(range(1, 5, 2, 3), word.union(range(2, 0, 2, 3)))
        assertEquals(range(0, 8, 1, 9), word.union(range(0, 8, 0, 13)))
        assertEquals(word, word.union(word))
    }

    @Test
    fun `ends move within the page and on to other pages`() {
        val selected = range(0, 4, 0, 7)
        assertEquals(range(0, 0, 0, 7), selected.withEndMoved(isStart = true, TextPosition(0, 0), pages))
        assertEquals(range(0, 4, 1, 4), selected.withEndMoved(isStart = false, TextPosition(1, 4), pages))
    }

    @Test
    fun `ends don't cross or meet`() {
        val selected = range(0, 4, 0, 7)
        assertEquals(selected, selected.withEndMoved(isStart = true, TextPosition(0, 7), pages))
        assertEquals(selected, selected.withEndMoved(isStart = true, TextPosition(1, 0), pages))
        assertEquals(selected, selected.withEndMoved(isStart = false, TextPosition(0, 4), pages))
        assertEquals(selected, selected.withEndMoved(isStart = false, TextPosition(0, 2), pages))
    }

    @Test
    fun `end at the very start of a page stays on the previous page`() {
        val selected = range(0, 4, 0, 7)
        assertEquals(range(0, 4, 0, 13), selected.withEndMoved(isStart = false, TextPosition(1, 0), pages))
    }

    @Test
    fun `start at the very end of a page goes on to the next page`() {
        val selected = range(0, 4, 1, 9)
        assertEquals(range(1, 0, 1, 9), selected.withEndMoved(isStart = true, TextPosition(0, 13), pages))
    }

    @Test
    fun `range with only spaces isn't taken`() {
        // The space between "One" and "two".
        val selected = range(0, 3, 0, 7)
        assertEquals(selected, selected.withEndMoved(isStart = false, TextPosition(0, 4), pages))
    }
}
