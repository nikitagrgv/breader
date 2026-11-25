package ru.nikitagrgv.breader.ui.dictionary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.BreaderApplication
import ru.nikitagrgv.breader.dictionary.CatalogLanguage
import ru.nikitagrgv.breader.dictionary.Dictionaries
import ru.nikitagrgv.breader.dictionary.DictionaryInfo
import ru.nikitagrgv.breader.dictionary.DictionaryRepository
import ru.nikitagrgv.breader.dictionary.DictionaryResult
import ru.nikitagrgv.breader.dictionary.Download
import ru.nikitagrgv.breader.dictionary.Edition
import ru.nikitagrgv.breader.dictionary.Kaikki
import ru.nikitagrgv.breader.dictionary.LookupFailure
import ru.nikitagrgv.breader.dictionary.OnlineDictionary
import ru.nikitagrgv.breader.ui.favorites.FavoriteOrigin

sealed interface LanguageList {
    data object Loading : LanguageList
    data object Failed : LanguageList
    data class Loaded(val languages: List<CatalogLanguage>) : LanguageList
}

/**
 * @param text what's looked up; empty when the user is yet to type it.
 * @param results null until the downloaded dictionaries are looked in, which is a moment.
 * @param pending the online dictionaries that are still being asked.
 * @param failed the online dictionaries that couldn't be asked.
 * @param origin where the text comes from, for keeping it in the favorites; null if it's typed.
 */
data class DictionaryLookup(
    val text: String,
    val results: List<DictionaryResult>?,
    val pending: List<OnlineDictionary> = emptyList(),
    val failed: Map<OnlineDictionary, LookupFailure> = emptyMap(),
    val origin: FavoriteOrigin? = null,
)

/** The dictionary manager and the detailed lookup of the selected text, or of a word the user types. */
class DictionaryViewModel(private val repository: DictionaryRepository, private val dictionaries: Dictionaries) : ViewModel() {
    val installed: StateFlow<List<DictionaryInfo>> = repository.installed
    val downloads: StateFlow<Map<String, Download>> = repository.downloads

    private val _languages = MutableStateFlow<Map<String, LanguageList>>(emptyMap())

    /** The languages of each edition that was asked for, by its code. Kept while the activity lives. */
    val languages: StateFlow<Map<String, LanguageList>> = _languages.asStateFlow()

    private val _lookup = MutableStateFlow<DictionaryLookup?>(null)

    /** Null when nothing is being looked up. */
    val lookup: StateFlow<DictionaryLookup?> = _lookup.asStateFlow()
    private var lookupJob: Job? = null

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())

    /** Words of the downloaded dictionaries that start with what's being typed. */
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()
    private var suggestionsJob: Job? = null

    /** Loads the languages of [edition], unless they're loaded already or being loaded. */
    fun loadLanguages(edition: Edition) {
        val current = _languages.value[edition.code]
        if (current is LanguageList.Loaded || current == LanguageList.Loading) return
        _languages.update { it + (edition.code to LanguageList.Loading) }
        viewModelScope.launch {
            val result = try {
                LanguageList.Loaded(Kaikki.languages(edition))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load the languages of ${edition.code}", e)
                LanguageList.Failed
            }
            _languages.update { it + (edition.code to result) }
        }
    }

    fun download(edition: Edition, language: CatalogLanguage) = repository.download(edition, language)

    fun cancel(id: String) = repository.cancel(id)

    fun remove(id: String) = repository.remove(id)

    /**
     * Shows what the dictionaries have for [text]; a blank one opens the lookup for the user to type a word.
     * @param origin see [DictionaryLookup.origin].
     */
    fun lookUp(text: String, origin: FavoriteOrigin? = null) {
        lookupJob?.cancel()
        if (text.isBlank()) {
            _lookup.value = DictionaryLookup("", results = emptyList())
            return
        }
        _lookup.value = DictionaryLookup(text, results = null, origin = origin)
        lookupJob = viewModelScope.launch {
            dictionaries.lookup(text).collect { progress ->
                _lookup.update { it?.copy(results = progress.results, pending = progress.pending, failed = progress.failed) }
            }
        }
    }

    /** Finds the words that start with [prefix], a moment later: they're not needed for every letter typed in a row. */
    fun suggest(prefix: String) {
        suggestionsJob?.cancel()
        if (prefix.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestionsJob = viewModelScope.launch {
            delay(SUGGESTIONS_DELAY_MS)
            _suggestions.value = dictionaries.wordsStartingWith(prefix, MAX_SUGGESTIONS)
        }
    }

    fun dismissLookup() {
        lookupJob?.cancel()
        suggestionsJob?.cancel()
        _lookup.value = null
        _suggestions.value = emptyList()
    }

    companion object {
        private const val TAG = "DictionaryViewModel"
        private const val SUGGESTIONS_DELAY_MS = 150L
        private const val MAX_SUGGESTIONS = 30

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BreaderApplication
                DictionaryViewModel(app.dictionaryRepository, app.dictionaries)
            }
        }
    }
}
