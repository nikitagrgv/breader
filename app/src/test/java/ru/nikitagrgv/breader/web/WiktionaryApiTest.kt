package ru.nikitagrgv.breader.web

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nikitagrgv.breader.dictionary.DictionaryEntry
import ru.nikitagrgv.breader.dictionary.DictionaryExample
import ru.nikitagrgv.breader.dictionary.DictionarySense

/** The replies are shortened from real ones of the Wikimedia REST API. */
class WiktionaryApiTest {
    private val ran = """{"en":[""" +
        """{"partOfSpeech":"Symbol","language":"Translingual","definitions":[{"definition":"<i><a rel=\"mw:WikiLink\" href=\"/wiki/language_code\" title=\"language code\">language code</a> for </i><b>Riantana</b>."}]},""" +
        """{"partOfSpeech":"Verb","language":"English","definitions":[""" +
        """{"definition":"<span class=\"form-of-definition use-with-mention\">simple <a rel=\"mw:WikiLink\" href=\"/wiki/Appendix:Glossary#past_tense\" title=\"Appendix:Glossary\">past</a> of <span class=\"form-of-definition-link\"><i class=\"Latn mention\" lang=\"en\"><a rel=\"mw:WikiLink\" href=\"/wiki/run#English\" title=\"run\">run</a></i></span></span>"},""" +
        """{"definition":"<span class=\"form-of-definition use-with-mention\">simple past of <span class=\"form-of-definition-link\"><a rel=\"mw:WikiLink\" href=\"/wiki/rin#English\" title=\"rin\">rin</a></span></span>"}]}""" +
        """]}"""

    @Test
    fun `inflected form gives its senses and its base words`() {
        val page = WiktionaryApi.parse(ran, "ran", language = null)
        assertEquals(
            listOf(DictionaryEntry("ran", "verb", emptyList(), listOf(DictionarySense("simple past of run"), DictionarySense("simple past of rin")))),
            page.entries,
        )
        assertEquals(listOf("run", "rin"), page.formOf)
    }

    @Test
    fun `nested senses and tags are left out, examples are kept`() {
        val json = """{"en":[{"partOfSpeech":"Verb","language":"English","definitions":[""" +
            """{"definition":"To move <a rel=\"mw:WikiLink\" href=\"/wiki/swiftly\" title=\"swiftly\">swiftly</a>.\n<ol><li>To go at a pace faster than a walk.</li></ol>",""" +
            """"examples":["<b>Run</b>, and you might still catch the train<span typeof=\"mw:Entity\">!</span>"],""" +
            """"parsedExamples":[{"example":"<b>Run</b>, and you might still catch the train<span typeof=\"mw:Entity\">!</span>"}]},""" +
            """{"definition":"<span class=\"usage-label-sense\"></span> "}]}]}"""
        assertEquals(
            listOf(
                DictionaryEntry(
                    "run",
                    "verb",
                    emptyList(),
                    listOf(DictionarySense("To move swiftly.", examples = listOf(DictionaryExample("Run, and you might still catch the train!")))),
                ),
            ),
            WiktionaryApi.parse(json, "run", language = null).entries,
        )
    }

    @Test
    fun `only the given language is taken`() {
        val json = """{"en":[{"partOfSpeech":"Noun","language":"English","definitions":[{"definition":"A house."}]}],""" +
            """"de":[{"partOfSpeech":"Noun","language":"German","definitions":[{"definition":"<a title=\"cathedral\">cathedral</a>",""" +
            """"parsedExamples":[{"example":"Der <b>Dom</b> ist alt.","translation":"The cathedral is old."}]}]}],""" +
            """"other":[{"partOfSpeech":"Noun","language":"Czech","definitions":[{"definition":"house"}]}]}"""
        assertEquals(
            listOf(
                DictionaryEntry(
                    "Dom",
                    "noun",
                    emptyList(),
                    listOf(DictionarySense("cathedral", examples = listOf(DictionaryExample("Der Dom ist alt.", "The cathedral is old.")))),
                ),
            ),
            WiktionaryApi.parse(json, "Dom", language = "de").entries,
        )
    }

    @Test
    fun `without a language, the first one of the page is taken, whatever the order of the keys`() {
        val json = """{"other":[{"partOfSpeech":"Adverb","language":"Chuukese","definitions":[{"definition":"near"}]}],""" +
            """"nl":[{"partOfSpeech":"Verb","language":"Dutch","definitions":[{"definition":"to run"}]}],""" +
            """"en":[{"partOfSpeech":"Symbol","language":"Translingual","definitions":[{"definition":"code"}]},""" +
            """{"partOfSpeech":"Verb","language":"English","definitions":[{"definition":"To move swiftly."}]}]}"""
        assertEquals(listOf("To move swiftly."), WiktionaryApi.parse(json, "run", language = null).entries.flatMap { it.senses }.map { it.gloss })
    }

    @Test
    fun `only inflections lead to base words`() {
        val json = """{"en":[{"partOfSpeech":"Verb","language":"English","definitions":[""" +
            """{"definition":"To move swiftly."},""" +
            """{"definition":"<span class=\"form-of-definition\">ellipsis of <span class=\"form-of-definition-link\"><a title=\"have the run of\">have the run of</a></span></span>"},""" +
            """{"definition":"<span class=\"form-of-definition\">past participle of <span class=\"form-of-definition-link\"><a title=\"rin\">rin</a></span></span>"}]}]}"""
        assertEquals(listOf("rin"), WiktionaryApi.parse(json, "run", language = "en").formOf)
    }

    @Test
    fun `html is turned into text`() {
        assertEquals("a < b & “c” d", htmlToText("<i>a</i> &lt; b &amp; &#8220;c&#x201D;\n  <br/>d"))
        assertEquals("&unknown; stays", htmlToText("&unknown; stays"))
    }
}
