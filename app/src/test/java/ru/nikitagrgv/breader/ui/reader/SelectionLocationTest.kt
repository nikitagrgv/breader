package ru.nikitagrgv.breader.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.nikitagrgv.breader.format.Paragraph
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

    private val BookSelection.range get() = start to end

    @Test
    fun `selection in one paragraph is found by the anchors`() {
        // The second "saw", which indexOf() alone would miss.
        val selection = locateSelection(listOf(part(1, 11, 14)), anchors = 11 to 14, paragraphs)
        assertEquals(TextPosition(1, 11) to TextPosition(1, 14), selection?.range)
    }

    @Test
    fun `selection made backwards is found by the anchors`() {
        val selection = locateSelection(listOf(part(1, 11, 14)), anchors = 14 to 11, paragraphs)
        assertEquals(TextPosition(1, 11) to TextPosition(1, 14), selection?.range)
    }

    @Test
    fun `anchor at the end of the previous paragraph is skipped`() {
        // The start anchor is at the end of "Chapter 1", with nothing selected there.
        val selection = locateSelection(listOf(part(0, 9, 9), part(1, 0, 6)), anchors = 9 to 6, paragraphs)
        assertEquals(TextPosition(1, 0) to TextPosition(1, 6), selection?.range)
    }

    @Test
    fun `without anchors the first occurrence is taken`() {
        val selection = locateSelection(listOf(part(1, 11, 14)), anchors = null, paragraphs)
        assertEquals(TextPosition(1, 3) to TextPosition(1, 6), selection?.range)
    }

    @Test
    fun `selection of several paragraphs runs from the first part to the last`() {
        val parts = listOf(part(0, 8, 9), part(1, 0, 15), part(2, 0, 4))
        val selection = locateSelection(parts, anchors = 8 to 4, paragraphs)
        assertEquals(TextPosition(0, 8) to TextPosition(2, 4), selection?.range)
    }

    @Test
    fun `text that isn't from the book isn't located`() {
        assertNull(locateSelection(listOf(AnnotatedString("saw")), anchors = null, paragraphs))
        assertNull(locateSelection(emptyList(), anchors = null, paragraphs))
    }
}
