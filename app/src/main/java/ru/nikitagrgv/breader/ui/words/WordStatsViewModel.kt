package ru.nikitagrgv.breader.ui.words

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.nikitagrgv.breader.BreaderApplication
import ru.nikitagrgv.breader.data.Book
import ru.nikitagrgv.breader.data.BookFiles
import ru.nikitagrgv.breader.dictionary.Dictionaries
import ru.nikitagrgv.breader.dictionary.meaningsLine
import ru.nikitagrgv.breader.format.BookContent
import ru.nikitagrgv.breader.format.BookFormat
import ru.nikitagrgv.breader.format.CommonWords
import ru.nikitagrgv.breader.format.PdfBook
import ru.nikitagrgv.breader.format.WordCount
import ru.nikitagrgv.breader.format.WordCounter
import ru.nikitagrgv.breader.format.WordFrequency

sealed interface WordStatsState {
    val bookId: String

    /** @param progress 0..1, if it's known: a PDF is read page by page. */
    data class Counting(override val bookId: String, val progress: Float? = null) : WordStatsState

    /** @param commonWords those of the book's language; null if they aren't known for it. */
    data class Ready(override val bookId: String, val frequency: WordFrequency, val commonWords: CommonWords?) : WordStatsState

    data class Failed(override val bookId: String, val reason: WordStatsFailure) : WordStatsState
}

enum class WordStatsFailure {
    /** A PDF is only read while it's open in the reader: PDFium can only read one at a time. */
    PDF_NOT_OPEN,
    UNREADABLE,
}

/** Counts the words of a book, and looks them up in the downloaded dictionaries, a page of them at a time. */
class WordStatsViewModel(private val files: BookFiles, private val dictionaries: Dictionaries) : ViewModel() {
    private val _state = MutableStateFlow<WordStatsState?>(null)

    /** The words of the last book they were counted for; they're kept, so it shows them at once next time. */
    val state: StateFlow<WordStatsState?> = _state.asStateFlow()
    private var countJob: Job? = null

    private val _meanings = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The meanings of the words looked up so far, by [WordCount.key]; empty for the ones the dictionaries don't have. */
    val meanings: StateFlow<Map<String, String>> = _meanings.asStateFlow()
    private var meaningsJob: Job? = null

    /**
     * Counts the words of [book], unless they're counted, or being counted, already.
     * @param openContent the book as it's open in the reader, if it is: it isn't read again then, and a PDF can only
     * be read then.
     */
    fun count(book: Book, openContent: BookContent?) {
        val current = _state.value
        if (current != null && current.bookId == book.id && current !is WordStatsState.Failed) return
        countJob?.cancel()
        if (book.format == BookFormat.PDF && openContent !is PdfBook) {
            _state.value = WordStatsState.Failed(book.id, WordStatsFailure.PDF_NOT_OPEN)
            return
        }
        _state.value = WordStatsState.Counting(book.id)
        countJob = viewModelScope.launch {
            _state.value = try {
                val frequency = when (openContent) {
                    is PdfBook -> countPages(book.id, openContent)
                    null -> {
                        val content = withContext(Dispatchers.IO) { files.readContent(book) }
                        withContext(Dispatchers.Default) { WordFrequency.of(content.paragraphs) }
                    }
                    else -> withContext(Dispatchers.Default) { WordFrequency.of(openContent.paragraphs) }
                }
                WordStatsState.Ready(book.id, frequency, withContext(Dispatchers.Default) { CommonWords.of(frequency) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to count the words of ${book.uri}", e)
                WordStatsState.Failed(book.id, WordStatsFailure.UNREADABLE)
            }
        }
    }

    /**
     * Looks up the [words] that aren't looked up yet, a moment later: the pages may be flipped through, and the words
     * of the ones passed by aren't needed.
     */
    fun loadMeanings(words: List<WordCount>) {
        meaningsJob?.cancel()
        meaningsJob = viewModelScope.launch {
            delay(MEANINGS_DELAY_MS)
            for (word in words) {
                if (word.key in _meanings.value) continue
                val meanings = try {
                    dictionaries.lookupDownloaded(word.word).meaningsLine().orEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to look up \"${word.word}\"", e)
                    ""
                }
                _meanings.update { it + (word.key to meanings) }
            }
        }
    }

    /**
     * Stops what's no longer needed when the words are closed. Counting that isn't done is stopped, while the counted
     * words are kept. The meanings aren't: the dictionaries may change until next time.
     */
    fun close() {
        meaningsJob?.cancel()
        _meanings.value = emptyMap()
        if (_state.value !is WordStatsState.Ready) {
            countJob?.cancel()
            _state.value = null
        }
    }

    /** The pages that can't be read count as ones without text, like scans. */
    private suspend fun countPages(bookId: String, book: PdfBook): WordFrequency {
        val counter = WordCounter()
        val pages = book.pageSizes.size
        for (page in 0 until pages) {
            book.pageText(page)?.let { text -> withContext(Dispatchers.Default) { counter.add(text) } }
            _state.value = WordStatsState.Counting(bookId, (page + 1f) / pages)
        }
        return withContext(Dispatchers.Default) { counter.result() }
    }

    companion object {
        private const val TAG = "WordStatsViewModel"
        private const val MEANINGS_DELAY_MS = 200L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BreaderApplication
                WordStatsViewModel(app.bookFiles, app.dictionaries)
            }
        }
    }
}
