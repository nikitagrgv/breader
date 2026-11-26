package ru.nikitagrgv.breader.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordFrequencyTest {
    private fun frequency(vararg texts: String): WordFrequency {
        val counter = WordCounter()
        texts.forEach(counter::add)
        return counter.result()
    }

    private fun WordFrequency.counts(): List<Pair<String, Int>> = words.map { it.word to it.count }

    @Test
    fun `words are counted, the most frequent first`() {
        val frequency = frequency("the cat and the dog and the bird")
        assertEquals(listOf("the" to 3, "and" to 2, "bird" to 1, "cat" to 1, "dog" to 1), frequency.counts())
        assertEquals(8, frequency.total)
    }

    @Test
    fun `case doesn't matter, and the word is in lowercase if the book has it so`() {
        val frequency = frequency("The end. Then the end came. I said London, I", "London")
        assertEquals("the", frequency.words.first { it.key == "the" }.word)
        assertEquals(2, frequency.words.first { it.key == "the" }.count)
        assertEquals("I", frequency.words.first { it.key == "i" }.word)
        assertEquals("London", frequency.words.first { it.key == "london" }.word)
        assertEquals(2, frequency.words.first { it.key == "london" }.count)
    }

    @Test
    fun `numbers aren't words, but words with digits are`() {
        val frequency = frequency("1984, in the 1st 1,000")
        assertEquals(listOf("1st" to 1, "in" to 1, "the" to 1), frequency.counts())
        assertEquals(3, frequency.total)
    }

    @Test
    fun `apostrophes and hyphens inside words are the usual ones`() {
        val frequency = frequency("don't don’t donʼt well-known well‐known extra­ordinary dogs' bowls")
        assertEquals(
            listOf("don't" to 3, "well-known" to 2, "bowls" to 1, "dogs" to 1, "extraordinary" to 1),
            frequency.counts(),
        )
    }

    @Test
    fun `a word doesn't go on from one part into the next`() {
        assertEquals(listOf("one" to 1, "two" to 1), frequency("one", "two").counts())
    }

    @Test
    fun `stress marks don't matter, but accents written apart do`() {
        // "й" as "и" with a combining breve, which isn't a stress mark: composed, it's the letter.
        val frequency = frequency("соба́ка собака Соба́ка йод йод")
        assertEquals(listOf("собака" to 3, "йод" to 2), frequency.counts())
    }

    @Test
    fun `the coverage of a word includes the ones above it`() {
        val list = frequency("a a a a b b b c c d").without(emptySet())
        assertEquals(10, list.total)
        assertEquals(0.0, list.hiddenShare, 0.0)
        assertEquals(0.4, list.share(0), 1e-9)
        assertEquals(0.4, list.coverage(0), 1e-9)
        assertEquals(0.7, list.coverage(1), 1e-9)
        assertEquals(0.9, list.coverage(2), 1e-9)
        assertEquals(1.0, list.coverage(3), 1e-9)
        assertEquals(0.0, list.coverage(-1), 0.0)
    }

    @Test
    fun `hidden words count as known in the coverage`() {
        val list = frequency("a a a a b b b c c d").without(setOf("a", "c"))
        assertEquals(listOf("b", "d"), list.words.map { it.word })
        assertEquals(0.6, list.hiddenShare, 1e-9)
        assertEquals(0.6, list.coverage(-1), 1e-9)
        assertEquals(0.3, list.share(0), 1e-9)
        assertEquals(0.9, list.coverage(0), 1e-9)
        assertEquals(1.0, list.coverage(1), 1e-9)
    }

    @Test
    fun `a text without words has nothing to cover`() {
        val frequency = frequency("— 42 …")
        assertEquals(0, frequency.total)
        assertEquals(0.0, frequency.share(setOf("the")), 0.0)
        assertEquals(0.0, frequency.without(emptySet()).hiddenShare, 0.0)
    }

    @Test
    fun `the language is told by its common words`() {
        assertEquals(
            CommonWords.ENGLISH,
            CommonWords.of(frequency("It was the best of times, it was the worst of times, it was the age of wisdom")),
        )
        assertEquals(
            CommonWords.RUSSIAN,
            CommonWords.of(frequency("Он сказал, что не может прийти, потому что у него было много работы, и она ему поверила.")),
        )
        assertEquals(
            CommonWords.GERMAN,
            CommonWords.of(frequency("Als Gregor Samsa eines Morgens aus unruhigen Träumen erwachte, fand er sich in seinem Bett zu einem ungeheueren Ungeziefer verwandelt.")),
        )
    }

    @Test
    fun `a language without known common words has none`() {
        assertNull(CommonWords.of(frequency("Lorem ipsum dolor sit amet consectetur adipiscing elit")))
        assertNull(CommonWords.of(frequency("")))
    }

    @Test
    fun `paragraphs are counted by their text`() {
        val frequency = WordFrequency.of(listOf(Paragraph("One two."), Paragraph(""), Paragraph("two one")))
        assertEquals(listOf("one" to 2, "two" to 2), frequency.counts())
    }
}
