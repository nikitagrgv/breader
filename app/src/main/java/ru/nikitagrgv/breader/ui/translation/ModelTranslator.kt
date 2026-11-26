package ru.nikitagrgv.breader.ui.translation

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import ru.nikitagrgv.breader.data.TranslationSettings
import ru.nikitagrgv.breader.translation.OpenRouter
import ru.nikitagrgv.breader.translation.RequestKind
import ru.nikitagrgv.breader.translation.TranslationCache
import ru.nikitagrgv.breader.translation.TranslationKey
import ru.nikitagrgv.breader.translation.TranslationPrompt

/**
 * Asks the models to translate excerpts, and caches the translations they give. A model already asked for a
 * translation isn't asked for it again: whoever wants it too waits for that reply, as when a selection being translated
 * is added to the favorites. A request goes on when nobody waits for it any more: it's billed anyway, so its
 * translation is cached for next time. There must be a single instance per process; must be used on the main thread.
 */
class ModelTranslator(private val openRouter: OpenRouter, private val cache: TranslationCache) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = HashMap<Pair<TranslationKey, String>, Deferred<ModelResult>>()

    /** What the [model] makes of the excerpt of [key], asked with the key and the language of the [settings]. */
    suspend fun translate(key: TranslationKey, model: String, settings: TranslationSettings): ModelResult {
        val id = key to model
        val request = requests[id] ?: scope.async { ask(key, model, settings) }.also { request ->
            requests[id] = request
            request.invokeOnCompletion { if (requests[id] === request) requests.remove(id) }
        }
        return request.await()
    }

    private suspend fun ask(key: TranslationKey, model: String, settings: TranslationSettings): ModelResult {
        val language = key.targetLanguage
        val result = try {
            val reply = openRouter.chat(
                apiKey = settings.apiKey,
                model = model,
                system = TranslationPrompt.system(language),
                user = TranslationPrompt.user(key.excerpt, key.sourceLanguage, language),
                kind = RequestKind.TRANSLATION,
                fragment = key.excerpt.fragment,
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
        return result
    }

    private companion object {
        const val TAG = "ModelTranslator"
    }
}
