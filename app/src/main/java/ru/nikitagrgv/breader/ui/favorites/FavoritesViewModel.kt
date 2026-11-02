package ru.nikitagrgv.breader.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.BreaderApplication
import ru.nikitagrgv.breader.data.Favorite
import ru.nikitagrgv.breader.data.FavoritesRepository
import ru.nikitagrgv.breader.data.favoriteKey
import ru.nikitagrgv.breader.dictionary.DictionaryRepository
import ru.nikitagrgv.breader.dictionary.dictionaryKey
import ru.nikitagrgv.breader.dictionary.meaningsLine

/** Favorites are words of the dictionaries: they're kept with their meanings. */
class FavoritesViewModel(
    private val repository: FavoritesRepository,
    private val dictionaries: DictionaryRepository,
) : ViewModel() {
    val favorites: StateFlow<List<Favorite>> = repository.favorites

    /** The keys of [favorites], to tell quickly whether a word is one of them. */
    val keys: StateFlow<Set<String>> = repository.favorites
        .map { list -> list.mapTo(HashSet()) { it.key } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Adds the selected [text] with its meanings; [onNotFound] is called instead if the dictionaries have nothing for it. */
    fun add(text: String, onNotFound: () -> Unit) {
        viewModelScope.launch {
            val results = dictionaries.lookup(text)
            val brief = results.meaningsLine() ?: return@launch onNotFound()
            val key = favoriteKey(text)
            // The dictionary's spelling, e.g. "push" for "Push" at the start of a sentence, unless it's another word.
            val word = results.flatMap { it.entries }.firstOrNull { dictionaryKey(it.word) == key }?.word
                ?: text.trim { !it.isLetterOrDigit() }
            repository.add(Favorite(key, word, brief, System.currentTimeMillis()))
        }
    }

    fun remove(keys: Set<String>) = repository.remove(keys)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BreaderApplication
                FavoritesViewModel(app.favoritesRepository, app.dictionaryRepository)
            }
        }
    }
}
