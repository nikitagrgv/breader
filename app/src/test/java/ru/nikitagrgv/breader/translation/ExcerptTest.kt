package ru.nikitagrgv.breader.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nikitagrgv.breader.format.Paragraph

class ExcerptTest {
    private val paragraphs = listOf(
        Paragraph("One two three."),
        Paragraph(""),
        Paragraph("Four five six seven eight."),
        Paragraph("Nine ten."),
    )

    private fun excerpt(start: TextPosition, end: TextPosition, words: Int) =
        BookSelection(paragraphs, start, end).excerpt(words)

    @Test
    fun `a range is found in the book`() {
        val range = BookRange(TextPosition(0, 4), TextPosition(2, 4))
        assertEquals(range, range.inBook(paragraphs)?.range)
    }

    @Test
    fun `a range that isn't in the book any more isn't found`() {
        fun position(paragraph: Int, offset: Int) = TextPosition(paragraph, offset)
        assertNull(BookRange(position(0, 0), position(4, 1)).inBook(paragraphs))
        assertNull(BookRange(position(5, 0), position(5, 1)).inBook(paragraphs))
        assertNull(BookRange(position(0, 15), position(2, 1)).inBook(paragraphs))
        assertNull(BookRange(position(0, 0), position(0, 15)).inBook(paragraphs))
        assertNull(BookRange(position(0, 3), position(0, 3)).inBook(paragraphs))
    }

    @Test
    fun `ranges touch when they overlap or are next to each other`() {
        fun range(start: Int, end: Int) = BookRange(TextPosition(0, start), TextPosition(0, end))
        assertTrue(range(0, 5).touches(range(3, 8)))
        assertTrue(range(0, 5).touches(range(5, 8)))
        assertTrue(range(5, 8).touches(range(0, 5)))
        assertFalse(range(0, 5).touches(range(6, 8)))
    }

    @Test
    fun `part of a paragraph is found within one paragraph`() {
        val selection = BookSelection(paragraphs, TextPosition(2, 10), TextPosition(2, 13))
        assertEquals(10 to 13, selection.partOf(2))
        assertNull(selection.partOf(1))
        assertNull(selection.partOf(3))
    }

    @Test
    fun `part of a paragraph is found across several paragraphs`() {
        val selection = BookSelection(paragraphs, TextPosition(0, 4), TextPosition(3, 4))
        assertEquals(4 to 14, selection.partOf(0))
        assertEquals(0 to 0, selection.partOf(1))
        assertEquals(0 to 26, selection.partOf(2))
        assertEquals(0 to 4, selection.partOf(3))
    }

    @Test
    fun `context is taken from the same paragraph when it's enough`() {
        // "six"
        val excerpt = excerpt(TextPosition(2, 10), TextPosition(2, 13), words = 2)
        assertEquals(Excerpt("Four five ", "six", " seven eight."), excerpt)
    }

    @Test
    fun `context goes on into neighboring paragraphs, skipping empty ones`() {
        val excerpt = excerpt(TextPosition(2, 10), TextPosition(2, 13), words = 4)
        assertEquals(Excerpt("two three.\n\nFour five ", "six", " seven eight.\nNine ten."), excerpt)
    }

    @Test
    fun `context stops at the book edges`() {
        val excerpt = excerpt(TextPosition(0, 0), TextPosition(0, 3), words = 50)
        assertEquals(Excerpt("", "One", " two three.\n\nFour five six seven eight.\nNine ten."), excerpt)
        assertEquals("One two three.\n\nFour five six seven eight.\n", excerpt(TextPosition(3, 0), TextPosition(3, 4), 50).before)
    }

    @Test
    fun `no context when no words are asked for`() {
        assertEquals(Excerpt("", "six", ""), excerpt(TextPosition(2, 10), TextPosition(2, 13), words = 0))
    }

    @Test
    fun `fragment of several paragraphs is joined by line breaks`() {
        val excerpt = excerpt(TextPosition(0, 8), TextPosition(2, 4), words = 1)
        assertEquals(Excerpt("two ", "three.\n\nFour", " five"), excerpt)
    }

    @Test
    fun `a word cut by the selection counts as a word`() {
        // "ven" of "seven"
        val excerpt = excerpt(TextPosition(2, 16), TextPosition(2, 19), words = 1)
        assertEquals(Excerpt("se", "ven", " eight."), excerpt)
    }
}
