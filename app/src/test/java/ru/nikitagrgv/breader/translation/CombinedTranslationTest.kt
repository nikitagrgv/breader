package ru.nikitagrgv.breader.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class CombinedTranslationTest {
    @Test
    fun `same translations are grouped and the most common goes first`() {
        val combined = combine(
            listOf(
                "a" to Translation("решит", listOf("сочтёт нужным")),
                "b" to Translation("Сочтёт нужным.", listOf("пожелает")),
                "c" to Translation("сочтёт нужным", listOf("решит", "пожелает", "захочет")),
            ),
        )
        assertEquals(listOf(Variant("Сочтёт нужным.", listOf("b", "c")), Variant("решит", listOf("a"))), combined.variants)
        // "пожелает" is offered twice, "захочет" once; "решит" and "сочтёт нужным" are main translations already.
        assertEquals(listOf("пожелает", "захочет"), combined.alternatives)
    }

    @Test
    fun `equally common translations are ranked by how often others offer them`() {
        val combined = combine(
            listOf(
                "a" to Translation("первый"),
                "b" to Translation("второй"),
                "c" to Translation("третий", listOf("второй")),
            ),
        )
        assertEquals(listOf("второй", "первый", "третий"), combined.variants.map { it.text })
    }

    @Test
    fun `only non-empty notes are kept`() {
        val combined = combine(listOf("a" to Translation("x", note = "idiom"), "b" to Translation("y", note = " ")))
        assertEquals(listOf("a" to "idiom"), combined.notes)
    }
}
