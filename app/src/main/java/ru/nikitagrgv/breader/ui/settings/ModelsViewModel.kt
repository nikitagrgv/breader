package ru.nikitagrgv.breader.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.BreaderApplication
import ru.nikitagrgv.breader.translation.CatalogOrder
import ru.nikitagrgv.breader.translation.ModelInfo
import ru.nikitagrgv.breader.translation.OpenRouter

sealed interface Catalog {
    data object Loading : Catalog
    data object Failed : Catalog
    data class Loaded(val models: List<ModelInfo>) : Catalog
}

/** The OpenRouter model catalog. Kept while the activity lives, so the picker doesn't download it every time. */
class ModelsViewModel(private val openRouter: OpenRouter) : ViewModel() {
    private val _catalogs = MutableStateFlow<Map<CatalogOrder, Catalog>>(emptyMap())

    /** The catalog in each order that was asked for. */
    val catalogs: StateFlow<Map<CatalogOrder, Catalog>> = _catalogs.asStateFlow()

    /** Loads the catalog in [order], unless it's loaded already or being loaded. */
    fun load(order: CatalogOrder) {
        val current = _catalogs.value[order]
        if (current is Catalog.Loaded || current == Catalog.Loading) return
        _catalogs.update { it + (order to Catalog.Loading) }
        viewModelScope.launch {
            val result = try {
                Catalog.Loaded(openRouter.models(order))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load the models by $order", e)
                Catalog.Failed
            }
            _catalogs.update { it + (order to result) }
        }
    }

    companion object {
        private const val TAG = "ModelsViewModel"

        val Factory = viewModelFactory {
            initializer { ModelsViewModel((this[APPLICATION_KEY] as BreaderApplication).openRouter) }
        }
    }
}
