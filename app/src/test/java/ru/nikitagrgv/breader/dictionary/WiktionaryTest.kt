package ru.nikitagrgv.breader.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lines are shortened from real kaikki.org data. */
class WiktionaryTest {
    @Test
    fun `entry of the Russian edition is read, with endings dropped from the forms`() {
        val line = """{"word": "alarm", "pos": "verb", "lang_code": "en", "lang": "Английский", "forms": [{"form": "alarms", "tags": ["third-person", "singular"]}, {"form": "ed", "tags": ["past"]}, {"form": "ing", "tags": ["gerund"]}], "categories": [{"name": "Английские глаголы"}], "senses": [{"glosses": ["поднимать тревогу"], "id": "x"}, {"glosses": ["встревоживать, волновать"], "examples": [{"text": "Drug abuse.", "bold_text_offsets": [[5, 10]], "translation": "Злоупотребление наркотиками."}]}]}"""
        val parsed = requireNotNull(Wiktionary.parse(line))
        assertEquals("Английский", parsed.language)
        assertEquals("en", parsed.languageCode)
        assertEquals(
            DictionaryEntry(
                word = "alarm",
                pos = "verb",
                forms = listOf("alarms"),
                senses = listOf(
                    DictionarySense("поднимать тревогу"),
                    DictionarySense("встревоживать, волновать", examples = listOf(DictionaryExample("Drug abuse.", "Злоупотребление наркотиками."))),
                ),
            ),
            parsed.entry,
        )
        assertEquals(emptyList<String>(), parsed.formOf)
    }

    @Test
    fun `inflected form gives its base word instead of senses`() {
        val line = """{"word": "ran", "pos": "verb", "lang_code": "en", "lang": "Английский", "senses": [{"glosses": ["форма прошедшего времени глагола run"], "tags": ["form-of"], "form_of": [{"word": "run"}]}]}"""
        val parsed = requireNotNull(Wiktionary.parse(line))
        assertEquals(emptyList<DictionarySense>(), parsed.entry.senses)
        assertEquals(listOf("run"), parsed.formOf)
    }

    @Test
    fun `senses without glosses are dropped`() {
        val line = """{"word": "went", "pos": "verb", "lang_code": "en", "lang": "Английский", "senses": [{"tags": ["no-gloss"]}]}"""
        assertEquals(emptyList<DictionarySense>(), requireNotNull(Wiktionary.parse(line)).entry.senses)
    }

    @Test
    fun `entry of the English edition keeps real forms, specific glosses and short examples`() {
        val line = """{"word": "raven", "pos": "verb", "lang": "German", "lang_code": "de", "forms": [{"form": "strong", "source": "declension", "tags": ["table-tags"]}, {"form": "ravt", "tags": ["present", "singular", "third-person"]}, {"form": "ravte", "tags": ["past"]}, {"form": "haben", "tags": ["auxiliary"]}], "senses": [{"glosses": ["to party", "to rave (to attend a rave)"], "tags": ["colloquial", "weak"], "examples": [{"text": "Es wird geravt.", "english": "There's raving.", "type": "example"}, {"text": "Long quotation", "ref": "2018, taz", "type": "quotation"}]}]}"""
        val entry = requireNotNull(Wiktionary.parse(line)).entry
        assertEquals(listOf("ravt", "ravte"), entry.forms)
        assertEquals(
            listOf(DictionarySense("to rave (to attend a rave)", listOf("colloquial", "weak"), listOf(DictionaryExample("Es wird geravt.", "There's raving.")))),
            entry.senses,
        )
    }

    @Test
    fun `broken lines are skipped`() {
        assertNull(Wiktionary.parse("{\"word\": "))
        assertNull(Wiktionary.parse("""{"pos": "noun"}"""))
    }

    @Test
    fun `senses survive storing`() {
        val senses = listOf(
            DictionarySense("бег"),
            DictionarySense("лучший", listOf("comparative"), listOf(DictionaryExample("Better late.", "Лучше поздно."), DictionaryExample("Only text"))),
        )
        assertEquals(senses, SensesJson.read(SensesJson.write(senses)))
    }

    @Test
    fun `languages are read from an edition index`() {
        val html = """
            <li><a href="All%20languages%20combined/index.html">All languages combined (1998828 senses)</a></li>
            <li><a href="%D0%A0%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9/index.html">Русский (586623 senses)</a></li>
            <li><a href="Old%20English/index.html">Old English &amp; Co (7037 senses)</a></li>
            <li><a href="https://kaikki.org/index.html">Home</a></li>
        """
        assertEquals(
            listOf(
                CatalogLanguage("Русский", "%D0%A0%D1%83%D1%81%D1%81%D0%BA%D0%B8%D0%B9", 586623),
                CatalogLanguage("Old English & Co", "Old%20English", 7037),
            ),
            Kaikki.parseLanguages(html),
        )
    }

    @Test
    fun `download link is found on a language page`() {
        val html = """<p>Download <a href="kaikki.org-dictionary-English.jsonl" download target="_blank">Download</a> postprocessed</p>"""
        assertEquals("kaikki.org-dictionary-English.jsonl", Kaikki.parseDownloadLink(html))
        assertNull(Kaikki.parseDownloadLink("<p>Nothing here</p>"))
        assertTrue(Kaikki.wholeEditionUrl(Kaikki.editions.first { it.code == "ru" }).endsWith("/ruwiktionary/raw-wiktextract-data.jsonl.gz"))
    }
}
