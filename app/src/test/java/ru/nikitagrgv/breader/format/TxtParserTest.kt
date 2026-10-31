package ru.nikitagrgv.breader.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtParserTest {
    private val line = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod"

    @Test
    fun `one line per paragraph is kept as is`() {
        val text = "First paragraph that is long enough. ".repeat(5) + "\nShort line\n\n" + "Second one. ".repeat(20)
        assertEquals(3, TxtParser.splitParagraphs(text).size)
    }

    @Test
    fun `hard-wrapped text is joined at blank lines`() {
        val text = List(3) { List(4) { line }.joinToString("\n") }.joinToString("\n\n")
        val paragraphs = TxtParser.splitParagraphs(text)
        assertEquals(3, paragraphs.size)
        assertEquals(List(4) { line }.joinToString(" "), paragraphs[0])
    }

    @Test
    fun `hard-wrapped text is joined at indented lines`() {
        val text = List(3) { "    $line\n" + List(3) { line }.joinToString("\n") }.joinToString("\n")
        assertEquals(3, TxtParser.splitParagraphs(text).size)
    }

    @Test
    fun `double-spaced text converted from PDF is joined back into paragraphs`() {
        val full = "word ".repeat(15) + "word" // 79 chars: a line filled up to the text width
        val longEnd = "word ".repeat(13) + "ends." // 70 chars: a paragraph ending almost at full width
        val pages = (1..12).map { page ->
            listOf(
                full, full, "end of the first paragraph $page.",
                full, longEnd,
                "'Dialogue $page,' she said.",
                full, longEnd,
                "$page",
                full, full,
                "Page $page", "Misery.txt",
            )
        }
        // Like misery.txt: an empty line after each line and a space before it.
        val text = pages.flatten().joinToString("\r\n\r\n") { " $it" }

        val paragraphs = TxtParser.splitParagraphs(text)

        assertTrue(paragraphs.none { "Page" in it || "Misery.txt" in it })
        assertEquals("$full $full end of the first paragraph 1.", paragraphs[0])
        assertEquals("$full $longEnd", paragraphs[1])
        assertEquals("'Dialogue 1,' she said.", paragraphs[2])
        assertEquals("$full $longEnd", paragraphs[3])
        assertEquals("1", paragraphs[4])
        // The last paragraph of a page continues on the next one.
        assertEquals("$full $full $full $full end of the first paragraph 2.", paragraphs[5])
        assertEquals(61, paragraphs.size)
    }

    @Test
    fun `non-UTF-8 text is decoded as windows-1251`() {
        val bytes = "Привет, мир".toByteArray(charset("windows-1251"))
        assertEquals("Привет, мир", TxtParser.decode(bytes))
    }

    @Test
    fun `UTF-8 with BOM is decoded`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Привет".toByteArray()
        assertEquals("Привет", TxtParser.decode(bytes))
    }
}
