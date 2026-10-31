package ru.nikitagrgv.breader.format

import org.junit.Assert.assertEquals
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
    fun `short lines without separators are not joined`() {
        val text = List(10) { line }.joinToString("\n")
        assertEquals(10, TxtParser.splitParagraphs(text).size)
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
