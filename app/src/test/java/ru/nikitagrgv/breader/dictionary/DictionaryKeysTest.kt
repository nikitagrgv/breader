package ru.nikitagrgv.breader.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryKeysTest {
    @Test
    fun `keys ignore case, apostrophes, spacing and stress marks`() {
        assertEquals("don't", dictionaryKey(" Don’t "))
        assertEquals("go off", dictionaryKey("Go\n  off"))
        assertEquals("собака", dictionaryKey("соба́ка"))
        // Letters with diacritics of their own stay.
        assertEquals("ёж café", dictionaryKey("Ёж Café"))
    }

    @Test
    fun `punctuation around the selection is dropped`() {
        assertEquals(listOf(listOf("“alarm,”", "alarm")), lookupKeys("“Alarm,”", "ru"))
        assertEquals(emptyList<List<String>>(), lookupKeys("…", "en"))
    }

    @Test
    fun `english possessives and base forms are tried after the text itself`() {
        assertEquals(listOf(listOf("john's", "john")), lookupKeys("John's", "en"))
        val dogs = lookupKeys("dogs'", "en")
        assertEquals(listOf("dogs'", "dogs"), dogs[0])
        assertTrue(dogs[1].toString(), "dog" in dogs[1])
    }

    @Test
    fun `no base forms are guessed for phrases and unknown languages`() {
        assertEquals(1, lookupKeys("went off", "en").size)
        assertEquals(1, lookupKeys("собаки", "ru").size)
    }

    @Test
    fun `english base forms include the right one`() {
        mapOf(
            "running" to "run",
            "stopped" to "stop",
            "making" to "make",
            "moved" to "move",
            "walked" to "walk",
            "cities" to "city",
            "carried" to "carry",
            "ties" to "tie",
            "lying" to "lie",
            "boxes" to "box",
            "horses" to "horse",
            "wolves" to "wolf",
            "knives" to "knife",
            "bigger" to "big",
            "later" to "late",
            "happiest" to "happy",
            "quickly" to "quick",
            "alarms" to "alarm",
        ).forEach { (form, base) ->
            val guesses = englishBaseForms(form)
            assertTrue("$form: $guesses", base in guesses)
            assertTrue("$form: $guesses", form !in guesses)
        }
    }
}
