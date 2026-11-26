package ru.nikitagrgv.breader.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nikitagrgv.breader.translation.BookRange
import ru.nikitagrgv.breader.translation.TextPosition

class SelectionHistoryStorageTest {
    private val ranges = listOf(
        BookRange(TextPosition(1, 2), TextPosition(1, 5)),
        BookRange(TextPosition(3, 40), TextPosition(4, 1)),
    )

    @Test
    fun `ranges survive being stored`() {
        assertEquals(listOf(1, 2, 1, 5, 3, 40, 4, 1), ranges.toInts())
        assertEquals(ranges, rangesFromInts(ranges.toInts()))
    }

    @Test
    fun `what can't be a range is left out`() {
        // Not a number, which is read as -1.
        assertEquals(ranges.drop(1), rangesFromInts(listOf(1, -1, 1, 5, 3, 40, 4, 1)))
        // The end before the start.
        assertEquals(ranges.drop(1), rangesFromInts(listOf(1, 5, 1, 2, 3, 40, 4, 1)))
        // Cut short.
        assertEquals(ranges.take(1), rangesFromInts(listOf(1, 2, 1, 5, 3, 40)))
    }
}
