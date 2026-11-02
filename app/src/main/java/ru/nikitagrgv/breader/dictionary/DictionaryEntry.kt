package ru.nikitagrgv.breader.dictionary

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A word in a dictionary, as one part of speech: Wiktionary has separate entries for "run" the verb and the noun. */
data class DictionaryEntry(
    val word: String,
    /** As Wiktionary names it, e.g. "noun". */
    val pos: String,
    /** Inflected forms, e.g. "runs" and "ran" of "run". */
    val forms: List<String>,
    val senses: List<DictionarySense>,
)

data class DictionarySense(
    /** The meaning, in the language of the dictionary's Wiktionary edition. */
    val gloss: String,
    /** Like "colloquial" or "transitive". */
    val tags: List<String> = emptyList(),
    val examples: List<DictionaryExample> = emptyList(),
)

/** @param translation into the language of the dictionary's Wiktionary edition; empty if there's none. */
data class DictionaryExample(val text: String, val translation: String = "")

/**
 * An entry of a kaikki.org JSONL file, see https://github.com/tatuylonen/wiktextract.
 * @param language the name of the entry's language in the edition's language, e.g. "Английский".
 * @param formOf words the entry is an inflected form of; their senses are left out of [entry].
 */
class WiktionaryLine(val language: String, val languageCode: String, val entry: DictionaryEntry, val formOf: List<String>)

object Wiktionary {
    /** Null if [line] isn't an entry. The [WiktionaryLine.entry] may have no senses: for inflected forms, it's only [WiktionaryLine.formOf]. */
    fun parse(line: String): WiktionaryLine? {
        val json = try {
            JSONObject(line)
        } catch (_: JSONException) {
            return null
        }
        val word = json.string("word").ifEmpty { return null }
        val formOf = ArrayList<String>()
        val senses = json.optJSONArray("senses").objects().mapNotNull { sense ->
            val targets = sense.optJSONArray("form_of").objects().map { it.string("word") }.filter { it.isNotEmpty() }
            if (targets.isNotEmpty()) {
                // "past of run" says nothing that the senses of "run" don't.
                formOf += targets
                return@mapNotNull null
            }
            // Nested senses list the glosses of their parents first.
            val gloss = sense.optJSONArray("glosses").strings().lastOrNull()?.trim().orEmpty()
            if (gloss.isEmpty()) return@mapNotNull null
            DictionarySense(
                gloss = gloss,
                tags = sense.optJSONArray("tags").strings().filter { it !in HIDDEN_SENSE_TAGS && !it.startsWith("error") },
                examples = sense.optJSONArray("examples").objects().mapNotNull(::example).take(MAX_EXAMPLES),
            )
        }
        val forms = json.optJSONArray("forms").objects()
            .filter { form -> form.optJSONArray("tags").strings().none { it in HIDDEN_FORM_TAGS } }
            .map { it.string("form") }
            .filter { it.isNotEmpty() && it != word && !isEndingOnly(it, word) }
            .distinct()
        return WiktionaryLine(
            language = json.string("lang"),
            languageCode = json.string("lang_code"),
            entry = DictionaryEntry(word, json.string("pos"), forms, senses),
            formOf = formOf.distinct(),
        )
    }

    /** Long quotations from books are left out: they take much room and say little more than the examples. */
    private fun example(json: JSONObject): DictionaryExample? {
        val text = json.string("text")
        if (text.isEmpty() || json.string("type") == "quotation" || text.length > MAX_EXAMPLE_LENGTH) return null
        return DictionaryExample(text, json.string("translation").ifEmpty { json.string("english") })
    }

    /** Some entries list endings instead of forms, like "ed" and "ing" of "alarm". */
    private fun isEndingOnly(form: String, word: String): Boolean =
        form.length <= 3 && word.length > form.length + 1 && !word.startsWith(form)

    /** Not forms of the word: names of inflection tables, auxiliary verbs, transliterations... */
    private val HIDDEN_FORM_TAGS = setOf("table-tags", "inflection-template", "auxiliary", "class", "romanization", "canonical")
    private val HIDDEN_SENSE_TAGS = setOf("form-of", "alt-of", "no-gloss")
    private const val MAX_EXAMPLES = 2
    private const val MAX_EXAMPLE_LENGTH = 200
}

/** How senses are stored in a dictionary database: compact JSON. */
internal object SensesJson {
    fun write(senses: List<DictionarySense>): String = JSONArray().apply {
        for (sense in senses) {
            val json = JSONObject().put("g", sense.gloss)
            if (sense.tags.isNotEmpty()) json.put("t", JSONArray(sense.tags))
            if (sense.examples.isNotEmpty()) {
                json.put("e", JSONArray().apply { sense.examples.forEach { put(JSONArray().put(it.text).put(it.translation)) } })
            }
            put(json)
        }
    }.toString()

    fun read(text: String): List<DictionarySense> = JSONArray(text).objects().map { json ->
        DictionarySense(
            gloss = json.getString("g"),
            tags = json.optJSONArray("t").strings(),
            examples = json.optJSONArray("e").arrays().map { DictionaryExample(it.getString(0), it.optString(1)) },
        )
    }
}

internal fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

internal fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { opt(it) as? String }

private fun JSONArray?.arrays(): List<JSONArray> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONArray(it) }

/** Unlike [JSONObject.optString], doesn't turn null into "null". */
private fun JSONObject.string(name: String): String = (opt(name) as? String)?.trim().orEmpty()
