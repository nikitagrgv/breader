package ru.nikitagrgv.breader.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.nikitagrgv.breader.format.Paragraph
import ru.nikitagrgv.breader.translation.BookRange
import ru.nikitagrgv.breader.translation.BookSelection
import ru.nikitagrgv.breader.translation.TextPosition

class SelectionLocationTest {
    private val paragraphs = listOf(
        Paragraph("Chapter 1"),
        Paragraph("He saw the saw."),
        Paragraph("Then he left."),
    )

    /** What [androidx.compose.foundation.text.selection.SelectionState.selectedTexts] gives for a part of a paragraph. */
    private fun part(index: Int, start: Int, end: Int): AnnotatedString =
        buildAnnotatedString {
            append(paragraphs[index].text)
            tagParagraph(index)
        }.subSequence(start, end)

    private val BookSelection.positions get() = start to end

    @Test
    fun `selection in one paragraph is found by the anchors`() {
        // The second "saw", which indexOf() alone would miss.
        val selection = locateSelection(listOf(part(1, 11, 14)), anchors = 11 to 14, paragraphs)
        assertEquals(TextPosition(1, 11) to TextPosition(1, 14), selection?.positions)
    }

    @Test
    fun `selection made backwards is found by the anchors`() {
        val selection = locateSelection(listOf(part(1, 11, 14)), anchors = 14 to 11, paragraphs)
        assertEquals(TextPosition(1, 11) to TextPosition(1, 14), selection?.positions)
    }

    @Test
    fun `anchor at the end of the previous paragraph is skipped`() {
        // The start anchor is at the end of "Chapter 1", with nothing selected there.
        val selection = locateSelection(listOf(part(0, 9, 9), part(1, 0, 6)), anchors = 9 to 6, paragraphs)
        assertEquals(TextPosition(1, 0) to TextPosition(1, 6), selection?.positions)
    }

    @Test
    fun `without anchors the first occurrence is taken`() {
        val selection = locateSelection(listOf(part(1, 11, 14)), anchors = null, paragraphs)
        assertEquals(TextPosition(1, 3) to TextPosition(1, 6), selection?.positions)
    }

    @Test
    fun `selection of several paragraphs runs from the first part to the last`() {
        val parts = listOf(part(0, 8, 9), part(1, 0, 15), part(2, 0, 4))
        val selection = locateSelection(parts, anchors = 8 to 4, paragraphs)
        assertEquals(TextPosition(0, 8) to TextPosition(2, 4), selection?.positions)
    }

    @Test
    fun `text that isn't from the book isn't located`() {
        assertNull(locateSelection(listOf(AnnotatedString("saw")), anchors = null, paragraphs))
        assertNull(locateSelection(emptyList(), anchors = null, paragraphs))
    }

    private val shown = listOf(part(0, 0, 9), part(1, 0, 15))

    private fun range(startParagraph: Int, start: Int, endParagraph: Int, end: Int) =
        BookRange(TextPosition(startParagraph, start), TextPosition(endParagraph, end))

    @Test
    fun `a place in the book is found among the texts on the screen`() {
        // "saw" in the second paragraph, after the 9 characters of the first one.
        assertEquals(TextRange(12, 15), rangeAmongTexts(shown, range(1, 3, 1, 6), partly = false))
    }

    @Test
    fun `a range over several texts is found`() {
        assertEquals(TextRange(4, 12), rangeAmongTexts(shown, range(0, 4, 1, 3), partly = false))
    }

    @Test
    fun `a range that goes off the screen is found only if a part of it will do`() {
        val offScreen = range(1, 3, 2, 4)
        assertNull(rangeAmongTexts(shown, offScreen, partly = false))
        assertEquals(TextRange(12, 24), rangeAmongTexts(shown, offScreen, partly = true))
    }

    @Test
    fun `a range that starts off the screen isn't found`() {
        assertNull(rangeAmongTexts(shown, range(2, 0, 2, 4), partly = true))
        assertNull(rangeAmongTexts(emptyList(), range(0, 0, 0, 4), partly = true))
    }
}
