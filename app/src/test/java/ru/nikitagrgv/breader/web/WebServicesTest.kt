package ru.nikitagrgv.breader.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.nikitagrgv.breader.dictionary.DictionaryEntry
import ru.nikitagrgv.breader.dictionary.DictionaryExample
import ru.nikitagrgv.breader.dictionary.DictionarySense
import ru.nikitagrgv.breader.translation.MachineTranslation

class WebServicesTest {
    /** As in the API's documentation. */
    @Test
    fun `yandex entry has translations with synonyms, meanings and examples`() {
        val json = """{"head":{},"def":[{"text":"time","pos":"noun","ts":"taɪm","tr":[""" +
            """{"text":"время","pos":"noun","syn":[{"text":"раз","pos":"noun"},{"text":"тайм","pos":"noun"}],""" +
            """"mean":[{"text":"timing"},{"text":"fold"}],"ex":[{"text":"prehistoric time","tr":[{"text":"доисторическое время"}]}]},""" +
            """{"text":"","pos":"noun"}]},{"text":"time","pos":"verb","tr":[]}]}"""
        assertEquals(
            listOf(
                DictionaryEntry(
                    word = "time",
                    pos = "noun",
                    forms = emptyList(),
                    senses = listOf(
                        DictionarySense(
                            gloss = "время, раз, тайм",
                            examples = listOf(DictionaryExample("prehistoric time", "доисторическое время")),
                            backTranslations = listOf("timing", "fold"),
                        ),
                    ),
                    transcription = "taɪm",
                ),
            ),
            YandexDictionary.parse(json),
        )
    }

    /** Shortened from a real reply. */
    @Test
    fun `mymemory translation comes with the detected language`() {
        val json = """{"responseData":{"translatedText":"запустить it&#39;s","match":0.85,""" +
            """"detectedLanguage":"en"},"quotaFinished":false,"mtLangSupported":null,"responseDetails":"","responseStatus":200}"""
        assertEquals(MachineTranslation("запустить it's", "en"), MyMemory.parse(json))
    }

    @Test
    fun `mymemory errors come in the body`() {
        val quota = """{"responseData":{"translatedText":"MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY."},""" +
            """"quotaFinished":true,"responseDetails":"","responseStatus":429}"""
        assertEquals(429, assertThrows(WebServiceException::class.java) { MyMemory.parse(quota) }.code)
        val invalid = """{"responseData":{"translatedText":"'XX' IS AN INVALID TARGET LANGUAGE"},"quotaFinished":false,""" +
            """"responseDetails":"'XX' IS AN INVALID TARGET LANGUAGE","responseStatus":"403"}"""
        val error = assertThrows(WebServiceException::class.java) { MyMemory.parse(invalid) }
        assertEquals(403, error.code)
        assertEquals("'XX' IS AN INVALID TARGET LANGUAGE", error.message)
    }
}
