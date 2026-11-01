package ru.nikitagrgv.breader.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.translation.CatalogOrder
import ru.nikitagrgv.breader.translation.ModelInfo
import java.math.BigDecimal
import java.math.MathContext

enum class ModelSort(@param:StringRes val label: Int, val catalogOrder: CatalogOrder) {
    /** Free ones first; equally priced ones by popularity. */
    PRICE(R.string.sort_price, CatalogOrder.POPULARITY),
    POPULARITY(R.string.sort_popularity, CatalogOrder.POPULARITY),
    INTELLIGENCE(R.string.sort_intelligence, CatalogOrder.INTELLIGENCE),
    SPEED(R.string.sort_speed, CatalogOrder.SPEED),
    NEWEST(R.string.sort_newest, CatalogOrder.POPULARITY),
}

/** A full-screen list of the OpenRouter models to pick the ones for translation. */
@Composable
fun ModelPickerDialog(
    selected: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    viewModel: ModelsViewModel = viewModel(factory = ModelsViewModel.Factory),
) {
    // In the order of picking, which is also the order of the results.
    var selection by rememberSaveable { mutableStateOf(selected.toList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(ModelSort.PRICE) }
    var freeOnly by rememberSaveable { mutableStateOf(false) }
    var selectedOnly by rememberSaveable { mutableStateOf(false) }

    val catalogs by viewModel.catalogs.collectAsStateWithLifecycle()
    val catalog = catalogs[sort.catalogOrder]
    LaunchedEffect(sort.catalogOrder) { viewModel.load(sort.catalogOrder) }
    val models = (catalog as? Catalog.Loaded)?.models
    val shown = remember(models, sort, query, freeOnly, selectedOnly, selection) {
        models?.let { pickerList(it, sort, query, freeOnly, selectedOnly, selection) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                    }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(stringResource(R.string.models_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = pluralStringResource(R.plurals.models_selected, selection.size, selection.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onSave(selection) }) {
                        Text(stringResource(R.string.save))
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_models)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    SortChip(sort, onSortChange = { sort = it })
                    FilterChip(
                        selected = freeOnly,
                        onClick = { freeOnly = !freeOnly },
                        label = { Text(stringResource(R.string.filter_free)) },
                    )
                    FilterChip(
                        selected = selectedOnly,
                        onClick = { selectedOnly = !selectedOnly },
                        label = { Text(stringResource(R.string.filter_selected)) },
                    )
                }
                HorizontalDivider()

                when {
                    catalog == Catalog.Failed -> Message(stringResource(R.string.models_failed)) {
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = { viewModel.load(sort.catalogOrder) }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                    shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    shown.isEmpty() -> Message(stringResource(R.string.models_empty))
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.id }) { model ->
                            val checked = model.id in selection
                            ModelItem(model, checked) {
                                selection = if (checked) selection - model.id else selection + model.id
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Filters and sorts [models], which come in their [ModelSort.catalogOrder]. The ones in [selection]
 * that aren't in the catalog (e.g. removed from OpenRouter) are added at the end, so they can be unpicked.
 */
internal fun pickerList(
    models: List<ModelInfo>,
    sort: ModelSort,
    query: String,
    freeOnly: Boolean,
    selectedOnly: Boolean,
    selection: Collection<String>,
): List<ModelInfo> {
    val known = models.mapTo(HashSet()) { it.id }
    val unknown = selection.filter { it !in known }.map { ModelInfo(it, it, null, null, 0) }
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val filtered = (models + unknown).filter { model ->
        (!freeOnly || model.isFree) &&
            (!selectedOnly || model.id in selection) &&
            terms.all { model.name.contains(it, ignoreCase = true) || model.id.contains(it, ignoreCase = true) }
    }
    // Stable sorts: models that are equal by these keep the catalog order.
    return when (sort) {
        ModelSort.PRICE -> filtered.sortedWith(compareBy(nullsLast()) { it.price })
        ModelSort.NEWEST -> filtered.sortedByDescending { it.created }
        ModelSort.POPULARITY, ModelSort.INTELLIGENCE, ModelSort.SPEED -> filtered
    }
}

@Composable
private fun SortChip(sort: ModelSort, onSortChange: (ModelSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(stringResource(sort.label)) },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_sort), contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
            },
            trailingIcon = {
                Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in ModelSort.entries) {
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                    onClick = {
                        onSortChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelItem(model: ModelInfo, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        headlineContent = { Text(model.name) },
        supportingContent = {
            Column {
                Text(model.id)
                Text(priceLabel(model), color = if (model.isFree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    )
}

@Composable
private fun priceLabel(model: ModelInfo): String = when {
    model.isFree -> stringResource(R.string.price_free)
    model.promptPrice == null || model.completionPrice == null -> stringResource(R.string.price_unknown)
    else -> stringResource(R.string.price_per_million, formatPrice(model.promptPrice), formatPrice(model.completionPrice))
}

/** Per million tokens, to 3 significant digits: "0.15", "2.5", "15". */
private fun formatPrice(perToken: Double): String =
    BigDecimal(perToken * 1_000_000).round(MathContext(3)).stripTrailingZeros().toPlainString()

@Composable
private fun Message(text: String, content: @Composable () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        content()
    }
}
