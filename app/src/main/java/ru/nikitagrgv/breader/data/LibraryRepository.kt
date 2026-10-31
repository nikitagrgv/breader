package ru.nikitagrgv.breader.data

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import ru.nikitagrgv.breader.format.BookFormat
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The list of added books, stored as JSON in the app's files dir.
 *
 * Mutations are expected on the main thread, after the library is loaded
 * (the UI doesn't offer any actions before that).
 */
class LibraryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val file = AtomicFile(File(appContext.filesDir, "library.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private var saveJob: Job? = null

    private val _library = MutableStateFlow<Library?>(null)

    /** `null` until the library is loaded from disk. */
    val library: StateFlow<Library?> = _library.asStateFlow()

    init {
        scope.launch {
            val loaded = try {
                read()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read the library", e)
                Library()
            }
            releaseUnusedPermissions(loaded)
            _library.value = loaded
        }
    }

    suspend fun awaitLibrary(): Library = library.filterNotNull().first()

    fun addBooks(books: List<Book>) = mutate { it.copy(books = books + it.books) }

    /** Returns the removed book with its index, so it can be put back with [insertBook]. */
    fun removeBook(id: String): IndexedValue<Book>? {
        val books = _library.value?.books ?: return null
        val index = books.indexOfFirst { it.id == id }
        if (index < 0) return null
        mutate { library ->
            library.copy(
                books = library.books.filterNot { it.id == id },
                currentBookId = library.currentBookId.takeIf { it != id },
            )
        }
        return IndexedValue(index, books[index])
    }

    fun insertBook(book: Book, index: Int) = mutate { library ->
        if (library.books.any { it.id == book.id }) return@mutate library
        library.copy(books = library.books.toMutableList().apply { add(index.coerceIn(0, size), book) })
    }

    fun setCurrentBook(id: String?) = mutate { it.copy(currentBookId = id) }

    fun updatePosition(id: String, position: ReadingPosition, progress: Float) =
        mutate(saveDelayMs = 1_000) { library ->
            library.copy(books = library.books.map {
                if (it.id == id) it.copy(position = position, progress = progress) else it
            })
        }

    private fun mutate(saveDelayMs: Long = 0, transform: (Library) -> Library) {
        val current = _library.value ?: return
        val updated = transform(current)
        if (updated == current) return
        _library.value = updated
        scheduleSave(saveDelayMs)
    }

    /** Coalesces frequent updates (like the reading position) into a single write. */
    private fun scheduleSave(delayMs: Long) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(delayMs)
            val library = _library.value ?: return@launch
            writeMutex.withLock { write(library) }
        }
    }

    private fun read(): Library {
        val bytes = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            return Library()
        }
        val json = JSONObject(bytes.decodeToString())
        val books = json.optJSONArray("books") ?: JSONArray()
        return Library(
            books = (0 until books.length()).mapNotNull { i ->
                runCatching { books.getJSONObject(i).toBook() }
                    .onFailure { Log.w(TAG, "Skipping a broken library entry", it) }
                    .getOrNull()
            },
            currentBookId = json.optString("currentBookId").ifEmpty { null },
        )
    }

    private fun write(library: Library) {
        val json = JSONObject()
            .put("currentBookId", library.currentBookId)
            .put("books", JSONArray().apply { library.books.forEach { put(it.toJson()) } })
        val stream = file.startWrite()
        try {
            stream.write(json.toString().encodeToByteArray())
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            Log.e(TAG, "Failed to save the library", e)
        }
    }

    /** The number of persisted grants is limited, so drop the ones left from removed books. */
    private fun releaseUnusedPermissions(library: Library) {
        val used = library.books.mapTo(HashSet()) { it.uri }
        val resolver = appContext.contentResolver
        resolver.persistedUriPermissions
            .filter { it.uri !in used }
            .forEach {
                runCatching { resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
    }

    private fun Book.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("uri", uri.toString())
        .put("fileName", fileName)
        .put("format", format.name)
        .put("title", title)
        .put("author", author)
        .put("addedAt", addedAt)
        .put("paragraph", position.paragraph)
        .put("offset", position.offset)
        .put("progress", progress.toDouble())

    private fun JSONObject.toBook() = Book(
        id = getString("id"),
        uri = getString("uri").toUri(),
        fileName = getString("fileName"),
        format = BookFormat.valueOf(getString("format")),
        title = getString("title"),
        author = optString("author").ifEmpty { null },
        addedAt = getLong("addedAt"),
        position = ReadingPosition(optInt("paragraph"), optInt("offset")),
        progress = optDouble("progress", 0.0).toFloat(),
    )

    private companion object {
        const val TAG = "LibraryRepository"
    }
}
