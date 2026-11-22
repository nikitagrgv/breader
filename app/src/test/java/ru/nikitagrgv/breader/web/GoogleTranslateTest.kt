package ru.nikitagrgv.breader.web

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nikitagrgv.breader.dictionary.DictionaryEntry
import ru.nikitagrgv.breader.dictionary.DictionarySense
import ru.nikitagrgv.breader.translation.MachineTranslation

/** The replies are shortened from real ones. */
class GoogleTranslateTest {
    @Test
    fun `dictionary keeps the common translations of each part of speech`() {
        val json = """{"sentences":[{"trans":"бегать","orig":"run","backend":10}],"dict":[""" +
            """{"pos":"глагол","terms":["работать","бежать","управлять","бегать","идти","проходить","убегать","линять"],"entry":[""" +
            """{"word":"работать","score":0.0505711},{"word":"бежать","score":0.028814545},{"word":"управлять","score":0.01720595},""" +
            """{"word":"бегать","score":0.0030370306},{"word":"идти","score":4.3752574E-4},{"word":"проходить","score":2.8248786E-4},""" +
            """{"word":"убегать","score":2.3419065E-4},{"word":"линять","score":2.753645E-5}],"base_form":"run","pos_enum":2},""" +
            """{"pos":"имя существительное","terms":["пробег","бег"],"entry":[{"word":"пробег","score":0.030672923},""" +
            """{"word":"бег","score":0.020754337,"reverse_translation":["run","running","race"]}],"base_form":"run","pos_enum":1}""" +
            """],"src":"en","spell":{}}"""
        assertEquals(
            listOf(
                DictionaryEntry(
                    word = "run",
                    pos = "глагол",
                    forms = emptyList(),
                    senses = listOf("работать", "бежать", "управлять", "бегать", "идти", "проходить").map { DictionarySense(it) },
                ),
                DictionaryEntry(
                    word = "run",
                    pos = "имя существительное",
                    forms = emptyList(),
                    senses = listOf(DictionarySense("пробег"), DictionarySense("бег", backTranslations = listOf("run", "running", "race"))),
                ),
            ),
            GoogleTranslate.parseDictionary(json, "Run"),
        )
    }

    @Test
    fun `phrase without entries gets its translation as the meaning`() {
        val json = """{"sentences":[{"trans":"закончился","orig":"ran out","backend":10}],"src":"en"}"""
        assertEquals(
            listOf(DictionaryEntry("ran out", "", emptyList(), listOf(DictionarySense("закончился")))),
            GoogleTranslate.parseDictionary(json, "ran out"),
        )
    }

    @Test
    fun `text in the target language has no meaning`() {
        val json = """{"sentences":[{"trans":"Дом","orig":"дом","backend":10}],"src":"ru"}"""
        assertEquals(emptyList<DictionaryEntry>(), GoogleTranslate.parseDictionary(json, "дом"))
    }

    @Test
    fun `sentences are joined into the translation`() {
        val json = """{"sentences":[{"trans":"Он выбежал из дома. ","orig":"He ran out of the house. "},""" +
            """{"trans":"Было холодно.","orig":"It was cold."},{"src_translit":"x"}],"src":"en"}"""
        assertEquals(MachineTranslation("Он выбежал из дома. Было холодно.", "en"), GoogleTranslate.parseTranslation(json))
    }
}
