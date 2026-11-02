package ru.nikitagrgv.breader.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
