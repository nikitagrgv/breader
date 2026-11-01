package ru.nikitagrgv.breader.translation

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What a model replied about the selected fragment. */
data class Translation(
    val text: String,
    /** Other possible translations, never the same as [text]. */
    val alternatives: List<String> = emptyList(),
    /** An explanation of an idiom or a non-obvious meaning; empty if there's nothing to explain. */
    val note: String = "",
)

/**
 * The model gets an excerpt of the book with the selected fragment marked, so it sees the context
 * but translates only the fragment.
 */
object TranslationPrompt {
    fun system(targetLanguage: String): String = """
        You are a translator built into an e-book reader. The reader selected a fragment of the book and wants to know what it means.

        You get an excerpt of the book where the selected fragment is wrapped in <mark></mark> tags. Translate ONLY the marked fragment into $targetLanguage: the surrounding text is there only to show which meaning fits. Never translate the rest of the excerpt.

        Reply with one JSON object and nothing else (no markdown, no comments):
        {"translation": "<the marked fragment translated into $targetLanguage, fitting this context>", "alternatives": ["<other possible translations of the fragment, if any>"], "note": "<a brief note in $targetLanguage if the fragment is an idiom, slang, a pun or has a non-obvious meaning here; otherwise an empty string>"}
    """.trimIndent()

    /** @param sourceLanguage the language of the book; blank to let the model detect it. */
    fun user(excerpt: Excerpt, sourceLanguage: String, targetLanguage: String): String {
        val text = "${excerpt.before}<mark>${excerpt.fragment}</mark>${excerpt.after}"
        // Quoting the fragment once more helps small models, but isn't worth it for long ones.
        val fragment = if (excerpt.fragment.length <= MAX_QUOTED_LENGTH) " \"${excerpt.fragment}\"" else ""
        val from = if (sourceLanguage.isBlank()) "" else " from ${sourceLanguage.trim()}"
        return "Excerpt:\n\"\"\"\n$text\n\"\"\"\n\nTranslate the marked fragment$fragment$from into $targetLanguage."
    }

    /** Reads a model's reply; null if there's no translation in it. */
    fun parse(reply: String): Translation? {
        val json = findJson(reply)
            // Some models ignore the format, but their plain text reply is still a translation.
            ?: return reply.trim().takeIf { it.isNotEmpty() && '{' !in it }?.let(::Translation)
        val text = json.string("translation").ifEmpty { return null }
        val value: Any? = json.opt("alternatives")
        val alternatives = when (value) {
            is JSONArray -> (0 until value.length()).mapNotNull { (value.opt(it) as? String)?.trim() }
            is String -> listOf(value.trim())
            else -> emptyList()
        }
        return Translation(
            text = text,
            alternatives = alternatives
                .filter { it.isNotEmpty() && it.translationKey() != text.translationKey() }
                .distinctBy { it.translationKey() },
            note = json.string("note"),
        )
    }

    /** The reply may be wrapped in a markdown code block or have some text around it. */
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

    /** Unlike [JSONObject.optString], doesn't turn null into "null". */
    private fun JSONObject.string(name: String): String = (opt(name) as? String)?.trim().orEmpty()

    private const val MAX_QUOTED_LENGTH = 200
}

/** Translations that differ only in case, spacing or punctuation around them have the same key. */
fun String.translationKey(): String = lowercase().trim { !it.isLetterOrDigit() }.replace(WHITESPACE, " ")

private val WHITESPACE = Regex("\\s+")
