package ru.nikitagrgv.breader.ui.dictionary

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.BreaderApplication
import ru.nikitagrgv.breader.dictionary.CatalogLanguage
import ru.nikitagrgv.breader.dictionary.DictionaryInfo
import ru.nikitagrgv.breader.dictionary.DictionaryRepository
import ru.nikitagrgv.breader.dictionary.DictionaryResult
import ru.nikitagrgv.breader.dictionary.Download
import ru.nikitagrgv.breader.dictionary.Edition
import ru.nikitagrgv.breader.dictionary.Kaikki

sealed interface LanguageList {
    data object Loading : LanguageList
    data object Failed : LanguageList
    data class Loaded(val languages: List<CatalogLanguage>) : LanguageList
}

/** @param results null while looking. */
data class DictionaryLookup(val text: String, val results: List<DictionaryResult>?)

/** The dictionary manager and the detailed lookup of the selected text. */
class DictionaryViewModel(private val repository: DictionaryRepository) : ViewModel() {
    val installed: StateFlow<List<DictionaryInfo>> = repository.installed
    val downloads: StateFlow<Map<String, Download>> = repository.downloads

    private val _languages = MutableStateFlow<Map<String, LanguageList>>(emptyMap())

    /** The languages of each edition that was asked for, by its code. Kept while the activity lives. */
    val languages: StateFlow<Map<String, LanguageList>> = _languages.asStateFlow()

    private val _lookup = MutableStateFlow<DictionaryLookup?>(null)

    /** Null when nothing is being looked up. */
    val lookup: StateFlow<DictionaryLookup?> = _lookup.asStateFlow()
    private var lookupJob: Job? = null

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

    fun lookUp(text: String) {
        lookupJob?.cancel()
        _lookup.value = DictionaryLookup(text, null)
        lookupJob = viewModelScope.launch {
            val results = repository.lookup(text)
            _lookup.update { it?.copy(results = results) }
        }
    }

    fun dismissLookup() {
        lookupJob?.cancel()
        _lookup.value = null
    }

    companion object {
        private const val TAG = "DictionaryViewModel"

        val Factory = viewModelFactory {
            initializer { DictionaryViewModel((this[APPLICATION_KEY] as BreaderApplication).dictionaryRepository) }
        }
    }
}
