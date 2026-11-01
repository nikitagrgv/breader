package ru.nikitagrgv.breader.ui.translation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.BreaderApplication
import ru.nikitagrgv.breader.data.TranslationSettings
import ru.nikitagrgv.breader.dictionary.DictionaryRepository
import ru.nikitagrgv.breader.dictionary.DictionaryResult
import ru.nikitagrgv.breader.translation.Excerpt
import ru.nikitagrgv.breader.translation.OpenRouter
import ru.nikitagrgv.breader.translation.OpenRouterException
import ru.nikitagrgv.breader.translation.Translation
import ru.nikitagrgv.breader.translation.TranslationCache
import ru.nikitagrgv.breader.translation.TranslationKey
import ru.nikitagrgv.breader.translation.TranslationPrompt
import java.io.IOException
import java.net.SocketTimeoutException

sealed interface ModelResult {
    data class Done(val translation: Translation) : ModelResult
    data class Failed(val error: TranslationError) : ModelResult
}

/** @param details the server's message, for errors that have no description of their own. */
data class TranslationError(val kind: Kind, val details: String? = null) {
    enum class Kind { INVALID_KEY, NO_CREDITS, RATE_LIMITED, TIMEOUT, NETWORK, BAD_REPLY, OTHER }
}

data class TranslationState(
    val excerpt: Excerpt,
    /** False if there's no API key or no models to ask. */
    val configured: Boolean,
    /** The asked models, in the order of the settings. */
    val models: List<String>,
    /** Results by model, in the order they came; the models that have none yet are waited for. */
    val results: Map<String, ModelResult>,
    /** What the downloaded dictionaries have for the fragment. */
    val dictionary: List<DictionaryResult> = emptyList(),
) {
    val pending: List<String>
        get() = models.filter { it !in results }

    /** All the meanings from the dictionaries in one line; null if they have nothing. */
    val dictionaryLine: String?
        get() = dictionary
            .flatMap { result -> result.entries.flatMap { entry -> entry.senses.map { it.gloss } } }
            .distinct()
            .joinToString("; ")
            .ifEmpty { null }
}

/**
 * Translates the selected text: briefly while it's selected ([preview]) and in detail on request ([sheet]).
 * Both show the same translation, so opening the sheet for the selection doesn't ask the models again.
 * All the chosen models are asked at once; each result is shown as soon as it comes, and the successful
 * ones are cached.
 */
class TranslationViewModel(
    private val openRouter: OpenRouter,
    private val cache: TranslationCache,
    private val dictionaries: DictionaryRepository,
) : ViewModel() {
    private val state = MutableStateFlow<TranslationState?>(null)

    /** Whether [state] is of the current selection. */
    private val selected = MutableStateFlow(false)
    private val sheetOpen = MutableStateFlow(false)

    /**
     * The translation of the selected text; null when nothing is selected, or when there's nothing to show:
     * translation isn't set up and the dictionaries have nothing.
     */
    val preview: StateFlow<TranslationState?> = combine(state, selected) { state, selected ->
        state?.takeIf { selected && (it.configured || it.dictionary.isNotEmpty()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The translation shown in detail; null when it isn't asked for. */
    val sheet: StateFlow<TranslationState?> = combine(state, sheetOpen) { state, open ->
        state?.takeIf { open }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The number of cached fragments. */
    val cacheSize: StateFlow<Int> = cache.size

    private var settings = TranslationSettings()
    private var key: TranslationKey? = null

    /** The parent of the current translation's requests. */
    private var requests: Job = Job()

    /** A selection that is waited on to settle, so that adjusting a selection doesn't ask the models every moment. */
    private var pending: Pair<Excerpt, TranslationSettings>? = null
    private var pendingJob: Job? = null

    /**
     * To be called on every change of the selection; [excerpt] is null when it's cleared. Until the new
     * selection settles, the translation of the previous one stays.
     */
    fun select(excerpt: Excerpt?, settings: TranslationSettings) {
        val selection = excerpt?.let { it to settings }
        if (selection != null && selection == pending) return
        cancelPending()
        when {
            excerpt == null -> {
                selected.value = false
                if (!sheetOpen.value) stop()
            }
            isCurrent(excerpt, settings) -> selected.value = true
            else -> {
                pending = selection
                pendingJob = viewModelScope.launch {
                    delay(SELECTION_DELAY_MS)
                    pending = null
                    start(excerpt, settings)
                    selected.value = true
                }
            }
        }
    }

    /** Shows the translation in detail; the one of the preview if it's of the same [excerpt]. */
    fun openSheet(excerpt: Excerpt, settings: TranslationSettings) {
        cancelPending()
        if (!isCurrent(excerpt, settings)) start(excerpt, settings)
        sheetOpen.value = true
    }

    fun dismissSheet() {
        sheetOpen.value = false
        if (!selected.value) stop()
    }

    fun retry(model: String) = request(model)

    fun clearCache() {
        viewModelScope.launch { cache.clear() }
    }

    private fun isCurrent(excerpt: Excerpt, settings: TranslationSettings): Boolean =
        state.value?.excerpt == excerpt && this.settings == settings

    private fun cancelPending() {
        pendingJob?.cancel()
        pendingJob = null
        pending = null
    }

    private fun start(excerpt: Excerpt, settings: TranslationSettings) {
        stop()
        requests = SupervisorJob(viewModelScope.coroutineContext.job)
        this.settings = settings
        val models = if (settings.isConfigured) settings.models else emptyList()
        val key = TranslationKey(excerpt, settings.sourceLanguage, settings.effectiveTargetLanguage)
        this.key = key
        state.value = TranslationState(excerpt, settings.isConfigured, models, emptyMap())
        viewModelScope.launch(requests) {
            val found = dictionaries.lookup(excerpt.fragment)
            if (found.isNotEmpty()) state.update { it?.copy(dictionary = found) }
        }
        if (models.isEmpty()) return
        // The cache is ready long before anything is selected, so the cached results show at once.
        viewModelScope.launch(requests) {
            val cached = cache.get(key).filterKeys { it in models }
            state.update { it?.copy(results = cached.mapValues { (_, translation) -> ModelResult.Done(translation) }) }
            models.filter { it !in cached }.forEach(::request)
        }
    }

    private fun stop() {
        requests.cancel()
        state.value = null
        key = null
    }

    private fun request(model: String) {
        val excerpt = state.value?.excerpt ?: return
        val key = key ?: return
        val settings = settings
        // Waited for again.
        state.update { state -> state?.copy(results = state.results - model) }
        viewModelScope.launch(requests) {
            val language = settings.effectiveTargetLanguage
            val result = try {
                val reply = openRouter.chat(
                    apiKey = settings.apiKey,
                    model = model,
                    system = TranslationPrompt.system(language),
                    user = TranslationPrompt.user(excerpt, settings.sourceLanguage, language),
                )
                TranslationPrompt.parse(reply)?.let { ModelResult.Done(it) }
                    ?: ModelResult.Failed(TranslationError(TranslationError.Kind.BAD_REPLY)).also {
                        Log.w(TAG, "No translation in the reply of $model: $reply")
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Translation by $model failed", e)
                ModelResult.Failed(e.toTranslationError())
            }
            if (result is ModelResult.Done) cache.put(key, model, result.translation)
            // Added to the end: the results stay in the order they came.
            state.update { state -> state?.copy(results = state.results - model + (model to result)) }
        }
    }

    companion object {
        private const val TAG = "TranslationViewModel"

        /** How long the selection has to stay the same before the models are asked. */
        private const val SELECTION_DELAY_MS = 500L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BreaderApplication
                TranslationViewModel(app.openRouter, app.translationCache, app.dictionaryRepository)
            }
        }
    }
}

private fun Exception.toTranslationError(): TranslationError {
    val kind = when (this) {
        is OpenRouterException -> when (code) {
            401 -> TranslationError.Kind.INVALID_KEY
            402 -> TranslationError.Kind.NO_CREDITS
            408 -> TranslationError.Kind.TIMEOUT
            429 -> TranslationError.Kind.RATE_LIMITED
            else -> return TranslationError(TranslationError.Kind.OTHER, message)
        }
        is SocketTimeoutException -> TranslationError.Kind.TIMEOUT
        is IOException -> TranslationError.Kind.NETWORK
        else -> return TranslationError(TranslationError.Kind.OTHER, message ?: javaClass.simpleName)
    }
    return TranslationError(kind)
}
