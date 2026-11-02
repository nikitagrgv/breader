package ru.nikitagrgv.breader.translation

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * What a translation depends on. The same fragment may mean something else in another context,
 * so the context is a part of the key, and a change of the context size gives new translations.
 */
data class TranslationKey(val excerpt: Excerpt, val sourceLanguage: String, val targetLanguage: String)

/**
 * Translations of the recently selected fragments, by model; kept in the app's cache dir across launches.
 * Only successful translations are kept, so the models that failed are asked again next time.
 * Beyond [maxSize] fragments, the least recently used ones are dropped.
 *
 * Must be used on the main thread.
 */
class TranslationCache(context: Context, maxSize: Flow<Int>) {
    private val file = AtomicFile(File(context.applicationContext.cacheDir, "translations.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writeMutex = Mutex()
    private var saveJob: Job? = null

    /** In access order, the least recently used first. Translations of each are in the order they came. */
    private val entries = LinkedHashMap<TranslationKey, Map<String, Translation>>(16, 0.75f, true)
    private var limit = 0

    /** Completed once the stored entries and the size limit are known; nothing is done with the cache before. */
    private val ready = CompletableDeferred<Unit>()

    private val _size = MutableStateFlow(0)

    /** The number of fragments in the cache. */
    val size: StateFlow<Int> = _size.asStateFlow()

    init {
        scope.launch {
            val stored = withContext(Dispatchers.IO) {
                try {
                    read()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read the translation cache", e)
                    emptyList()
                }
            }
            for ((key, translations) in stored) entries[key] = translations
            // The limit is known before anything is added: trimming to a default one would lose the stored entries.
            limit = maxSize.first()
            trim()
            _size.value = entries.size
            ready.complete(Unit)
            maxSize.collect {
                limit = it
                trim()
            }
        }
    }

    /** Translations of [key] by model, in the order they came; empty if none are cached. */
    suspend fun get(key: TranslationKey): Map<String, Translation> {
        ready.await()
        // Marks it as recently used, which is to be kept too.
        return entries[key]?.also { scheduleSave() }.orEmpty()
    }

    suspend fun put(key: TranslationKey, model: String, translation: Translation) {
        ready.await()
        if (limit <= 0) return
        entries[key] = entries[key].orEmpty() + (model to translation)
        _size.value = entries.size
        trim()
        scheduleSave()
    }

    suspend fun clear() {
        ready.await()
        if (entries.isEmpty()) return
        entries.clear()
        _size.value = 0
        scheduleSave()
    }

    private fun trim() {
        if (entries.size <= limit) return
        val iterator = entries.keys.iterator()
        while (entries.size > limit) {
            iterator.next()
            iterator.remove()
        }
        _size.value = entries.size
        scheduleSave()
    }

    /** Coalesces translations coming one after another into a single write. */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            // Taken on the main thread: the entries are only changed there.
            val snapshot = entries.toList()
            withContext(Dispatchers.IO) { writeMutex.withLock { write(snapshot) } }
        }
    }

    private fun read(): List<Pair<TranslationKey, Map<String, Translation>>> {
        val bytes = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        val array = JSONObject(bytes.decodeToString()).optJSONArray("entries") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching { array.getJSONObject(i).toEntry() }
                .onFailure { Log.w(TAG, "Skipping a broken cache entry", it) }
                .getOrNull()
        }
    }

    private fun write(snapshot: List<Pair<TranslationKey, Map<String, Translation>>>) {
        val json = JSONObject().put("entries", JSONArray().apply { snapshot.forEach { put(it.toJson()) } })
        val stream = file.startWrite()
        try {
            stream.write(json.toString().encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            Log.e(TAG, "Failed to save the translation cache", e)
        }
    }

    private fun Pair<TranslationKey, Map<String, Translation>>.toJson(): JSONObject {
        val (key, translations) = this
        return JSONObject()
            .put("before", key.excerpt.before)
            .put("fragment", key.excerpt.fragment)
            .put("after", key.excerpt.after)
            .put("sourceLanguage", key.sourceLanguage)
            .put("targetLanguage", key.targetLanguage)
            .put(
                "translations",
                JSONArray().apply {
                    for ((model, translation) in translations) {
                        put(
                            JSONObject()
                                .put("model", model)
                                .put("text", translation.text)
                                .put("alternatives", JSONArray(translation.alternatives))
                                .put("note", translation.note),
                        )
                    }
                },
            )
    }

    private fun JSONObject.toEntry(): Pair<TranslationKey, Map<String, Translation>> {
        val key = TranslationKey(
            excerpt = Excerpt(getString("before"), getString("fragment"), getString("after")),
            sourceLanguage = getString("sourceLanguage"),
            targetLanguage = getString("targetLanguage"),
        )
        val array = getJSONArray("translations")
        val translations = LinkedHashMap<String, Translation>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val alternatives = item.optJSONArray("alternatives") ?: JSONArray()
            translations[item.getString("model")] = Translation(
                text = item.getString("text"),
                alternatives = (0 until alternatives.length()).map { alternatives.getString(it) },
                note = item.optString("note"),
            )
        }
        return key to translations
    }

    private companion object {
        const val TAG = "TranslationCache"
        const val SAVE_DELAY_MS = 1_000L
    }
}
