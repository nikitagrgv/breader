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
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * A word kept from a book.
 * @param key what it's found by, see [favoriteKey].
 * @param brief its meanings from the dictionaries when it was added.
 */
data class Favorite(val key: String, val word: String, val brief: String, val added: Long)

/**
 * The same word selected anywhere has the same key: case and the punctuation around it don't matter.
 * Inflected forms are words of their own though: "pushed" isn't "push".
 */
fun favoriteKey(text: String): String = dictionaryKey(text.trim { !it.isLetterOrDigit() })

/** The favorite words, newest first, stored as JSON in the app's files dir. */
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

    /** Replaces the favorite with the same key, if there is one. */
    fun add(favorite: Favorite) = mutate { list -> listOf(favorite) + list.filter { it.key != favorite.key } }

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
            runCatching {
                val json = array.getJSONObject(i)
                Favorite(json.getString("key"), json.getString("word"), json.optString("brief"), json.optLong("added"))
            }.onFailure { Log.w(TAG, "Skipping a broken favorite", it) }.getOrNull()
        }
    }

    private fun write(favorites: List<Favorite>) {
        val array = JSONArray()
        for (favorite in favorites) {
            array.put(
                JSONObject()
                    .put("key", favorite.key)
                    .put("word", favorite.word)
                    .put("brief", favorite.brief)
                    .put("added", favorite.added),
            )
        }
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
