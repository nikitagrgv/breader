package ru.nikitagrgv.breader.web

import ru.nikitagrgv.breader.translation.abortOnCancel
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * An error reported by a web service; [code] is an HTTP status, or the one that means the same for services that report
 * errors their own way: 401 for a bad key, 429 for too many requests.
 */
class WebServiceException(val code: Int, message: String) : IOException(message)

/** A reply of a web service: the HTTP status and the body, an error's too. */
internal class HttpReply(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
}

/** GETs [url]. Cancelling aborts the request. */
internal suspend fun httpGet(url: String, headers: Map<String, String> = emptyMap()): HttpReply {
    val connection = URL(url).openConnection() as HttpURLConnection
    return connection.abortOnCancel {
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        for ((name, value) in headers) setRequestProperty(name, value)
        val code = responseCode
        val body = (if (code < 400) inputStream else errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
        HttpReply(code, body)
    }
}

/** For a query parameter. */
internal fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

/** The text of an HTML fragment: without the tags, with the entities decoded and the whitespace collapsed. */
internal fun htmlToText(html: String): String =
    decodeEntities(html.replace(TAG, "")).replace(WHITESPACE, " ").trim()

internal fun decodeEntities(text: String): String {
    if ('&' !in text) return text
    return ENTITY.replace(text) { match ->
        val name = match.groupValues[1]
        val code = when {
            name.startsWith("#x", ignoreCase = true) -> name.drop(2).toIntOrNull(16)
            name.startsWith("#") -> name.drop(1).toIntOrNull()
            else -> NAMED_ENTITIES[name]
        }
        if (code != null && Character.isValidCodePoint(code)) String(Character.toChars(code)) else match.value
    }
}

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private val TAG = Regex("<[^>]*>")
private val WHITESPACE = Regex("\\s+")
private val ENTITY = Regex("&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);")
private val NAMED_ENTITIES = mapOf("amp" to '&'.code, "lt" to '<'.code, "gt" to '>'.code, "quot" to '"'.code, "apos" to '\''.code, "nbsp" to ' '.code)
