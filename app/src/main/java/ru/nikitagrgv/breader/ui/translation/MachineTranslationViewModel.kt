package ru.nikitagrgv.breader.ui.translation

import android.util.Log
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.data.TranslationSettings
import ru.nikitagrgv.breader.translation.MachineTranslation
import ru.nikitagrgv.breader.translation.MachineTranslationService
import ru.nikitagrgv.breader.translation.MachineTranslator

sealed interface MachineResult {
    data class Done(val translation: MachineTranslation) : MachineResult
    data class Failed(val error: TranslationError) : MachineResult
}

/**
 * @param from the code of the language to translate from, null if it's detected.
 * @param result null while it's being translated.
 */
data class MachineTranslationState(
    val text: String,
    val service: MachineTranslationService,
    val from: String?,
    val to: String,
    val result: MachineResult?,
)

/** Translation of the selected text by a free service, which translates it alone, without the text around it. */
class MachineTranslationViewModel : ViewModel() {
    private val _state = MutableStateFlow<MachineTranslationState?>(null)

    /** Null when the translation isn't shown. */
    val state: StateFlow<MachineTranslationState?> = _state.asStateFlow()

    /** The recent translations, so that opening one again needs no requests. */
    private val cache = LruCache<MachineTranslationState, MachineTranslation>(CACHE_SIZE)
    private var job: Job? = null

    fun translate(text: String, settings: TranslationSettings) =
        translate(MachineTranslationState(text, settings.machineService, settings.sourceCode, settings.targetCode, result = null))

    /** Translates the same text by another service. */
    fun switchService(service: MachineTranslationService) {
        val current = _state.value ?: return
        if (current.service != service) translate(current.copy(service = service, result = null))
    }

    fun retry() {
        _state.value?.let { translate(it.copy(result = null)) }
    }

    fun dismiss() {
        job?.cancel()
        _state.value = null
    }

    /** @param request has no result. */
    private fun translate(request: MachineTranslationState) {
        job?.cancel()
        cache[request]?.let {
            _state.value = request.copy(result = MachineResult.Done(it))
            return
        }
        _state.value = request
        job = viewModelScope.launch {
            val result = try {
                val translation = MachineTranslator.translate(request.service, request.text, request.from, request.to)
                cache.put(request, translation)
                MachineResult.Done(translation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Translation by ${request.service} failed", e)
                MachineResult.Failed(e.toTranslationError())
            }
            _state.value = request.copy(result = result)
        }
    }

    companion object {
        private const val TAG = "MachineTranslation"
        private const val CACHE_SIZE = 20

        val Factory = viewModelFactory {
            initializer { MachineTranslationViewModel() }
        }
    }
}
