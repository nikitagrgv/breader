package ru.nikitagrgv.breader.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import ru.nikitagrgv.breader.data.Library
import ru.nikitagrgv.breader.data.LibraryRepository
import ru.nikitagrgv.breader.data.ReaderFont
import ru.nikitagrgv.breader.data.ReadingPosition
import ru.nikitagrgv.breader.data.SeekBarPosition
import ru.nikitagrgv.breader.data.SettingsRepository
import ru.nikitagrgv.breader.data.TranslationSettings
import ru.nikitagrgv.breader.ui.reader.ReaderState
import ru.nikitagrgv.breader.ui.reader.bookId

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

    private val _reader = MutableStateFlow<ReaderState>(ReaderState.Empty)
    val reader: StateFlow<ReaderState> = _reader.asStateFlow()

    private val _events = Channel<LibraryEvent>(Channel.BUFFERED)
    val events: Flow<LibraryEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        // Reopen the book that was being read last time.
        viewModelScope.launch {
            val library = repository.awaitLibrary()
            library.books.find { it.id == library.currentBookId }?.let(::load)
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
            _reader.value = ReaderState.Empty
        }
        _events.trySend(LibraryEvent.BookRemoved(removed, index))
    }

    fun undoRemove(event: LibraryEvent.BookRemoved) = repository.insertBook(event.book, event.index)

    fun savePosition(bookId: String, position: ReadingPosition, progress: Float) =
        repository.updatePosition(bookId, position, progress)

    fun addReadingTime(bookId: String, ms: Long) = repository.addReadingTime(bookId, ms)

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

    fun setSeekBarPosition(position: SeekBarPosition) {
        viewModelScope.launch { settingsRepository.setSeekBarPosition(position) }
    }

    fun updateTranslationSettings(transform: (TranslationSettings) -> TranslationSettings) {
        viewModelScope.launch { settingsRepository.updateTranslation(transform) }
    }

    private fun load(book: Book) {
        loadJob?.cancel()
        _reader.value = ReaderState.Loading(book.id, book.title)
        loadJob = viewModelScope.launch {
            val content = withContext(Dispatchers.IO) {
                try {
                    files.readContent(book)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open ${book.uri}", e)
                    null
                }
            }
            // The passed book may be stale by now, take the latest saved position.
            val position = library.value?.books?.find { it.id == book.id }?.position ?: book.position
            _reader.value = if (content != null) {
                ReaderState.Ready(book.id, book.title, content, position)
            } else {
                ReaderState.Failed(book.id, book.title)
            }
        }
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
