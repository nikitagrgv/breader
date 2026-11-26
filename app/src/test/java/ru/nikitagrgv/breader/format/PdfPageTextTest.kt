package ru.nikitagrgv.breader.format

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageTextTest {
    /**
     * A page where each line is at its own height and each character is 10 points wide and 10 high,
     * with 2 points between the lines. Line ends are given the way PDFium gives them.
     */
    private fun page(vararg lines: String): PdfPageText {
        val raw = lines.joinToString("\r\n")
        val boxes = FloatArray(raw.length * 4)
        var line = 0
        var column = 0
        for ((index, char) in raw.withIndex()) {
            if (char == '\n') {
                line++
                column = 0
                continue
            }
            if (char == '\r') continue
            val top = line * 12f
            floatArrayOf(column * 10f, top, column * 10f + 10f, top + 10f).copyInto(boxes, index * 4)
            column++
        }
        return PdfPageText.of(raw, boxes)
    }

    @Test
    fun `lines are joined into running text`() {
        assertEquals("One two three", page("One two", "three").text)
    }

    @Test
    fun `extra whitespace is collapsed and trimmed`() {
        assertEquals("a b c", page("  a   b", "", "c  ").text)
    }

    @Test
    fun `word hyphenated at the end of a line is put together`() {
        assertEquals("example of it", page("exam-", "ple of it").text)
    }

    @Test
    fun `line end hyphens made by PDFium put a word together whatever follows`() {
        assertEquals("examPle", page("exam\u0002", "Ple").text)
        assertEquals("example", page("exam￾", "ple").text)
        assertEquals("example", page("exam­", "ple").text)
    }

    @Test
    fun `hyphens that aren't breaking a word stay`() {
        assertEquals("well-known", page("well-known").text)
        // The next line doesn't go on with the word.
        assertEquals("Anglo- Saxon", page("Anglo-", "Saxon").text)
        // A dash, not a hyphen.
        assertEquals("yes - no", page("yes -", "no").text)
    }

    @Test
    fun `invisible characters are dropped`() {
        assertEquals("ab", page("a​b\u0001").text)
    }

    @Test
    fun `ligatures are spelled out and keep their place`() {
        val text = page("ﬁnd oﬃce")
        assertEquals("find office", text.text)
        assertEquals("find".length - 1, text.charAt(x = 25f, y = 5f))
        // All the letters of "ﬃ" are in its box; the first one is what's found.
        assertEquals("find o".length, text.charAt(x = 55f, y = 5f))
        assertEquals(listOf(PdfRect(0f, 0f, 30f, 10f)), text.rects(0, "find".length))
    }

    @Test
    fun `characters are found by their place despite what's dropped`() {
        // "two" is at the start of the second line.
        val text = page("One", "two")
        assertEquals('t', text.text[text.charAt(x = 5f, y = 17f)])
        assertEquals('o', text.text[text.charAt(x = 25f, y = 17f)])
    }

    @Test
    fun `a point past the end of a line stays on the line`() {
        val text = page("Short", "A much longer line")
        // To the right of "Short", straight above the second line's characters.
        assertEquals('t', text.text[text.charAt(x = 150f, y = 5f)])
    }

    @Test
    fun `a point between the lines of a column doesn't go to another column`() {
        val raw = "Left\r\nRight"
        val boxes = FloatArray(raw.length * 4)
        // "Left" is a line at the height of the point, far away; "Right" is close, just below it.
        for (i in 0 until 4) floatArrayOf(i * 10f, 0f, i * 10f + 10f, 10f).copyInto(boxes, i * 4)
        for (i in 0 until 5) floatArrayOf(200f + i * 10f, 11f, 210f + i * 10f, 21f).copyInto(boxes, (6 + i) * 4)
        val text = PdfPageText.of(raw, boxes)
        assertEquals('R', text.text[text.charAt(x = 205f, y = 10f)])
    }

    @Test
    fun `nothing is found further than asked for`() {
        val text = page("One")
        assertEquals(-1, text.charAt(x = 100f, y = 5f, maxDistance = 20f))
        assertEquals(2, text.charAt(x = 40f, y = 5f, maxDistance = 20f))
        assertEquals(-1, page("").charAt(x = 5f, y = 5f))
    }

    @Test
    fun `caret goes to the side of the character that's closer`() {
        val text = page("abc")
        assertEquals(1, text.caretAt(x = 12f, y = 5f))
        assertEquals(2, text.caretAt(x = 18f, y = 5f))
        assertEquals(3, text.caretAt(x = 100f, y = 5f))
        assertEquals(0, text.caretAt(x = -10f, y = 5f))
    }

    @Test
    fun `caret goes the other way in right-to-left text`() {
        val raw = "אב"
        // The first letter is on the right.
        val boxes = floatArrayOf(10f, 0f, 20f, 10f, 0f, 0f, 10f, 10f)
        val text = PdfPageText.of(raw, boxes)
        assertEquals(0, text.caretAt(x = 18f, y = 5f))
        assertEquals(1, text.caretAt(x = 12f, y = 5f))
    }

    @Test
    fun `whole word is found around a character`() {
        val text = page("Don't stop, now")
        assertEquals(0 to 5, text.wordAt(0))
        assertEquals(0 to 5, text.wordAt(4))
        assertEquals(6 to 10, text.wordAt(8))
        assertEquals(12 to 15, text.wordAt(14))
    }

    @Test
    fun `punctuation is on its own`() {
        assertEquals(10 to 11, page("Don't stop, now").wordAt(10))
    }

    @Test
    fun `word split between lines is found as a whole`() {
        val text = page("an exam-", "ple here")
        assertEquals("an example here", text.text)
        assertEquals(3 to 10, text.wordAt(4))
    }

    @Test
    fun `rects are per line and leave out what isn't drawn`() {
        val text = page("One two", "three four")
        assertEquals(
            listOf(PdfRect(40f, 0f, 70f, 10f), PdfRect(0f, 12f, 50f, 22f)),
            text.rects("One ".length, "One two three".length),
        )
    }

    @Test
    fun `rects of nothing or only spaces are empty`() {
        val text = page("One two")
        assertEquals(emptyList<PdfRect>(), text.rects(2, 2))
        assertEquals(emptyList<PdfRect>(), text.rects(3, 4))
    }

    @Test
    fun `page without text has an empty text`() {
        assertEquals("", PdfPageText.of("", FloatArray(0)).text)
    }
}
