package ru.nikitagrgv.breader.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nikitagrgv.breader.BreaderApplication
import ru.nikitagrgv.breader.data.AppSettings
import ru.nikitagrgv.breader.data.AppTheme
import ru.nikitagrgv.breader.data.Book
import ru.nikitagrgv.breader.data.BookFiles
import ru.nikitagrgv.breader.data.DictionarySettings
import ru.nikitagrgv.breader.data.Library
import ru.nikitagrgv.breader.data.LibraryRepository
import ru.nikitagrgv.breader.data.ReaderFont
import ru.nikitagrgv.breader.data.ReadingPosition
import ru.nikitagrgv.breader.data.SeekBarPosition
import ru.nikitagrgv.breader.data.SettingsRepository
import ru.nikitagrgv.breader.data.TranslationSettings
import ru.nikitagrgv.breader.format.BookContent
import ru.nikitagrgv.breader.format.BookFormat
import ru.nikitagrgv.breader.format.BookWords
import ru.nikitagrgv.breader.format.PdfBook
import ru.nikitagrgv.breader.format.countWords
import ru.nikitagrgv.breader.ui.reader.ReaderState
import ru.nikitagrgv.breader.ui.reader.SelectionHistoryState
import ru.nikitagrgv.breader.ui.reader.bookId
import java.io.Closeable

sealed interface LibraryEvent {
    data class ImportFinished(val failed: List<String>, val duplicates: List<String>) : LibraryEvent
    data class BookRemoved(val book: Book, val index: Int) : LibraryEvent
}

class MainViewModel(
    private val repository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val files: BookFiles,
) : ViewModel() {

    val library: StateFlow<Library?> = repository.library

    /** `null` until loaded; the UI waits for it to avoid flashing the wrong theme. */
    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _reader = MutableStateFlow<ReaderState>(ReaderState.Starting)
    val reader: StateFlow<ReaderState> = _reader.asStateFlow()

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    /** Counts the words of the open PDF, whose pages are read one by one. */
    private var pdfWordsJob: Job? = null

    init {
        // Reopen the book that was being read last time.
        viewModelScope.launch {
            val library = repository.awaitLibrary()
            val book = library.books.find { it.id == library.currentBookId }
            if (book != null) load(book) else setReader(ReaderState.Empty)
            countMissingWords(library.books)
        }
    }

    fun addBooks(uris: List<Uri>) {
        val known = library.value?.books?.mapTo(HashSet()) { it.uri } ?: return
        viewModelScope.launch {
            val added = ArrayList<Book>()
            val failed = ArrayList<String>()
            val duplicates = ArrayList<String>()
            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    if (uri in known) {
                        duplicates += files.displayName(uri) ?: uri.toString()
                        continue
                    }
                    try {
                        added += files.importBook(uri)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to import $uri", e)
                        failed += files.displayName(uri) ?: uri.toString()
                    }
                }
            }
            repository.addBooks(added)
            countMissingWords(added)
            if (failed.isNotEmpty() || duplicates.isNotEmpty()) {
                _events.send(LibraryEvent.ImportFinished(failed, duplicates))
            }
        }
    }

    fun openBook(book: Book) {
        val current = _reader.value
        if (current.bookId == book.id && current !is ReaderState.Failed) return
        repository.setCurrentBook(book.id)
        load(book)
    }

    fun removeBook(book: Book) {
        val (index, removed) = repository.removeBook(book.id) ?: return
        if (_reader.value.bookId == book.id) {
            loadJob?.cancel()
            setReader(ReaderState.Empty)
        }
        _events.trySend(LibraryEvent.BookRemoved(removed, index))
    }

    fun undoRemove(event: LibraryEvent.BookRemoved) = repository.insertBook(event.book, event.index)

    fun setFavorite(book: Book, favorite: Boolean) = repository.setFavorite(book.id, favorite)

    /** See [LibraryRepository.reorderBooks]. */
    fun reorderBooks(ids: List<String>) = repository.reorderBooks(ids)

    fun savePosition(bookId: String, position: ReadingPosition, progress: Float) =
        repository.updatePosition(bookId, position, progress)

    fun addReadingTime(bookId: String, ms: Long, percent: Int?) = repository.addReadingTime(bookId, ms, percent)

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun setFontSize(size: Float) {
        viewModelScope.launch { settingsRepository.setFontSize(size) }
    }

    fun setFont(font: ReaderFont) {
        viewModelScope.launch { settingsRepository.setFont(font) }
    }

    fun setLineSpacing(spacing: Float) {
        viewModelScope.launch { settingsRepository.setLineSpacing(spacing) }
    }

    fun setMargins(margins: Int) {
        viewModelScope.launch { settingsRepository.setMargins(margins) }
    }

    fun setJustify(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setJustify(enabled) }
    }

    fun setHyphenation(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHyphenation(enabled) }
    }

    fun setMoreSelectionActions(open: Boolean) {
        viewModelScope.launch { settingsRepository.setMoreSelectionActions(open) }
    }

    fun setSelectionFade(ms: Int) {
        viewModelScope.launch { settingsRepository.setSelectionFade(ms) }
    }

    fun setSelectionHistorySize(size: Int) {
        viewModelScope.launch { settingsRepository.setSelectionHistorySize(size) }
    }

    fun setSeekBarPosition(position: SeekBarPosition) {
        viewModelScope.launch { settingsRepository.setSeekBarPosition(position) }
    }

    fun updateTranslationSettings(transform: (TranslationSettings) -> TranslationSettings) {
        viewModelScope.launch { settingsRepository.updateTranslation(transform) }
    }

    fun updateDictionarySettings(transform: (DictionarySettings) -> DictionarySettings) {
        viewModelScope.launch { settingsRepository.updateDictionaries(transform) }
    }

    override fun onCleared() {
        closeContent(_reader.value)
    }

    private fun load(book: Book) {
        loadJob?.cancel()
        setReader(ReaderState.Loading(book.id, book.title))
        loadJob = viewModelScope.launch {
            var content: BookContent? = null
            var words: BookWords? = null
            try {
                withContext(Dispatchers.IO) {
                    try {
                        val read = files.readContent(book)
                        content = read
                        // A PDF's pages are read as they're shown; its words are counted later.
                        if (read !is PdfBook) words = BookWords.of(read.paragraphs)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to open ${book.uri}", e)
                    }
                }
            } catch (e: CancellationException) {
                // Another book was opened in the meantime, and nobody else knows about this one.
                (content as? Closeable)?.close()
                throw e
            }
            // The passed book may be stale by now, take what was saved last.
            val latest = library.value?.books?.find { it.id == book.id } ?: book
            val history = SelectionHistoryState(latest.selectionHistory) { repository.updateSelectionHistory(book.id, it) }
            setReader(
                content?.let { ReaderState.Ready(book.id, book.title, it, latest.position, history, words) }
                    ?: ReaderState.Failed(book.id, book.title),
            )
            words?.let { if (it.total != latest.wordCount) repository.setWordCount(book.id, it.total) }
            (content as? PdfBook)?.let { if (latest.wordCount == null) countPdfWords(book.id, it) }
        }
    }

    /**
     * Counts the words of a PDF page by page, in the background: reading all of its pages takes a few seconds. The pages
     * read are kept, so searching the book afterwards is quick. Stopped when the PDF is closed.
     */
    private fun countPdfWords(bookId: String, book: PdfBook) {
        pdfWordsJob?.cancel()
        pdfWordsJob = viewModelScope.launch {
            var total = 0
            for (page in book.pageSizes.indices) {
                // A page that can't be read counts as one without text, like a scan.
                val text = book.pageText(page) ?: continue
                total += withContext(Dispatchers.Default) { countWords(text) }
            }
            repository.setWordCount(bookId, total)
        }
    }

    /**
     * Counts the words of the [books] that aren't counted yet, one at a time in the background, so that the library
     * shows them without opening each book. PDFs are left until they're opened: PDFium can only read one at a time.
     */
    private fun countMissingWords(books: List<Book>) {
        val uncounted = books.filter { it.wordCount == null && it.format != BookFormat.PDF }
        if (uncounted.isEmpty()) return
        viewModelScope.launch {
            for (book in uncounted) {
                val count = withContext(Dispatchers.IO) {
                    try {
                        BookWords.of(files.readContent(book).paragraphs).total
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to count the words of ${book.uri}", e)
                        null
                    }
                }
                // Removed in the meantime, or counted by being opened.
                if (count != null && library.value?.books?.find { it.id == book.id }?.wordCount == null) {
                    repository.setWordCount(book.id, count)
                }
            }
        }
    }

    private fun setReader(state: ReaderState) {
        val previous = _reader.value
        if ((previous as? ReaderState.Ready)?.content !== (state as? ReaderState.Ready)?.content) pdfWordsJob?.cancel()
        _reader.value = state
        closeContent(previous)
    }

    /** A PDF stays open while it's read. */
    private fun closeContent(state: ReaderState) {
        ((state as? ReaderState.Ready)?.content as? Closeable)?.close()
    }

    companion object {
        private const val TAG = "MainViewModel"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BreaderApplication
                MainViewModel(app.libraryRepository, app.settingsRepository, app.bookFiles)
            }
        }
    }
}
