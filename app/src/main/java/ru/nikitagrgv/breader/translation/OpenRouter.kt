package ru.nikitagrgv.breader.translation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import java.time.Instant
import java.time.format.DateTimeParseException

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

/** What a request to the models was made for. */
enum class RequestKind { TRANSLATION, GRAMMAR }

/** A request to the models that got a reply, and so was billed. */
data class RequestUsage(
    /** Unix time in ms when the reply came. */
    val time: Long,
    val kind: RequestKind,
    /** The selected text the request was about. */
    val fragment: String,
    /** The model that replied, which for a router isn't the one asked. */
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    /** In USD; null if OpenRouter didn't tell. */
    val cost: Double?,
)

/** What OpenRouter tells about an API key. The amounts are in USD, which OpenRouter credits are. */
data class KeyInfo(
    /** Null if the key has no limit of its own, and only the account credits limit it. */
    val limit: Double?,
    val limitRemaining: Double?,
    /** How often the limit starts anew: "daily", "weekly" or "monthly"; null if never. */
    val limitReset: String?,
    /** Spent with the key in all. */
    val usage: Double,
    /** Spent in the current UTC day, week and month. */
    val usageDaily: Double,
    val usageWeekly: Double,
    val usageMonthly: Double,
    /** Requests to the free models today, which have a daily limit of their own; null if not told. */
    val freeRequests: FreeRequests?,
    /** Null if the key doesn't expire. */
    val expiresAt: Instant?,
)

data class FreeRequests(val used: Int, val limit: Int)

/** The credits of the whole account, shared by all its keys; in USD. */
data class Credits(val total: Double, val used: Double) {
    /** Negative if the account went into debt. */
    val remaining: Double
        get() = total - used
}

/**
 * A client of the OpenRouter API, see https://openrouter.ai/docs.
 * @param onUsage gets each billed request to the models.
 */
class OpenRouter(private val onUsage: suspend (RequestUsage) -> Unit = {}) {
    /**
     * Where the requests to the models run. One that's dropped halfway is billed all the same, so it's let finish
     * to log its cost; the caller just stops waiting for it.
     */
    private val chatScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun models(order: CatalogOrder): List<ModelInfo> {
        val json = request("$BASE_URL/models?sort=${order.param}")
        return withContext(Dispatchers.Default) {
            val data = json.getJSONArray("data")
            (0 until data.length()).mapNotNull { data.getJSONObject(it).toModelInfo() }
        }
    }

    suspend fun keyInfo(apiKey: String): KeyInfo = request("$BASE_URL/key", apiKey).getJSONObject("data").toKeyInfo()

    /**
     * Null if the key may not see them. OpenRouter's docs say they take a management key, though for now
     * an ordinary one gets them too.
     */
    suspend fun credits(apiKey: String): Credits? = try {
        request("$BASE_URL/credits", apiKey).getJSONObject("data").toCredits()
    } catch (e: OpenRouterException) {
        if (e.code == 401 || e.code == 403) null else throw e
    }

    /**
     * Asks [model] for a reply in JSON format. Reasoning is turned off where possible: with it, a
     * reply takes up to a minute instead of a couple of seconds.
     * @param kind what the request is for, and [fragment] the selected text it's about: both for [onUsage].
     */
    suspend fun chat(
        apiKey: String,
        model: String,
        system: String,
        user: String,
        kind: RequestKind,
        fragment: String,
    ): String = chatScope.async { exchange(apiKey, model, system, user, kind, fragment) }.await()

    private suspend fun exchange(
        apiKey: String,
        model: String,
        system: String,
        user: String,
        kind: RequestKind,
        fragment: String,
    ): String {
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
        onUsage(response.toUsage(System.currentTimeMillis(), kind, fragment, model))
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

/** A reply of the chat completions endpoint as [RequestUsage]; [askedModel] if the reply doesn't name its model. */
internal fun JSONObject.toUsage(time: Long, kind: RequestKind, fragment: String, askedModel: String): RequestUsage {
    val usage = optJSONObject("usage")
    return RequestUsage(
        time = time,
        kind = kind,
        fragment = fragment,
        model = optString("model").ifEmpty { askedModel },
        promptTokens = usage?.optInt("prompt_tokens") ?: 0,
        completionTokens = usage?.optInt("completion_tokens") ?: 0,
        cost = usage?.optDoubleOrNull("cost"),
    )
}

/** The "data" of the /key endpoint as [KeyInfo]. */
internal fun JSONObject.toKeyInfo(): KeyInfo = KeyInfo(
    limit = optDoubleOrNull("limit"),
    limitRemaining = optDoubleOrNull("limit_remaining"),
    limitReset = optStringOrNull("limit_reset"),
    usage = optDoubleOrNull("usage") ?: 0.0,
    usageDaily = optDoubleOrNull("usage_daily") ?: 0.0,
    usageWeekly = optDoubleOrNull("usage_weekly") ?: 0.0,
    usageMonthly = optDoubleOrNull("usage_monthly") ?: 0.0,
    freeRequests = optJSONObject("free_model_daily_requests")?.let { free ->
        val used = free.optInt("used", -1)
        val limit = free.optInt("limit", -1)
        if (used >= 0 && limit > 0) FreeRequests(used, limit) else null
    },
    expiresAt = optStringOrNull("expires_at")?.let {
        try {
            Instant.parse(it)
        } catch (_: DateTimeParseException) {
            null
        }
    },
)

/** The "data" of the /credits endpoint as [Credits]. */
internal fun JSONObject.toCredits(): Credits = Credits(total = getDouble("total_credits"), used = getDouble("total_usage"))

/** Null if there's no such key, it's null, or not a number. */
private fun JSONObject.optDoubleOrNull(key: String): Double? = optDouble(key).takeUnless { it.isNaN() }

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }

/**
 * Runs the blocking [block] on the IO dispatcher. Blocking reads ignore coroutine cancellation,
 * so on cancellation the connection is closed to abort them, instead of waiting for the timeout.
 */
internal suspend fun <T> HttpURLConnection.abortOnCancel(block: HttpURLConnection.() -> T): T = coroutineScope {
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
