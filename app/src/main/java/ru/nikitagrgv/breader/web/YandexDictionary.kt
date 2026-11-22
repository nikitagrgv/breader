package ru.nikitagrgv.breader.web

import org.json.JSONObject
import ru.nikitagrgv.breader.dictionary.DictionaryEntry
import ru.nikitagrgv.breader.dictionary.DictionaryExample
import ru.nikitagrgv.breader.dictionary.DictionarySense
import ru.nikitagrgv.breader.dictionary.objects
import ru.nikitagrgv.breader.dictionary.string

/**
 * Yandex Dictionary, see https://yandex.com/dev/dictionary/: translations of words with their synonyms and examples, for
 * pairs of languages mostly with Russian or English. It's free, but needs a key that the user gets from Yandex.
 */
object YandexDictionary {
    const val KEY_URL = "https://yandex.ru/dev/dictionary/keys/get/?service=dict"

    /** Empty if the dictionary has nothing, or isn't for these languages. */
    suspend fun lookUp(key: String, text: String, from: String, to: String): List<DictionaryEntry> {
        val url = "https://dictionary.yandex.net/api/v1/dicservice.json/lookup" +
            "?key=${key.trim().urlEncoded()}&lang=$from-$to&ui=$to&text=${text.urlEncoded()}"
        val reply = httpGet(url)
        if (reply.isSuccess) return parse(reply.body)
        // The HTTP status doesn't tell the error, the code of the API's own does.
        val error = runCatching { JSONObject(reply.body) }.getOrNull()
        val message = error?.string("message")?.ifEmpty { null } ?: "Yandex Dictionary: HTTP ${reply.code}"
        when (error?.optInt("code")) {
            ERR_LANG_NOT_SUPPORTED, ERR_TEXT_TOO_LONG -> return emptyList()
            ERR_KEY_INVALID, ERR_KEY_BLOCKED -> throw WebServiceException(401, message)
            ERR_DAILY_REQ_LIMIT_EXCEEDED -> throw WebServiceException(429, message)
            else -> throw WebServiceException(reply.code, message)
        }
    }

    internal fun parse(json: String): List<DictionaryEntry> =
        JSONObject(json).optJSONArray("def").objects().mapNotNull { definition ->
            val senses = definition.optJSONArray("tr").objects().mapNotNull { translation ->
                // The synonyms are translations of the same meaning.
                val words = (listOf(translation) + translation.optJSONArray("syn").objects())
                    .map { it.string("text") }
                    .filter { it.isNotEmpty() }
                if (words.isEmpty()) return@mapNotNull null
                DictionarySense(
                    gloss = words.joinToString(", "),
                    examples = translation.optJSONArray("ex").objects().mapNotNull { example ->
                        val text = example.string("text").ifEmpty { return@mapNotNull null }
                        DictionaryExample(text, example.optJSONArray("tr").objects().joinToString { it.string("text") })
                    }.take(MAX_EXAMPLES),
                    backTranslations = translation.optJSONArray("mean").objects().map { it.string("text") }.filter { it.isNotEmpty() },
                )
            }
            if (senses.isEmpty()) return@mapNotNull null
            DictionaryEntry(
                word = definition.string("text"),
                pos = definition.string("pos"),
                forms = emptyList(),
                senses = senses,
                transcription = definition.string("ts"),
            )
        }

    private const val MAX_EXAMPLES = 2
    private const val ERR_KEY_INVALID = 401
    private const val ERR_KEY_BLOCKED = 402
    private const val ERR_DAILY_REQ_LIMIT_EXCEEDED = 403
    private const val ERR_TEXT_TOO_LONG = 413
    private const val ERR_LANG_NOT_SUPPORTED = 501
}
