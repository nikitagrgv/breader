package ru.nikitagrgv.breader.web

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import ru.nikitagrgv.breader.dictionary.DictionaryEntry
import ru.nikitagrgv.breader.dictionary.DictionaryExample
import ru.nikitagrgv.breader.dictionary.DictionarySense
import ru.nikitagrgv.breader.dictionary.objects
import ru.nikitagrgv.breader.dictionary.string
import ru.nikitagrgv.breader.dictionary.strings
import ru.nikitagrgv.breader.translation.languageName

/**
 * English Wiktionary online, through the Wikimedia REST API: the largest Wiktionary, with the words of every language
 * explained in English. Unlike the downloaded dictionaries, it has all of it, and it's up to date.
 */
object WiktionaryApi {
    /**
     * The entries of [word], only of the language with the code [language] if it's given. An inflected form brings the
     * entries of its base words too, as "ran" brings "run".
     */
    suspend fun lookUp(word: String, language: String?): List<DictionaryEntry> = coroutineScope {
        // Page titles are case-sensitive, and a word at the start of a sentence is capitalized.
        val page = listOf(word, word.lowercase()).distinct().firstNotNullOfOrNull { title ->
            fetch(title)?.let { parse(it, title, language) }?.takeIf { it.entries.isNotEmpty() }
        } ?: return@coroutineScope emptyList()
        val bases = page.formOf.filter { it != page.title }.take(MAX_BASE_WORDS)
        val baseEntries = bases.map { base -> async { fetch(base)?.let { parse(it, base, language).entries }.orEmpty() } }.awaitAll()
        page.entries + baseEntries.flatten()
    }

    /** Null if there's no such page. */
    private suspend fun fetch(title: String): String? {
        val path = title.replace(' ', '_').urlEncoded()
        val reply = httpGet(
            "https://en.wiktionary.org/api/rest_v1/page/definition/$path?redirect=true",
            // Wikimedia asks clients to name themselves.
            mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json"),
        )
        if (reply.code == 404) return null
        if (!reply.isSuccess) throw WebServiceException(reply.code, "Wiktionary: HTTP ${reply.code}")
        return reply.body
    }

    /** @param formOf the base words of the senses that say the word is an inflected form of them, "run" for "ran". */
    internal class Page(val title: String, val entries: List<DictionaryEntry>, val formOf: List<String>)

    /**
     * The reply groups the parts of speech under language codes, which aren't reliable: "Translingual" comes under "en",
     * and the languages with few speakers under "other". Each part names its language in English though.
     * @param language the code of the language to take; without it, the first one of the page is taken, as the page
     *   has dozens of languages for a short word, mostly ones with few speakers. English comes first, after Translingual,
     *   which only has symbols and codes.
     */
    internal fun parse(json: String, title: String, language: String?): Page {
        val root = JSONObject(json)
        // In the order of the page, which the order of the keys may not keep.
        val usages = root.keys().asSequence()
            .sortedBy { code -> if (code == "en") 0 else if (code == "other") 2 else 1 }
            .flatMap { root.optJSONArray(it).objects() }
            .toList()
        val wanted = language?.let(::languageName)
            ?: usages.map { it.string("language") }.firstOrNull { it != TRANSLINGUAL }
            ?: return Page(title, emptyList(), emptyList())
        val entries = ArrayList<DictionaryEntry>()
        val formOf = LinkedHashSet<String>()
        for (usage in usages.filter { it.string("language").equals(wanted, ignoreCase = true) }) {
            val senses = usage.optJSONArray("definitions").objects().mapNotNull { definition ->
                // The narrower senses follow in a nested list, which would run into one with the sense.
                val html = definition.string("definition").substringBefore("<ol").substringBefore("<ul")
                val gloss = htmlToText(html).ifEmpty { return@mapNotNull null }
                // "simple past of run", unlike "ellipsis of have the run of" or "alternative spelling of".
                if (INFLECTION.containsMatchIn(gloss)) FORM_OF.find(html)?.let { formOf += decodeEntities(it.groupValues[1]) }
                DictionarySense(gloss, examples = examples(definition))
            }
            if (senses.isNotEmpty()) entries += DictionaryEntry(title, usage.string("partOfSpeech").lowercase(), emptyList(), senses)
        }
        return Page(title, entries, formOf.toList())
    }

    private fun examples(definition: JSONObject): List<DictionaryExample> {
        val parsed = definition.optJSONArray("parsedExamples").objects().map {
            DictionaryExample(htmlToText(it.string("example")), htmlToText(it.string("translation")))
        }
        val examples = parsed.ifEmpty { definition.optJSONArray("examples").strings().map { DictionaryExample(htmlToText(it)) } }
        // Long quotations from books say little more than the short examples, as with the downloaded dictionaries.
        return examples.filter { it.text.isNotEmpty() && it.text.length <= MAX_EXAMPLE_LENGTH }.distinctBy { it.text }.take(MAX_EXAMPLES)
    }

    private const val USER_AGENT = "Breader/1.0 (Android e-book reader)"
    private const val TRANSLINGUAL = "Translingual"
    private const val MAX_BASE_WORDS = 2
    private const val MAX_EXAMPLES = 2
    private const val MAX_EXAMPLE_LENGTH = 200

    private val INFLECTION = Regex("""\b(past|participle|plural|singular|comparative|superlative|tense|gerund|inflection|person)\b""", RegexOption.IGNORE_CASE)

    /** The link to the base word in "simple past of run". */
    private val FORM_OF = Regex("""form-of-definition-link.*?<a\b[^>]*?\btitle="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
}
