package ru.nikitagrgv.breader.dictionary

import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import ru.nikitagrgv.breader.data.AppSettings
import ru.nikitagrgv.breader.data.DictionarySettings
import ru.nikitagrgv.breader.web.GoogleTranslate
import ru.nikitagrgv.breader.web.WebServiceException
import ru.nikitagrgv.breader.web.WiktionaryApi
import ru.nikitagrgv.breader.web.YandexDictionary
import java.io.IOException
import java.net.SocketTimeoutException

/** Dictionaries on the web, in the default order of their results: the ones that translate first. */
enum class OnlineDictionary {
    /** Translations with synonyms and examples. Needs a free key the user gets from Yandex. */
    YANDEX,

    /** Translations by part of speech, the common ones. */
    GOOGLE,

    /** English Wiktionary: the words of every language explained in English, the most of them of any dictionary. */
    WIKTIONARY;

    /** Whether it explains the words rather than translating them. */
    val explains: Boolean get() = this == WIKTIONARY
}

/** Why an online dictionary couldn't be asked. */
data class LookupFailure(val kind: Kind, val details: String? = null) {
    enum class Kind {
        NETWORK,
        RATE_LIMITED,
        INVALID_KEY,

        /** It translates from a given language only, and "Translate from" isn't set. */
        NEEDS_SOURCE_LANGUAGE,
        OTHER,
    }
}

/**
 * What a lookup has found so far.
 * @param results in the order of the settings.
 * @param pending the online dictionaries that are still being asked.
 * @param failed the online dictionaries that couldn't be asked.
 */
data class LookupProgress(
    val results: List<DictionaryResult>,
    val pending: List<OnlineDictionary> = emptyList(),
    val failed: Map<OnlineDictionary, LookupFailure> = emptyMap(),
)

/** All the dictionaries, the [installed] ones and the online ones, turned on or not, in the order of the settings. */
fun DictionarySettings.all(installed: List<DictionaryInfo>): List<DictionarySource> =
    ordered(installed.map { DictionarySource.Downloaded(it) } + OnlineDictionary.entries.map { DictionarySource.Online(it) }) {
        it.orderKey
    }

/** The dictionaries that are looked in, of the [installed] ones and the online ones, in the order of the settings. */
fun DictionarySettings.inUse(installed: List<DictionaryInfo>): List<DictionarySource> {
    val usable = usable.toSet()
    return all(installed).filter { source ->
        when (source) {
            is DictionarySource.Downloaded -> source.info.id !in disabled
            is DictionarySource.Online -> source.dictionary in usable
        }
    }
}

/** The downloaded dictionaries together with the online ones, as the settings have them. */
class Dictionaries(private val downloaded: DictionaryRepository, private val settings: Flow<AppSettings>) {
    /** Online results, so that looking something up again needs no requests. Failures aren't kept. */
    private val cache = LruCache<CacheKey, List<DictionaryEntry>>(CACHE_SIZE)

    /**
     * Looks [text] up, a word or a phrase. The downloaded dictionaries are looked in at once, and the online ones are
     * asked all at the same time; every result is emitted as it comes, along with what's found before. The last
     * value has all of them.
     */
    fun lookup(text: String): Flow<LookupProgress> = channelFlow {
        val settings = settings.first()
        val dictionaries = settings.dictionaries
        val local = downloaded.lookup(text, skip = dictionaries.disabled)
        // Quotes and punctuation around a word are often selected along with it.
        val bare = text.trim { !it.isLetterOrDigit() }
        val online = if (bare.isEmpty() || bare.length > MAX_LOOKUP_LENGTH) emptyList() else dictionaries.usable
        if (online.isEmpty()) {
            send(LookupProgress(dictionaries.ordered(local) { it.source.orderKey }))
            return@channelFlow
        }
        val found = HashMap<OnlineDictionary, List<DictionaryEntry>>()
        val failed = LinkedHashMap<OnlineDictionary, LookupFailure>()
        val mutex = Mutex()
        fun progress() = LookupProgress(
            results = dictionaries.ordered(
                local + online.mapNotNull { dictionary ->
                    found[dictionary]?.takeIf { it.isNotEmpty() }?.let { DictionaryResult(DictionarySource.Online(dictionary), it) }
                },
            ) { it.source.orderKey },
            pending = online.filter { it !in found && it !in failed },
            failed = failed.toMap(),
        )
        send(progress())
        for (dictionary in online) launch {
            val outcome = try {
                Result.success(ask(dictionary, bare, settings))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to look up \"$bare\" in $dictionary", e)
                Result.failure(e)
            }
            mutex.withLock {
                outcome.onSuccess { found[dictionary] = it }.onFailure { failed[dictionary] = it.toLookupFailure() }
                send(progress())
            }
        }
    }

    /**
     * What the dictionaries have for [text], once all of them answer or [timeoutMs] passes, whichever comes first: the
     * online ones that are too slow, or fail, are left out.
     */
    suspend fun lookupAll(text: String, timeoutMs: Long = LOOKUP_ALL_TIMEOUT_MS): List<DictionaryResult> {
        var found = emptyList<DictionaryResult>()
        withTimeoutOrNull(timeoutMs) { lookup(text).collect { found = it.results } }
        return found
    }

    /** Words of the downloaded dictionaries that are turned on that start with [prefix], for completing what's being typed. */
    suspend fun wordsStartingWith(prefix: String, limit: Int): List<String> =
        downloaded.wordsStartingWith(prefix, limit, skip = settings.first().dictionaries.disabled)

    private suspend fun ask(dictionary: OnlineDictionary, text: String, settings: AppSettings): List<DictionaryEntry> {
        val translation = settings.translation
        val from = translation.sourceCode
        val to = translation.targetCode
        // Nothing to translate into the language it's in.
        if (!dictionary.explains && from == to) return emptyList()
        val key = CacheKey(dictionary, dictionaryKey(text), from, to)
        cache[key]?.let { return it }
        val entries = when (dictionary) {
            OnlineDictionary.YANDEX -> YandexDictionary.lookUp(
                key = settings.dictionaries.yandexKey,
                text = text,
                from = from ?: throw LookupException(LookupFailure.Kind.NEEDS_SOURCE_LANGUAGE),
                to = to,
            )
            OnlineDictionary.GOOGLE -> GoogleTranslate.lookUp(text, from, to)
            OnlineDictionary.WIKTIONARY -> WiktionaryApi.lookUp(text, from)
        }
        cache.put(key, entries)
        return entries
    }

    private data class CacheKey(val dictionary: OnlineDictionary, val text: String, val from: String?, val to: String)

    private companion object {
        const val TAG = "Dictionaries"
        const val CACHE_SIZE = 200
        const val LOOKUP_ALL_TIMEOUT_MS = 4_000L
    }
}

private class LookupException(val kind: LookupFailure.Kind) : Exception(kind.name)

private fun Throwable.toLookupFailure(): LookupFailure = when (this) {
    is LookupException -> LookupFailure(kind)
    is WebServiceException -> when (code) {
        401 -> LookupFailure(LookupFailure.Kind.INVALID_KEY)
        429 -> LookupFailure(LookupFailure.Kind.RATE_LIMITED)
        else -> LookupFailure(LookupFailure.Kind.OTHER, message)
    }
    is SocketTimeoutException, is IOException -> LookupFailure(LookupFailure.Kind.NETWORK)
    is JSONException -> LookupFailure(LookupFailure.Kind.OTHER, "Unexpected reply")
    else -> LookupFailure(LookupFailure.Kind.OTHER, message ?: javaClass.simpleName)
}
