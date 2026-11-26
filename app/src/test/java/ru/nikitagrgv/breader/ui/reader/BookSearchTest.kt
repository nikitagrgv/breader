package ru.nikitagrgv.breader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nikitagrgv.breader.translation.BookRange
import ru.nikitagrgv.breader.translation.TextPosition

class BookSearchTest {
    private fun matches(text: String, query: String, limit: Int = 100): List<Pair<Int, Int>> {
        val found = ArrayList<BookRange>()
        findIn(text, 3, foldForSearch(query), found, limit)
        return found.map { range ->
            assertEquals(3, range.start.paragraph)
            assertEquals(3, range.end.paragraph)
            range.start.offset to range.end.offset
        }
    }

    @Test
    fun `case doesn't matter`() {
        assertEquals(listOf(0 to 3, 8 to 11), matches("The cat the dog", "the"))
    }

    @Test
    fun `kinds of apostrophes and quotes don't matter`() {
        assertEquals(listOf(4 to 9), matches("I’m don’t know", "don't"))
        assertEquals(listOf(4 to 10), matches("She «said» it", "\"said\""))
        assertEquals(listOf(0 to 4), matches("Ёлка", "елка"))
    }

    @Test
    fun `matches don't overlap and stop at the limit`() {
        assertEquals(listOf(0 to 2, 2 to 4), matches("aaaaa", "aa"))
        assertEquals(listOf(0 to 1), matches("aaa", "a", limit = 1))
        assertEquals(emptyList<Pair<Int, Int>>(), matches("abc", ""))
    }

    @Test
    fun `folding keeps the length`() {
        val text = "İSTANBUL «Ёж» don’t"
        assertEquals(text.length, foldForSearch(text).length)
    }
}
