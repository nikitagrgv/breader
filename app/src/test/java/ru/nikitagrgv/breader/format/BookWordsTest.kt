package ru.nikitagrgv.breader.format

import org.junit.Assert.assertEquals
import org.junit.Test

class BookWordsTest {
    @Test
    fun `words are runs of letters and digits`() {
        assertEquals(0, countWords(""))
        assertEquals(0, countWords(" — … "))
        assertEquals(4, countWords("It was 1984, again."))
        assertEquals(3, countWords("Он  сказал:\nнет"))
    }

    @Test
    fun `apostrophes and hyphens inside words don't split them`() {
        assertEquals(1, countWords("don't"))
        assertEquals(1, countWords("don’t"))
        assertEquals(1, countWords("well-known"))
        assertEquals(1, countWords("extra­ordinary"))
        // Unless they're at the end of a word.
        assertEquals(2, countWords("dogs' bowls"))
        assertEquals(2, countWords("well - known"))
        assertEquals(2, countWords("word- word"))
    }

    @Test
    fun `stress marks don't split words`() {
        assertEquals(1, countWords("соба́ка"))
    }

    @Test
    fun `words read are counted by paragraph and share of it`() {
        val words = BookWords.of(intArrayOf(10, 0, 30))
        assertEquals(40, words.total)
        assertEquals(0f, words.at(0, 0f), 0f)
        assertEquals(5f, words.at(0, 0.5f), 0f)
        assertEquals(10f, words.at(1, 0.5f), 0f)
        assertEquals(25f, words.at(2, 0.5f), 0f)
        assertEquals(40f, words.at(2, 1f), 0f)
    }

    @Test
    fun `a fraction is found in the paragraph that has it`() {
        val words = BookWords.of(intArrayOf(10, 0, 30))
        assertEquals(0 to 0f, words.find(0f))
        assertEquals(0 to 0.5f, words.find(0.125f))
        // The empty paragraph starts where the third one does, which is the last one starting there.
        assertEquals(2 to 0f, words.find(0.25f))
        assertEquals(2 to 0.5f, words.find(0.625f))
        assertEquals(2 to 1f, words.find(1f))
    }

    @Test
    fun `a book without paragraphs has no words`() {
        val words = BookWords.of(emptyList())
        assertEquals(0, words.total)
        assertEquals(0f, words.at(0, 0.5f), 0f)
        assertEquals(0 to 0f, words.find(0.5f))
    }

    @Test
    fun `paragraphs are counted by their text`() {
        val words = BookWords.of(listOf(Paragraph("One two."), Paragraph(""), Paragraph("Three")))
        assertEquals(3, words.total)
        assertEquals(3, words.paragraphs)
    }
}
