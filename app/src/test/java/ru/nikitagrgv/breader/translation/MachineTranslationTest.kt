package ru.nikitagrgv.breader.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineTranslationTest {
    private fun fitsIn(length: Int): (String) -> Boolean = { it.length <= length }

    @Test
    fun `short text goes in one request`() {
        assertEquals(listOf(Chunk("Hello there.", "")), splitForRequests("  Hello there.\n", fitsIn(100)))
        assertEquals(emptyList<Chunk>(), splitForRequests("  ", fitsIn(100)))
    }

    @Test
    fun `long text is split between sentences, keeping what separated them`() {
        val chunks = splitForRequests("One two three. Four five six.\nSeven eight.", fitsIn(20))
        assertEquals(listOf(Chunk("One two three.", " "), Chunk("Four five six.", "\n"), Chunk("Seven eight.", "")), chunks)
    }

    @Test
    fun `sentences are put together while they fit`() {
        val chunks = splitForRequests("A b. C d. E f. G h.", fitsIn(10))
        assertEquals(listOf("A b. C d.", "E f. G h."), chunks.map { it.text })
    }

    @Test
    fun `too long sentence is split between words, and a word between letters`() {
        val chunks = splitForRequests("Supercalifragilistic is a long word", fitsIn(10))
        assertTrue(chunks.all { it.text.length <= 10 })
        assertEquals("Supercalifragilistic is a long word", chunks.joinToString("") { it.text + it.separator })
    }

    @Test
    fun `languages are found by name or code`() {
        assertEquals("en", languageCode("English"))
        assertEquals("en", languageCode(" english "))
        assertEquals("ru", languageCode("Russian"))
        // In the language itself.
        assertEquals("ru", languageCode("русский"))
        assertEquals("de", languageCode("de"))
        // Java's old code for Hebrew is "iw".
        assertEquals("he", languageCode("Hebrew"))
        assertNull(languageCode(""))
        assertNull(languageCode("Klingonese"))
    }
}
