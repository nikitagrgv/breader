package ru.nikitagrgv.breader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BookOrderTest {
    private fun List<String>.reordered(vararg keys: String) = reordered(keys.toList()) { it }

    @Test
    fun `reordered books take the places of each other, the rest stay`() {
        // The favorites a and c among the others.
        assertEquals(listOf("c", "b", "a", "d"), listOf("a", "b", "c", "d").reordered("c", "a"))
    }

    @Test
    fun `the whole library can be reordered`() {
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").reordered("c", "a", "b"))
    }

    @Test
    fun `books that aren't in the library are ignored`() {
        // Removed while being dragged.
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").reordered("x", "b", "a"))
        assertEquals(listOf("a", "b"), listOf("a", "b").reordered())
    }
}
