package ru.nikitagrgv.breader.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nikitagrgv.breader.translation.ModelInfo

class PickerListTest {
    // In the order of popularity, as the catalog comes.
    private val models = listOf(
        ModelInfo("a/popular-paid", "Popular Paid", 1e-6, 2e-6, created = 3),
        ModelInfo("b/popular-free:free", "Popular Free", 0.0, 0.0, created = 1),
        ModelInfo("openrouter/auto", "Auto Router", null, null, created = 4),
        ModelInfo("c/cheap", "Cheap", 1e-7, 1e-7, created = 2),
        ModelInfo("d/less-popular-free:free", "Less Popular Free", 0.0, 0.0, created = 5),
    )

    private fun ids(sort: ModelSort, query: String = "", freeOnly: Boolean = false, selectedOnly: Boolean = false, selection: List<String> = emptyList()) =
        pickerList(models, sort, query, freeOnly, selectedOnly, selection).map { it.id }

    @Test
    fun `cheapest first, then the more popular, unknown prices last`() {
        assertEquals(
            listOf("b/popular-free:free", "d/less-popular-free:free", "c/cheap", "a/popular-paid", "openrouter/auto"),
            ids(ModelSort.PRICE),
        )
    }

    @Test
    fun `newest first`() {
        assertEquals(listOf("d/less-popular-free:free", "openrouter/auto", "a/popular-paid", "c/cheap", "b/popular-free:free"), ids(ModelSort.NEWEST))
    }

    @Test
    fun `every search word has to match the name or the ID`() {
        assertEquals(listOf("a/popular-paid"), ids(ModelSort.POPULARITY, query = " paid  POPULAR "))
        assertEquals(listOf("openrouter/auto"), ids(ModelSort.POPULARITY, query = "openrouter/"))
    }

    @Test
    fun `filters by price and selection`() {
        assertEquals(listOf("b/popular-free:free", "d/less-popular-free:free"), ids(ModelSort.POPULARITY, freeOnly = true))
        assertEquals(listOf("c/cheap"), ids(ModelSort.POPULARITY, selectedOnly = true, selection = listOf("c/cheap")))
    }

    @Test
    fun `picked models missing from the catalog are still listed`() {
        assertEquals(listOf("c/cheap", "x/removed"), ids(ModelSort.PRICE, selectedOnly = true, selection = listOf("x/removed", "c/cheap")))
    }
}
