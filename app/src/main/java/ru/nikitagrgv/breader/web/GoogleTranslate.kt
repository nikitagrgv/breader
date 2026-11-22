package ru.nikitagrgv.breader.web

import org.json.JSONObject
import ru.nikitagrgv.breader.dictionary.DictionaryEntry
import ru.nikitagrgv.breader.dictionary.DictionarySense
import ru.nikitagrgv.breader.dictionary.dictionaryKey
import ru.nikitagrgv.breader.dictionary.objects
import ru.nikitagrgv.breader.dictionary.string
import ru.nikitagrgv.breader.dictionary.strings
import ru.nikitagrgv.breader.translation.MachineTranslation

/**
 * Google Translate through the endpoint of its browser widgets: free and with no key, but not an official API, so Google
 * may limit it or change it. Besides translating, it has a dictionary of single words.
 */
object GoogleTranslate {
    /** @param from the code of the language, null to detect it. */
    suspend fun translate(text: String, from: String?, to: String): MachineTranslation =
        parseTranslation(request(text, from, to, "t"))

    /** Translations of a word by part of speech, the common ones; for a phrase that has none, its translation. */
    suspend fun lookUp(text: String, from: String?, to: String): List<DictionaryEntry> =
        parseDictionary(request(text, from, to, "t", "bd"), text)

    /** @param data what's asked for: "t" is the translation, "bd" the dictionary. */
    private suspend fun request(text: String, from: String?, to: String, vararg data: String): String {
        val url = buildString {
            // dj=1 makes the reply an object with named fields rather than nested arrays.
            append("https://translate.googleapis.com/translate_a/single?client=gtx&dj=1")
            append("&sl=").append(from ?: "auto")
            append("&tl=").append(to)
            // The names of the parts of speech are in this language.
            append("&hl=").append(to)
            for (it in data) append("&dt=").append(it)
            append("&q=").append(text.urlEncoded())
        }
        val reply = httpGet(url)
        // Too many requests get a "sorry" page, with 429.
        if (!reply.isSuccess) throw WebServiceException(reply.code, "Google Translate: HTTP ${reply.code}")
        return reply.body
    }

    internal fun parseTranslation(json: String): MachineTranslation {
        val root = JSONObject(json)
        // Each sentence is translated on its own; the spaces and line ends between them are in the translations.
        val text = root.optJSONArray("sentences").objects().joinToString("") { (it.opt("trans") as? String).orEmpty() }
        return MachineTranslation(text.trim(), root.string("src").ifEmpty { null })
    }

    internal fun parseDictionary(json: String, text: String): List<DictionaryEntry> {
        val root = JSONObject(json)
        val entries = root.optJSONArray("dict").objects().mapNotNull { part ->
            val terms = part.optJSONArray("entry").objects()
                .mapNotNull { entry ->
                    val word = entry.string("word").ifEmpty { return@mapNotNull null }
                    Term(word, entry.optDouble("score", 0.0), entry.optJSONArray("reverse_translation").strings())
                }
                .ifEmpty { part.optJSONArray("terms").strings().map { Term(it, 0.0, emptyList()) } }
            // A common word has dozens of translations, most of them for rare meanings: "линять" for "run". They're
            // ranked by how often they're used, and the scores fall steeply after the common ones.
            val top = terms.maxOfOrNull { it.score } ?: 0.0
            val common = terms.filter { it.score >= top / COMMON_SHARE }.take(MAX_TERMS)
            if (common.isEmpty()) return@mapNotNull null
            DictionaryEntry(
                word = part.string("base_form").ifEmpty { text.trim() },
                pos = part.string("pos"),
                forms = emptyList(),
                senses = common.map { DictionarySense(it.word, backTranslations = it.back) },
            )
        }
        if (entries.isNotEmpty()) return entries
        // Phrases and names have no entries, but their translation is as good as a meaning.
        val translation = parseTranslation(json).text
        if (translation.isEmpty() || dictionaryKey(translation) == dictionaryKey(text)) return emptyList()
        return listOf(DictionaryEntry(text.trim(), pos = "", forms = emptyList(), senses = listOf(DictionarySense(translation))))
    }

    private class Term(val word: String, val score: Double, val back: List<String>)

    /** A translation is common if it's used at least this many times less than the most common one. */
    private const val COMMON_SHARE = 200.0
    private const val MAX_TERMS = 8
}
