package ru.nikitagrgv.breader.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.BreaderApplication
import ru.nikitagrgv.breader.translation.Credits
import ru.nikitagrgv.breader.translation.KeyInfo
import ru.nikitagrgv.breader.translation.LoggedRequest
import ru.nikitagrgv.breader.translation.OpenRouter
import ru.nikitagrgv.breader.translation.UsageLog
import ru.nikitagrgv.breader.translation.UsageTotals
import ru.nikitagrgv.breader.ui.translation.TranslationError
import ru.nikitagrgv.breader.ui.translation.toTranslationError

sealed interface Balance {
    data object Loading : Balance
    data class Failed(val error: TranslationError) : Balance

    /** @param credits null if the key may not see them. */
    data class Loaded(val key: KeyInfo, val credits: Credits?) : Balance {
        /** What can still be spent: the key limit or the account credits, whichever leaves less; null if neither is known. */
        val remaining: Double?
            get() = listOfNotNull(key.limitRemaining, credits?.remaining).minOrNull()
    }
}

/** The latest requests in the log; [endReached] if there are no more. */
data class LoggedRequests(val requests: List<LoggedRequest>, val totals: UsageTotals, val endReached: Boolean)

/** What OpenRouter tells about the key, and the requests the app made with it. */
class UsageViewModel(private val openRouter: OpenRouter, private val log: UsageLog) : ViewModel() {
    private val _balance = MutableStateFlow<Balance>(Balance.Loading)
    val balance: StateFlow<Balance> = _balance.asStateFlow()
    private var balanceJob: Job? = null

    /** How many of the latest requests are shown; grows a page at a time as the list is scrolled. */
    private val limit = MutableStateFlow(PAGE_SIZE)

    /** Null until they're read. Read anew as requests are made, so the new ones show up at the top. */
    val requests: StateFlow<LoggedRequests?> = combine(log.changes, limit) { _, limit -> limit }
        .map { limit ->
            val requests = log.latest(limit)
            LoggedRequests(requests, log.totals(), endReached = requests.size < limit)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshBalance(apiKey: String) {
        balanceJob?.cancel()
        _balance.value = Balance.Loading
        balanceJob = viewModelScope.launch {
            _balance.value = try {
                coroutineScope {
                    // Only a nicety, not worth losing the key's numbers for.
                    val credits = async {
                        try {
                            openRouter.credits(apiKey)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to get the credits", e)
                            null
                        }
                    }
                    Balance.Loaded(openRouter.keyInfo(apiKey), credits.await())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get the key info", e)
                Balance.Failed(e.toTranslationError())
            }
        }
    }

    /** Shows another page of the requests, unless the shown ones are all there are or the last page isn't read yet. */
    fun loadMore() {
        val shown = requests.value ?: return
        if (!shown.endReached && shown.requests.size >= limit.value) limit.value += PAGE_SIZE
    }

    fun clear() {
        viewModelScope.launch {
            log.clear()
            limit.value = PAGE_SIZE
        }
    }

    companion object {
        private const val TAG = "UsageViewModel"
        private const val PAGE_SIZE = 50

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as BreaderApplication
                UsageViewModel(app.openRouter, app.usageLog)
            }
        }
    }
}
