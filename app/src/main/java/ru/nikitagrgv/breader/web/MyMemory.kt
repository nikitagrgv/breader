package ru.nikitagrgv.breader.web

import org.json.JSONException
import org.json.JSONObject
import ru.nikitagrgv.breader.dictionary.string
import ru.nikitagrgv.breader.translation.MachineTranslation

/**
 * MyMemory, see https://mymemory.translated.net/doc/spec.php: a free API with no key. It looks the text up in a memory
 * of human translations and translates by machine what isn't there. A few thousand words a day per device are free.
 */
object MyMemory {
    /** At most this many bytes of UTF-8 per request. */
    const val MAX_BYTES = 500

    /** @param from the code of the language, null to detect it. */
    suspend fun translate(text: String, from: String?, to: String): MachineTranslation {
        val pair = "${from ?: "autodetect"}|$to"
        val reply = httpGet("https://api.mymemory.translated.net/get?q=${text.urlEncoded()}&langpair=${pair.urlEncoded()}")
        if (!reply.isSuccess && !reply.body.trimStart().startsWith("{")) {
            throw WebServiceException(reply.code, "MyMemory: HTTP ${reply.code}")
        }
        return parse(reply.body)
    }

    /** Errors come in the body, often with the HTTP status 200. */
    internal fun parse(json: String): MachineTranslation {
        val root = JSONObject(json)
        val status = root.opt("responseStatus")?.toString()?.toIntOrNull() ?: 200
        val details = root.string("responseDetails")
        val data = root.optJSONObject("responseData")
        val text = data?.string("translatedText").orEmpty()
        if (root.optBoolean("quotaFinished") || status == 429 || text.startsWith(QUOTA_WARNING)) {
            throw WebServiceException(429, details.ifEmpty { "MyMemory: no free translations left for today" })
        }
        if (status !in 200..299) throw WebServiceException(status, details.ifEmpty { "MyMemory: error $status" })
        if (data == null) throw JSONException("No responseData")
        return MachineTranslation(decodeEntities(text), data.string("detectedLanguage").ifEmpty { null })
    }

    private const val QUOTA_WARNING = "MYMEMORY WARNING"
}
