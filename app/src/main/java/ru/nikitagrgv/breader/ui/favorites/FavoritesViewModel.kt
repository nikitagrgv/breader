package ru.nikitagrgv.breader.ui.favorites

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.BreaderApplication
import ru.nikitagrgv.breader.data.Favorite
import ru.nikitagrgv.breader.data.FavoriteKind
import ru.nikitagrgv.breader.data.FavoritePlace
import ru.nikitagrgv.breader.data.FavoritesRepository
import ru.nikitagrgv.breader.data.MachineText
import ru.nikitagrgv.breader.data.TranslationSettings
import ru.nikitagrgv.breader.data.favoriteKey
import ru.nikitagrgv.breader.dictionary.Dictionaries
import ru.nikitagrgv.breader.dictionary.dictionaryKey
import ru.nikitagrgv.breader.dictionary.meaningsLine
import ru.nikitagrgv.breader.translation.Excerpt
import ru.nikitagrgv.breader.translation.MachineTranslator
import ru.nikitagrgv.breader.translation.Translation
import ru.nikitagrgv.breader.translation.TranslationCache
import ru.nikitagrgv.breader.translation.TranslationKey
import ru.nikitagrgv.breader.ui.translation.ModelResult
import ru.nikitagrgv.breader.ui.translation.ModelTranslator
import ru.nikitagrgv.breader.ui.translation.TranslationError
import ru.nikitagrgv.breader.ui.translation.toTranslationError

/**
 * Where a text to keep comes from.
 * @param excerpt the text with what's around it, which the models translate it in.
 * @param place where it's selected in a book; null if it isn't selected in one, e.g. when it's typed.
 */
data class FavoriteOrigin(val excerpt: Excerpt, val place: FavoritePlace?)

/** Where the favorite was kept from, for keeping it with something else too; null if it was typed. */
val Favorite.origin: FavoriteOrigin?
    get() = if (context == null && place == null) null else FavoriteOrigin(context ?: Excerpt("", word, ""), place)

sealed interface FavoriteResult {
    /**
     * @param kind what it's kept with now; null if nothing could be had, and it's kept as it is.
     * @param wanted the kind asked for; null if it was the best there is.
     */
    data class Saved(val kind: FavoriteKind?, val wanted: FavoriteKind?) : FavoriteResult

    data object NotInDictionaries : FavoriteResult

    /** There's no API key or no models to ask. */
    data object ModelsNotSetUp : FavoriteResult

    data class TranslationFailed(val error: TranslationError) : FavoriteResult
}

/**
 * The favorite words and phrases. One is kept with what tells its meaning: the meanings from the dictionaries, the
 * translation by the models, and the one by a free service, any of them.
 */
class FavoritesViewModel(
    private val repository: FavoritesRepository,
    private val dictionaries: Dictionaries,
    private val translator: ModelTranslator,
    private val cache: TranslationCache,
) : ViewModel() {
    val favorites: StateFlow<List<Favorite>> = repository.favorites

    /** What each kept text is kept with, by its key; a text that isn't kept isn't there. */
    val kinds: StateFlow<Map<String, Set<FavoriteKind>>> = repository.favorites
        .map { list -> list.associate { it.key to it.kinds } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _saving = MutableStateFlow<Set<String>>(emptySet())

    /** The keys of the texts being kept, which may take a while: the models may be asked. */
    val saving: StateFlow<Set<String>> = _saving.asStateFlow()

    /**
     * Keeps [text] with the [kind] asked for; if it's kept already, that's added to it. Without a kind, it's kept with the
     * best there is, of the meanings from the dictionaries, the models' translation (a cached one if there is), the
     * translation by a free service, and nothing, in this order.
     * @param origin where it comes from; null if it's typed.
     */
    fun add(
        text: String,
        origin: FavoriteOrigin?,
        kind: FavoriteKind?,
        settings: TranslationSettings,
        onResult: (FavoriteResult) -> Unit,
    ) {
        val key = favoriteKey(text)
        if (key.isEmpty() || key in _saving.value) return
        _saving.update { it + key }
        viewModelScope.launch {
            try {
                val (result, found) = find(text, key, origin, kind, settings)
                if (found != null) repository.update(key) { kept -> kept?.merge(found) ?: found }
                onResult(result)
            } finally {
                _saving.update { it - key }
            }
        }
    }

    fun remove(keys: Set<String>) = repository.remove(keys)

    /** Keeps the favorite without what it's kept with as [kind]; it's removed if it's left with nothing. */
    fun remove(key: String, kind: FavoriteKind) = repository.update(key) { kept ->
        kept?.without(kind)?.takeIf { it.kinds.isNotEmpty() }
    }

    /** What [text] is to be kept with: a favorite with just that, or null if what's asked for can't be had. */
    private suspend fun find(
        text: String,
        key: String,
        origin: FavoriteOrigin?,
        kind: FavoriteKind?,
        settings: TranslationSettings,
    ): Pair<FavoriteResult, Favorite?> {
        // A phrase may run over lines; around it, there may be punctuation, as around a word.
        val shown = text.trim { !it.isLetterOrDigit() }.replace(WHITESPACE, " ")
        val excerpt = origin?.excerpt ?: Excerpt("", shown, "")
        val bare = Favorite(key, shown, System.currentTimeMillis(), context = origin?.excerpt, place = origin?.place)
        fun saved(favorite: Favorite, found: FavoriteKind?) = FavoriteResult.Saved(found, kind) to favorite

        if (kind == null || kind == FavoriteKind.DICTIONARY) {
            val results = dictionaries.lookupAll(text)
            val meanings = results.meaningsLine()
            if (meanings != null) {
                // The dictionary's spelling, e.g. "push" for "Push" at the start of a sentence, unless it's another word.
                val word = results.flatMap { it.entries }.firstOrNull { dictionaryKey(it.word) == key }?.word ?: shown
                return saved(bare.copy(word = word, meanings = meanings), FavoriteKind.DICTIONARY)
            }
            if (kind == FavoriteKind.DICTIONARY) return FavoriteResult.NotInDictionaries to null
        }
        if (kind == null || kind == FavoriteKind.AI) {
            when (val translated = translateByModels(excerpt, settings)) {
                is ModelsOutcome.Done -> return saved(bare.copy(translations = translated.translations), FavoriteKind.AI)
                is ModelsOutcome.Failed -> if (kind == FavoriteKind.AI) return translated.result to null
            }
        }
        if (kind == null || kind == FavoriteKind.MACHINE) {
            val error = try {
                val translation = MachineTranslator.translate(settings.machineService, excerpt.fragment, settings.sourceCode, settings.targetCode)
                if (translation.text.isNotBlank()) {
                    return saved(bare.copy(machine = MachineText(translation.text, settings.machineService)), FavoriteKind.MACHINE)
                }
                TranslationError(TranslationError.Kind.BAD_REPLY)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Machine translation of \"$text\" failed", e)
                e.toTranslationError()
            }
            if (kind == FavoriteKind.MACHINE) return FavoriteResult.TranslationFailed(error) to null
        }
        return saved(bare, found = null)
    }

    private sealed interface ModelsOutcome {
        /** @param translations pairs of a model and its translation, in the order of the settings. */
        data class Done(val translations: List<Pair<String, Translation>>) : ModelsOutcome
        data class Failed(val result: FavoriteResult) : ModelsOutcome
    }

    /**
     * The translations the models have cached for [excerpt], if there are any: asking the rest would cost money and
     * time for little more. Otherwise, all the models are asked, and whatever they give is taken.
     */
    private suspend fun translateByModels(excerpt: Excerpt, settings: TranslationSettings): ModelsOutcome {
        if (!settings.isConfigured) return ModelsOutcome.Failed(FavoriteResult.ModelsNotSetUp)
        val key = TranslationKey(excerpt, settings.sourceLanguage, settings.effectiveTargetLanguage)
        val cached = cache.get(key)
        val fromCache = settings.models.mapNotNull { model -> cached[model]?.let { model to it } }
        if (fromCache.isNotEmpty()) return ModelsOutcome.Done(fromCache)
        val results = coroutineScope {
            settings.models.map { model -> async { model to translator.translate(key, model, settings) } }.awaitAll()
        }
        val done = results.mapNotNull { (model, result) -> (result as? ModelResult.Done)?.let { model to it.translation } }
        if (done.isNotEmpty()) return ModelsOutcome.Done(done)
        val error = results.firstNotNullOfOrNull { (_, result) -> (result as? ModelResult.Failed)?.error }
            ?: TranslationError(TranslationError.Kind.OTHER)
        return ModelsOutcome.Failed(FavoriteResult.TranslationFailed(error))
    }

    companion object {
        private const val TAG = "FavoritesViewModel"
        private val WHITESPACE = Regex("\\s+")

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BreaderApplication
                FavoritesViewModel(app.favoritesRepository, app.dictionaries, app.modelTranslator, app.translationCache)
            }
        }
    }
}
