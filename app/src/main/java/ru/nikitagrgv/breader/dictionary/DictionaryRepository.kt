package ru.nikitagrgv.breader.dictionary

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.nikitagrgv.breader.translation.abortOnCancel
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * @param name the language of the words, named in the [edition]'s language, e.g. "Английский".
 * @param languageCode of the words, e.g. "en".
 */
data class DictionaryInfo(
    val id: String,
    val name: String,
    val edition: Edition,
    val languageCode: String,
    val entries: Int,
    val sizeBytes: Long,
    val created: Long,
)

/** The entries one dictionary has for a looked up text. */
data class DictionaryResult(val dictionary: DictionaryInfo, val entries: List<DictionaryEntry>)

/** All the meanings in one line, as a brief translation; null if there are none. */
fun List<DictionaryResult>.meaningsLine(): String? =
    flatMap { result -> result.entries.flatMap { entry -> entry.senses.map { it.gloss } } }
        .distinct()
        .joinToString("; ")
        .ifEmpty { null }

sealed interface DownloadState {
    /**
     * @param indexing whether everything is downloaded and the dictionary is being finished.
     * @param totalBytes null if unknown.
     * @param wholeEdition whether the whole edition is downloaded, the language's own file being gone.
     */
    data class Running(val indexing: Boolean, val bytes: Long, val totalBytes: Long?, val wholeEdition: Boolean) : DownloadState

    data class Failed(val error: DownloadError) : DownloadState
}

/** A dictionary being downloaded, or failed to be. */
data class Download(val edition: Edition, val language: CatalogLanguage, val state: DownloadState)

enum class DownloadError {
    NETWORK,
    NOT_FOUND,

    /** Nothing of the language was in the download. */
    EMPTY,

    /** The language has no file of its own, and the whole edition is too big to download. */
    TOO_BIG,
    OTHER,
}

/** For the files of [Kaikki.languages]: they're installed under this ID. */
fun dictionaryId(edition: Edition, language: CatalogLanguage): String =
    "${edition.code}-${language.path.replace(UNSAFE_FILE_CHARS, "_")}"

/**
 * The downloaded dictionaries, made from kaikki.org data and kept in the app's files dir. Downloads run here,
 * so they go on when the screen that started them is closed. There must be a single instance per process.
 */
class DictionaryRepository(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "dictionaries")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Guards [readers] and their files: a dictionary isn't closed or replaced while it's being looked in. */
    private val mutex = Mutex()
    private val readers = HashMap<String, DictionaryReader>()

    /** Only used on the main thread. */
    private val jobs = HashMap<String, Job>()

    private val _installed = MutableStateFlow<List<DictionaryInfo>>(emptyList())

    /** In the order they were installed. */
    val installed: StateFlow<List<DictionaryInfo>> = _installed.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

    /** The running and failed downloads by dictionary ID. */
    val downloads: StateFlow<Map<String, Download>> = _downloads.asStateFlow()

    init {
        scope.launch { mutex.withLock { load() } }
    }

    /** Downloads the words of [language] explained in [edition]; replaces the dictionary if it's installed. */
    fun download(edition: Edition, language: CatalogLanguage) {
        val id = dictionaryId(edition, language)
        if (jobs[id]?.isActive == true) return
        setDownload(id, edition, language, DownloadState.Running(indexing = false, bytes = 0, totalBytes = null, wholeEdition = false))
        jobs[id] = scope.launch {
            try {
                install(id, edition, language)
                clearDownload(id)
            } catch (e: CancellationException) {
                // The state is cleared by cancel().
                throw e
            } catch (e: Exception) {
                // Cancelling aborts the connection, which comes as an IOException.
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "Failed to install ${edition.code}/${language.name}", e)
                val error = when (e) {
                    is DownloadException -> e.error
                    is IOException -> DownloadError.NETWORK
                    else -> DownloadError.OTHER
                }
                setDownload(id, edition, language, DownloadState.Failed(error))
            }
        }
    }

    /** Stops a download, or forgets that it failed. */
    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        clearDownload(id)
    }

    fun remove(id: String) {
        cancel(id)
        scope.launch {
            mutex.withLock {
                readers.remove(id)?.close()
                File(dir, id + EXTENSION).delete()
                _installed.update { list -> list.filter { it.id != id } }
            }
        }
    }

    /** What the installed dictionaries have for [text], a word or a phrase; empty if it's too long to be either. */
    suspend fun lookup(text: String): List<DictionaryResult> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LOOKUP_LENGTH) return emptyList()
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val start = SystemClock.elapsedRealtime()
                val results = _installed.value.mapNotNull { info ->
                    val reader = readers[info.id] ?: return@mapNotNull null
                    lookupKeys(trimmed, info.languageCode)
                        .firstNotNullOfOrNull { keys -> reader.lookup(keys).ifEmpty { null } }
                        ?.let { DictionaryResult(info, it) }
                }
                Log.d(TAG, "Looked up \"$trimmed\" in ${SystemClock.elapsedRealtime() - start} ms")
                results
            }
        }
    }

    private fun load() {
        val files = dir.listFiles().orEmpty()
        // Left by a download that the app was closed during.
        files.filter { it.name.endsWith(TEMP_EXTENSION) || it.name.endsWith("$TEMP_EXTENSION-journal") }.forEach { it.delete() }
        _installed.value = files
            .filter { it.name.endsWith(EXTENSION) }
            .mapNotNull { file ->
                val id = file.name.removeSuffix(EXTENSION)
                try {
                    val reader = DictionaryReader(file)
                    readers[id] = reader
                    reader.info(id, file)
                } catch (e: Exception) {
                    Log.e(TAG, "Removing a broken dictionary $file", e)
                    file.delete()
                    null
                }
            }
            .sortedBy { it.created }
    }

    private suspend fun install(id: String, edition: Edition, language: CatalogLanguage) {
        dir.mkdirs()
        val temp = File(dir, id + TEMP_EXTENSION)
        try {
            val languageUrl = Kaikki.languageFileUrl(edition, language)
            val fromLanguageFile = languageUrl != null && try {
                importDictionary(id, edition, languageUrl, language, temp, wholeEdition = false)
                true
            } catch (e: DownloadException) {
                if (e.error != DownloadError.NOT_FOUND) throw e
                false
            }
            if (!fromLanguageFile) importDictionary(id, edition, Kaikki.wholeEditionUrl(edition), language, temp, wholeEdition = true)

            val file = File(dir, id + EXTENSION)
            mutex.withLock {
                readers.remove(id)?.close()
                if (!temp.renameTo(file)) throw IOException("Couldn't rename $temp")
                val reader = DictionaryReader(file)
                readers[id] = reader
                val info = reader.info(id, file)
                _installed.update { list -> list.filter { it.id != id } + info }
            }
        } finally {
            temp.delete()
            File(temp.path + "-journal").delete()
        }
    }

    /** Makes the dictionary file [temp] out of the JSONL at [url]. */
    private suspend fun importDictionary(id: String, edition: Edition, url: String, language: CatalogLanguage, temp: File, wholeEdition: Boolean) {
        temp.delete()
        DictionaryWriter(temp).use { writer ->
            var languageCode = ""
            download(url) { input, counter, total ->
                if (wholeEdition && total != null && total > MAX_WHOLE_EDITION_BYTES) throw DownloadException(DownloadError.TOO_BIG)
                var reported = 0L
                fun report() = setDownload(id, edition, language, DownloadState.Running(indexing = false, counter.count, total, wholeEdition))
                report()
                input.bufferedReader().lineSequence().forEach { line ->
                    // A whole edition has all languages; the check of the text is quicker than parsing.
                    if (wholeEdition && language.name !in line) return@forEach
                    val parsed = Wiktionary.parse(line) ?: return@forEach
                    if (wholeEdition && parsed.language != language.name) return@forEach
                    if (languageCode.isEmpty()) languageCode = parsed.languageCode
                    writer.add(parsed)
                    if (counter.count - reported >= REPORT_EVERY_BYTES) {
                        reported = counter.count
                        report()
                    }
                }
            }
            if (writer.entries == 0) throw DownloadException(DownloadError.EMPTY)
            setDownload(id, edition, language, DownloadState.Running(indexing = true, bytes = 0, totalBytes = null, wholeEdition = wholeEdition))
            writer.finish(
                mapOf(
                    DictionaryMeta.NAME to language.name,
                    DictionaryMeta.EDITION to edition.code,
                    DictionaryMeta.LANGUAGE_CODE to languageCode,
                    DictionaryMeta.ENTRIES to writer.entries.toString(),
                    DictionaryMeta.CREATED to System.currentTimeMillis().toString(),
                    DictionaryMeta.SOURCE to url,
                ),
            )
        }
    }

    /**
     * Runs [block] with the decompressed body of [url]. Gzip is asked for explicitly: the platform would
     * decompress it silently otherwise, and then the downloaded size couldn't be compared to the total.
     */
    private suspend fun download(url: String, block: (input: InputStream, counter: CountingInputStream, totalBytes: Long?) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.abortOnCancel {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept-Encoding", "gzip")
            when (val code = responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> throw DownloadException(DownloadError.NOT_FOUND)
                !in 200..299 -> throw IOException("HTTP $code for $url")
            }
            val counter = CountingInputStream(inputStream)
            val gzipped = contentEncoding == "gzip" || url.endsWith(".gz")
            (if (gzipped) GZIPInputStream(counter, BUFFER_SIZE) else counter).use { input ->
                block(input, counter, contentLengthLong.takeIf { it > 0 })
            }
        }
    }

    private fun setDownload(id: String, edition: Edition, language: CatalogLanguage, state: DownloadState) {
        _downloads.update { it + (id to Download(edition, language, state)) }
    }

    private fun clearDownload(id: String) {
        _downloads.update { it - id }
    }

    private fun DictionaryReader.info(id: String, file: File): DictionaryInfo {
        val editionCode = meta[DictionaryMeta.EDITION].orEmpty()
        return DictionaryInfo(
            id = id,
            name = meta[DictionaryMeta.NAME] ?: id,
            edition = Kaikki.editions.find { it.code == editionCode } ?: Edition(editionCode, editionCode, ""),
            languageCode = meta[DictionaryMeta.LANGUAGE_CODE].orEmpty(),
            entries = meta[DictionaryMeta.ENTRIES]?.toIntOrNull() ?: 0,
            sizeBytes = file.length(),
            created = meta[DictionaryMeta.CREATED]?.toLongOrNull() ?: file.lastModified(),
        )
    }

    private companion object {
        const val TAG = "DictionaryRepository"
        const val EXTENSION = ".db"
        const val TEMP_EXTENSION = ".db.tmp"
        const val TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 1 shl 16
        const val REPORT_EVERY_BYTES = 128L * 1024

        /** The Russian edition is about 300 MB; the English one is 3 GB, too much for a phone. */
        const val MAX_WHOLE_EDITION_BYTES = 600L * 1024 * 1024

        /** Longer texts are sentences, not words or phrases that dictionaries have. */
        const val MAX_LOOKUP_LENGTH = 60
    }
}

private class DownloadException(val error: DownloadError) : Exception(error.name)

/** Counts the bytes read, for the progress of a download. */
private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    @Volatile
    var count = 0L
        private set

    override fun read(): Int = super.read().also { if (it >= 0) count++ }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }

    override fun skip(n: Long): Long = super.skip(n).also { count += it }
}

private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9_-]")
