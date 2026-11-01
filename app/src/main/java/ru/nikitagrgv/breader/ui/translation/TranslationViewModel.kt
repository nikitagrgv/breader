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
import ru.nikitagrgv.breader.translation.OpenRouter
import ru.nikitagrgv.breader.translation.OpenRouterException
import ru.nikitagrgv.breader.translation.Translation
import ru.nikitagrgv.breader.translation.TranslationPrompt
import java.io.IOException
import java.net.SocketTimeoutException

sealed interface ModelResult {
    data object Loading : ModelResult
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
    /** By model ID, in the order of the settings. */
    val results: Map<String, ModelResult>,
)

/** Asks all the chosen models at once; each result is shown as soon as it comes. */
class TranslationViewModel(private val openRouter: OpenRouter) : ViewModel() {
    private val _state = MutableStateFlow<TranslationState?>(null)

    /** Null when nothing is being translated. */
    val state: StateFlow<TranslationState?> = _state.asStateFlow()

    private var settings = TranslationSettings()

    /** The parent of the current translation's requests. */
    private var requests: Job = Job()

    fun translate(excerpt: Excerpt, settings: TranslationSettings) {
        requests.cancel()
        requests = SupervisorJob(viewModelScope.coroutineContext.job)
        this.settings = settings
        val models = if (settings.isConfigured) settings.models else emptyList()
        _state.value = TranslationState(excerpt, settings.isConfigured, models.associateWith { ModelResult.Loading })
        models.forEach(::request)
    }

    fun retry(model: String) = request(model)

    fun dismiss() {
        requests.cancel()
        _state.value = null
    }

    private fun request(model: String) {
        val excerpt = _state.value?.excerpt ?: return
        val settings = settings
        setResult(model, ModelResult.Loading)
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
            setResult(model, result)
        }
    }

    private fun setResult(model: String, result: ModelResult) {
        // Replacing the value of an existing key keeps the order of the map.
        _state.update { state -> state?.copy(results = state.results + (model to result)) }
    }

    companion object {
        private const val TAG = "TranslationViewModel"

        val Factory = viewModelFactory {
            initializer { TranslationViewModel((this[APPLICATION_KEY] as BreaderApplication).openRouter) }
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
