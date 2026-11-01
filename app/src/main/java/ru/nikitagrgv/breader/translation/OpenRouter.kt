package ru.nikitagrgv.breader.translation

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A model from the OpenRouter catalog. */
data class ModelInfo(
    val id: String,
    val name: String,
    /** USD per token; null if unknown, e.g. for routers that pick a model per request. */
    val promptPrice: Double?,
    val completionPrice: Double?,
    /** Unix time in seconds. */
    val created: Long,
) {
    val isFree: Boolean
        get() = promptPrice == 0.0 && completionPrice == 0.0

    /** For comparing models by price; null if unknown. */
    val price: Double?
        get() = if (promptPrice != null && completionPrice != null) promptPrice + completionPrice else null
}

/** Orders of the catalog that only OpenRouter knows: they come from usage stats and benchmarks. */
enum class CatalogOrder(val param: String) {
    POPULARITY("top-weekly"),
    INTELLIGENCE("intelligence-high-to-low"),
    SPEED("throughput-high-to-low"),
}

/** An error reported by OpenRouter or a provider behind it; [code] is an HTTP status. */
class OpenRouterException(val code: Int, message: String) : IOException(message)

/** A client of the OpenRouter API, see https://openrouter.ai/docs. */
class OpenRouter {
    suspend fun models(order: CatalogOrder): List<ModelInfo> {
        val json = request("$BASE_URL/models?sort=${order.param}")
        return withContext(Dispatchers.Default) {
            val data = json.getJSONArray("data")
            (0 until data.length()).mapNotNull { data.getJSONObject(it).toModelInfo() }
        }
    }

    /**
     * Asks [model] for a reply in JSON format. Reasoning is turned off where possible: with it, a
     * reply takes up to a minute instead of a couple of seconds.
     */
    suspend fun chat(apiKey: String, model: String, system: String, user: String): String {
        fun body(reasoning: JSONObject) = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            )
            // Ignored by the models that don't support it, the prompt asks for JSON anyway.
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("reasoning", reasoning)

        val url = "$BASE_URL/chat/completions"
        val response = try {
            request(url, apiKey, body(JSONObject().put("enabled", false)))
        } catch (e: OpenRouterException) {
            // Some models can't work without reasoning, those get the least of it.
            if (e.code != 400 || e.message?.contains("reasoning", ignoreCase = true) != true) throw e
            request(url, apiKey, body(JSONObject().put("effort", "low").put("exclude", true)))
        }
        val choice = response.optJSONArray("choices")?.optJSONObject(0)
        choice?.optJSONObject("error")?.let { throw it.toException(fallbackCode = 502) }
        return choice?.optJSONObject("message")?.opt("content") as? String ?: ""
    }

    private suspend fun request(url: String, apiKey: String? = null, body: JSONObject? = null): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        return connection.abortOnCancel {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Title", APP_TITLE)
            if (apiKey != null) setRequestProperty("Authorization", "Bearer $apiKey")
            if (body != null) {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toString().encodeToByteArray()) }
            }
            val code = responseCode
            val text = (if (code < 400) inputStream else errorStream)?.use { it.readBytes().decodeToString() }
            val json = try {
                text?.let(::JSONObject)
            } catch (_: JSONException) {
                null
            }
            json?.optJSONObject("error")?.let { throw it.toException(fallbackCode = code) }
            if (code !in 200..299 || json == null) throw OpenRouterException(code, "HTTP $code")
            json
        }
    }

    private companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        const val APP_TITLE = "Breader"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 90_000
        const val CHAT_ENDPOINT = "/api/v1/chat/completions"

        /** Null for models that can't be chatted with, like batch-only variants or image generators. */
        fun JSONObject.toModelInfo(): ModelInfo? {
            optJSONArray("endpoints")?.let { endpoints ->
                if ((0 until endpoints.length()).none { endpoints.optString(it) == CHAT_ENDPOINT }) return null
            }
            optJSONObject("architecture")?.optJSONArray("output_modalities")?.let { outputs ->
                if ((0 until outputs.length()).none { outputs.optString(it) == "text" }) return null
            }
            val id = optString("id").ifEmpty { return null }
            val pricing = optJSONObject("pricing")
            // Routers have negative prices, meaning that they depend on the chosen model.
            fun price(key: String) = pricing?.optString(key)?.toDoubleOrNull()?.takeIf { it >= 0 }
            return ModelInfo(
                id = id,
                name = optString("name").ifEmpty { id },
                promptPrice = price("prompt"),
                completionPrice = price("completion"),
                created = optLong("created"),
            )
        }

        fun JSONObject.toException(fallbackCode: Int): OpenRouterException {
            // "Provider returned error" says little by itself, the provider's own message follows it.
            val raw = optJSONObject("metadata")?.opt("raw") as? String
            val message = listOf(optString("message"), raw.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(": ")
            return OpenRouterException(optInt("code", fallbackCode), message.ifEmpty { "HTTP $fallbackCode" })
        }
    }
}

/**
 * Runs the blocking [block] on the IO dispatcher. Blocking reads ignore coroutine cancellation,
 * so on cancellation the connection is closed to abort them, instead of waiting for the timeout.
 */
private suspend fun <T> HttpURLConnection.abortOnCancel(block: HttpURLConnection.() -> T): T = coroutineScope {
    // Undispatched, so the finally block is guaranteed to run even if cancelled right away.
    val aborter = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            disconnect()
        }
    }
    try {
        withContext(Dispatchers.IO) { block() }
    } finally {
        aborter.cancel()
    }
}
