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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.BreaderApplication
import ru.nikitagrgv.breader.data.TranslationSettings
import ru.nikitagrgv.breader.translation.Excerpt
import ru.nikitagrgv.breader.translation.GrammarExplanation
import ru.nikitagrgv.breader.translation.GrammarPrompt
import ru.nikitagrgv.breader.translation.OpenRouter

sealed interface GrammarResult {
    data class Done(val explanation: GrammarExplanation) : GrammarResult
    data class Failed(val error: TranslationError) : GrammarResult
}

data class GrammarState(
    val excerpt: Excerpt,
    /** False if there's no API key or no models to ask. */
    val configured: Boolean,
    /** The asked models, in the order of the settings. */
    val models: List<String>,
    /** Results by model, in the order they came; the models that have none yet are waited for. */
    val results: Map<String, GrammarResult>,
)

/** Asks the translation models to explain the grammar of the selected fragment, all at once. */
class GrammarViewModel(private val openRouter: OpenRouter) : ViewModel() {
    private val _state = MutableStateFlow<GrammarState?>(null)

    /** Null when no explanation is shown. */
    val state: StateFlow<GrammarState?> = _state.asStateFlow()

    /** The last explanation, kept after it's dismissed: asking for the same fragment again doesn't ask the models again. */
    private var last: GrammarState? = null
    private var settings = TranslationSettings()
    private var requests: Job = Job()

    fun explain(excerpt: Excerpt, settings: TranslationSettings) {
        requests.cancel()
        requests = SupervisorJob(viewModelScope.coroutineContext.job)
        val models = if (settings.isConfigured) settings.models else emptyList()
        val previous = last?.takeIf { it.excerpt == excerpt && this.settings == settings }
        this.settings = settings
        // The models that failed or didn't answer before are asked again.
        val kept = previous?.results.orEmpty().filter { (model, result) -> model in models && result is GrammarResult.Done }
        _state.value = GrammarState(excerpt, settings.isConfigured, models, kept)
        last = _state.value
        models.filter { it !in kept }.forEach(::request)
    }

    fun retry(model: String) = request(model)

    fun dismiss() {
        requests.cancel()
        _state.value = null
    }

    private fun request(model: String) {
        val excerpt = _state.value?.excerpt ?: return
        val settings = settings
        update { it.copy(results = it.results - model) }
        viewModelScope.launch(requests) {
            val language = settings.effectiveTargetLanguage
            val result = try {
                val reply = openRouter.chat(
                    apiKey = settings.apiKey,
                    model = model,
                    system = GrammarPrompt.system(language),
                    user = GrammarPrompt.user(excerpt, settings.sourceLanguage, language),
                )
                GrammarPrompt.parse(reply)?.let { GrammarResult.Done(it) }
                    ?: GrammarResult.Failed(TranslationError(TranslationError.Kind.BAD_REPLY))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Grammar explanation by $model failed", e)
                GrammarResult.Failed(e.toTranslationError())
            }
            // Added to the end: the results stay in the order they came.
            update { it.copy(results = it.results - model + (model to result)) }
        }
    }

    private fun update(transform: (GrammarState) -> GrammarState) {
        _state.update { state -> state?.let(transform)?.also { last = it } }
    }

    companion object {
        private const val TAG = "GrammarViewModel"

        val Factory = viewModelFactory {
            initializer { GrammarViewModel((this[APPLICATION_KEY] as BreaderApplication).openRouter) }
        }
    }
}
