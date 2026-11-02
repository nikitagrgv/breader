package ru.nikitagrgv.breader.translation

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A grammar construction of the selected fragment. */
data class GrammarPoint(
    /** The words of the fragment it's about. */
    val words: String,
    /** The construction's name and how it's formed. */
    val rule: String,
    /** Why it's used here and what it means. */
    val explanation: String,
)

/**
 * What a model replied about the grammar of the selected fragment.
 * @param text the whole reply, for models that ignore the format; then there are no [points].
 */
data class GrammarExplanation(
    val translation: String = "",
    val points: List<GrammarPoint> = emptyList(),
    val text: String = "",
)

/**
 * Asks for the grammar of the selected fragment, explained in context, like [TranslationPrompt] does for
 * its meaning. The prompt was picked by trying several on the free models: free text came out long and in
 * markdown, and without the hints models took "would" for a past habit where it was the future in the past,
 * and described English in terms of Russian grammar, like noun cases. The "clause" isn't shown: quoting it
 * first makes models look at the context, and they stopped taking "forced" of "had forced" for past simple.
 */
object GrammarPrompt {
    fun system(targetLanguage: String): String = """
        You are a grammar tutor built into an e-book reader. The reader is learning the language of the book, selected a fragment and wants to understand its grammar.

        You get an excerpt where the selected fragment is wrapped in <mark></mark> tags. Explain the grammar of ONLY the marked fragment; the surrounding text shows the context, which tells what the constructions mean here.

        Pick the constructions a learner needs to understand the fragment, the most important first, usually 1-4 of them: tenses and aspect, voice, mood and conditionals, modal verbs, infinitives, gerunds and participles, phrasal verbs, the structure of the clauses, word order, set phrases. Many forms have several uses (e.g. "would": future in the past, past habit or conditional; "-ing": gerund or participle), so decide from the context which one it is here. Describe the grammar of the book's language in its own terms, without categories it doesn't have (e.g. no noun cases in English). Skip the obvious, like a plain article, and don't explain plain vocabulary. When the fragment is a part of a larger construction (e.g. a participle after "had", or the particle of a phrasal verb), explain that whole construction, taking its other words from the context. For a single word, also explain its form and its role in the sentence.

        Reply with one JSON object and nothing else (no markdown), with all texts in $targetLanguage; use English only for the quoted words and the usual English names of constructions:
        {"clause": "<the whole clause of the excerpt that the fragment is in, quoted as written>", "translation": "<the marked fragment translated into $targetLanguage>", "points": [{"words": "<the words of the fragment it's about, exactly as written>", "rule": "<the construction's name in $targetLanguage, with its usual English name in parentheses if it has one, and how it's formed>", "explanation": "<why it's used here and what it adds to the meaning, 1-2 sentences>"}]}
    """.trimIndent()

    /** @param sourceLanguage the language of the book; blank to let the model detect it. */
    fun user(excerpt: Excerpt, sourceLanguage: String, targetLanguage: String): String {
        val text = "${excerpt.before}<mark>${excerpt.fragment}</mark>${excerpt.after}"
        val fragment = if (excerpt.fragment.length <= MAX_QUOTED_LENGTH) " \"${excerpt.fragment}\"" else ""
        val from = if (sourceLanguage.isBlank()) "" else " (${sourceLanguage.trim()})"
        return "Excerpt$from:\n\"\"\"\n$text\n\"\"\"\n\nExplain the grammar of the marked fragment$fragment in $targetLanguage."
    }

    /** Reads a model's reply; null if there's nothing in it. Models rename the fields at times, so some other names are accepted. */
    fun parse(reply: String): GrammarExplanation? {
        val json = findJson(reply) ?: return reply.trim().takeIf { it.isNotEmpty() }?.let { GrammarExplanation(text = it) }
        val points = (json.opt("points") as? JSONArray)?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }.mapNotNull { point ->
                val explanation = point.string("explanation", "use", "description")
                val rule = point.string("rule", "construction", "title", "name")
                if (explanation.isEmpty() && rule.isEmpty()) null else GrammarPoint(point.string("words", "fragment"), rule, explanation)
            }
        }.orEmpty()
        val translation = json.string("translation")
        if (points.isEmpty() && translation.isEmpty()) return reply.trim().takeIf { it.isNotEmpty() }?.let { GrammarExplanation(text = it) }
        return GrammarExplanation(translation, points)
    }

    private fun findJson(reply: String): JSONObject? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end < start) return null
        return try {
            JSONObject(reply.substring(start, end + 1))
        } catch (_: JSONException) {
            null
        }
    }

    /** The first of [names] that has a text. */
    private fun JSONObject.string(vararg names: String): String =
        names.firstNotNullOfOrNull { name -> (opt(name) as? String)?.trim()?.takeIf { it.isNotEmpty() } }.orEmpty()

    private const val MAX_QUOTED_LENGTH = 200
}
