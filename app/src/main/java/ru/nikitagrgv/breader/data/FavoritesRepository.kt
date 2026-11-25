package ru.nikitagrgv.breader.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import ru.nikitagrgv.breader.dictionary.dictionaryKey
import ru.nikitagrgv.breader.translation.BookRange
import ru.nikitagrgv.breader.translation.Excerpt
import ru.nikitagrgv.breader.translation.MachineTranslationService
import ru.nikitagrgv.breader.translation.Translation
import ru.nikitagrgv.breader.translation.combine
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** What tells the meaning of a favorite. A favorite may have each of them, or none, if nothing could be had. */
enum class FavoriteKind {
    /** The meanings from the dictionaries. */
    DICTIONARY,

    /** The translation by the models, in the context it was selected in. */
    AI,

    /** The translation by a free service, of the text alone. */
    MACHINE,
}

/**
 * Where in a book a favorite was selected.
 * @param fingerprint the book's, see [Book.fingerprint]: it finds the book when it's removed and added again, which gives
 * it another ID, maybe under another name. Null if it wasn't known yet.
 * @param bookTitle as it was then, to tell which book it was when it's gone.
 */
data class FavoritePlace(val bookId: String, val fingerprint: String?, val bookTitle: String, val range: BookRange)

/** A translation by a free service, and which one it was. */
data class MachineText(val text: String, val service: MachineTranslationService)

/**
 * A word or a phrase kept from a book, with what tells its meaning, as it was when that was added.
 * @param key what it's found by, see [favoriteKey]; there's one favorite per key.
 * @param word as it's shown: the dictionary's spelling of a word, or the selected text.
 * @param meanings the meanings from the dictionaries, in a line; null if it isn't kept with them.
 * @param translations the models' translations: pairs of a model and its translation; empty if it isn't kept with them.
 * @param machine the translation by a free service; null if it isn't kept with one.
 * @param context the text around it where it was selected, which the models got too; null if it wasn't selected in a book.
 * @param place where it was selected; null if it wasn't selected in a book.
 */
data class Favorite(
    val key: String,
    val word: String,
    val added: Long,
    val meanings: String? = null,
    val translations: List<Pair<String, Translation>> = emptyList(),
    val machine: MachineText? = null,
    val context: Excerpt? = null,
    val place: FavoritePlace? = null,
) {
    /** What it's kept with; none if nothing could be had. */
    val kinds: Set<FavoriteKind>
        get() = buildSet {
            if (meanings != null) add(FavoriteKind.DICTIONARY)
            if (translations.isNotEmpty()) add(FavoriteKind.AI)
            if (machine != null) add(FavoriteKind.MACHINE)
        }

    /** All it's kept with, in a line: the translation fitting the context first, then the meanings. */
    val brief: String
        get() = listOfNotNull(
            translations.takeIf { it.isNotEmpty() }?.let { combine(it).variants.joinToString("; ") { variant -> variant.text } },
            meanings,
            machine?.text,
        ).joinToString(" · ")

    /** Without what it's kept with as [kind]. */
    fun without(kind: FavoriteKind): Favorite = when (kind) {
        FavoriteKind.DICTIONARY -> copy(meanings = null)
        FavoriteKind.AI -> copy(translations = emptyList())
        FavoriteKind.MACHINE -> copy(machine = null)
    }

    /**
     * With what [other], a favorite of the same text, is kept with too. The rest stays: where it was kept from first is
     * where it's learned from, unless it wasn't kept from a book then.
     */
    fun merge(other: Favorite): Favorite = copy(
        meanings = other.meanings ?: meanings,
        translations = other.translations.ifEmpty { translations },
        machine = other.machine ?: machine,
        context = context ?: other.context,
        place = place ?: other.place,
    )
}

/**
 * The same word selected anywhere has the same key: case and the punctuation around it don't matter.
 * Inflected forms are words of their own though: "pushed" isn't "push".
 */
fun favoriteKey(text: String): String = dictionaryKey(text.trim { !it.isLetterOrDigit() })

/** The favorites, newest first, stored as JSON in the app's files dir. */
class FavoritesRepository(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "favorites.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    /** Changes wait for the stored favorites, so that they don't replace them. */
    private val loaded = CompletableDeferred<Unit>()

    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()

    init {
        scope.launch {
            _favorites.value = try {
                read()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read the favorites", e)
                emptyList()
            }
            loaded.complete(Unit)
        }
    }

    /**
     * Changes the favorite with the [key] to what [transform] makes of it, which gets null if there's none. A new one goes
     * first, a changed one stays where it is, and one that [transform] makes null is removed.
     */
    fun update(key: String, transform: (Favorite?) -> Favorite?) = mutate { list ->
        val index = list.indexOfFirst { it.key == key }
        val updated = transform(list.getOrNull(index))
        when {
            index < 0 -> listOfNotNull(updated) + list
            updated == null -> list.filterIndexed { i, _ -> i != index }
            else -> list.toMutableList().apply { this[index] = updated }
        }
    }

    fun remove(keys: Set<String>) = mutate { list -> list.filter { it.key !in keys } }

    private fun mutate(transform: (List<Favorite>) -> List<Favorite>) {
        scope.launch {
            loaded.await()
            _favorites.update(transform)
            writeMutex.withLock { write(_favorites.value) }
        }
    }

    private fun read(): List<Favorite> {
        val bytes = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            return emptyList()
        }
        val array = JSONObject(bytes.decodeToString()).optJSONArray("favorites") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching { array.getJSONObject(i).toFavorite() }
                .onFailure { Log.w(TAG, "Skipping a broken favorite", it) }
                .getOrNull()
        }
    }

    private fun write(favorites: List<Favorite>) {
        val array = JSONArray()
        for (favorite in favorites) array.put(favorite.toJson())
        val stream = file.startWrite()
        try {
            stream.write(JSONObject().put("favorites", array).toString().encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            Log.e(TAG, "Failed to save the favorites", e)
        }
    }

    private companion object {
        const val TAG = "FavoritesRepository"
    }
}

internal fun Favorite.toJson(): JSONObject = JSONObject()
    .put("key", key)
    .put("word", word)
    .put("added", added)
    .apply {
        if (meanings != null) put("meanings", meanings)
        if (translations.isNotEmpty()) {
            put(
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
        if (machine != null) put("machine", JSONObject().put("text", machine.text).put("service", machine.service.name))
        if (context != null) {
            put("context", JSONObject().put("before", context.before).put("fragment", context.fragment).put("after", context.after))
        }
        if (place != null) {
            put(
                "place",
                JSONObject()
                    .put("bookId", place.bookId)
                    .put("fingerprint", place.fingerprint)
                    .put("title", place.bookTitle)
                    .put("range", JSONArray(listOf(place.range).toInts())),
            )
        }
    }

/** The ones stored before there were translations have their meanings as the "brief". */
internal fun JSONObject.toFavorite(): Favorite = Favorite(
    key = getString("key"),
    word = getString("word"),
    added = optLong("added"),
    meanings = (opt("meanings") ?: opt("brief")) as? String,
    translations = optJSONArray("translations")?.let { array ->
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val alternatives = item.optJSONArray("alternatives") ?: JSONArray()
            item.getString("model") to Translation(
                text = item.getString("text"),
                alternatives = (0 until alternatives.length()).map { alternatives.getString(it) },
                note = item.optString("note"),
            )
        }
    }.orEmpty(),
    machine = optJSONObject("machine")?.let { json ->
        val service = MachineTranslationService.entries.find { it.name == json.optString("service") }
            ?: MachineTranslationService.GOOGLE
        MachineText(json.getString("text"), service)
    },
    context = optJSONObject("context")?.let { Excerpt(it.optString("before"), it.getString("fragment"), it.optString("after")) },
    // A place that isn't one any more is left out, rather than the favorite.
    place = optJSONObject("place")?.let { json ->
        val range = json.optJSONArray("range")
            ?.let { array -> rangesFromInts((0 until array.length()).map { array.optInt(it, -1) }) }
            ?.singleOrNull()
            ?: return@let null
        FavoritePlace(
            bookId = json.getString("bookId"),
            fingerprint = json.optString("fingerprint").ifEmpty { null },
            bookTitle = json.optString("title"),
            range = range,
        )
    },
)
