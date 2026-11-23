package ru.nikitagrgv.breader.ui.dictionary

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.DictionarySettings
import ru.nikitagrgv.breader.dictionary.CatalogLanguage
import ru.nikitagrgv.breader.dictionary.DictionaryInfo
import ru.nikitagrgv.breader.dictionary.DictionarySource
import ru.nikitagrgv.breader.dictionary.Download
import ru.nikitagrgv.breader.dictionary.DownloadError
import ru.nikitagrgv.breader.dictionary.DownloadState
import ru.nikitagrgv.breader.dictionary.Edition
import ru.nikitagrgv.breader.dictionary.Kaikki
import ru.nikitagrgv.breader.dictionary.OnlineDictionary
import ru.nikitagrgv.breader.dictionary.all
import ru.nikitagrgv.breader.dictionary.dictionaryId
import ru.nikitagrgv.breader.ui.settings.htmlString
import java.text.NumberFormat
import java.util.Collections

/**
 * A full-screen list of the dictionaries, the downloaded ones and the online ones, to turn them on and off and to set
 * the order of their results, and of the dictionaries to download.
 * @param defaultEdition the edition whose languages are listed first.
 * @param onSettingsChange gets a function that makes the new settings out of the stored ones.
 */
@Composable
fun DictionaryManagerDialog(
    defaultEdition: Edition,
    viewModel: DictionaryViewModel,
    settings: DictionarySettings,
    onSettingsChange: ((DictionarySettings) -> DictionarySettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var editionCode by rememberSaveable { mutableStateOf(defaultEdition.code) }
    val edition = Kaikki.editions.find { it.code == editionCode } ?: defaultEdition
    var query by rememberSaveable { mutableStateOf("") }
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val languageLists by viewModel.languages.collectAsStateWithLifecycle()
    LaunchedEffect(edition) { viewModel.loadLanguages(edition) }
    val languageList = languageLists[edition.code]
    val installedIds = remember(installed) { installed.mapTo(HashSet()) { it.id } }
    // The field keeps its own text: the stored one comes back a moment later and would move the cursor.
    var yandexKey by remember { mutableStateOf(settings.yandexKey) }

    val dictionaries = remember(installed, settings.order) { settings.all(installed) }
    val byKey = remember(dictionaries) { dictionaries.associateBy { it.orderKey } }
    val storedOrder = remember(dictionaries) { dictionaries.map { it.orderKey } }
    val drag = remember { DictionaryDrag() }
    // Once the settings have the order that's dragged, or anything else changes it.
    LaunchedEffect(storedOrder) { drag.forgetOrder() }
    val order = drag.shownOrder(storedOrder)
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    fun reorder(keys: List<String>) {
        if (keys != storedOrder) onSettingsChange { it.copy(order = keys) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                    }
                    Text(
                        text = stringResource(R.string.dictionaries_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                LazyColumn(Modifier.fillMaxSize()) {
                    item { SectionHeader(R.string.dictionaries_yours) }
                    item {
                        Text(
                            text = stringResource(R.string.dictionaries_yours_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    items(order, key = { it }) { key ->
                        val source = byKey[key] ?: return@items
                        val index = order.indexOf(key)
                        val dragged = drag.key == key
                        // Read in the layer, so that following the finger takes no recomposition.
                        val placement = if (dragged) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer {
                                    translationY = drag.offset
                                    shadowElevation = DRAGGED_ELEVATION.toPx()
                                }
                        } else {
                            Modifier.animateItem()
                        }
                        val handle: @Composable () -> Unit = {
                            DragHandle(
                                onStart = { y -> drag.start(key, y, order) },
                                onDrag = drag::dragTo,
                                onEnd = { drag.end()?.let(::reorder) },
                            )
                        }
                        // For those who can't drag.
                        val actions = listOfNotNull(
                            order.getOrNull(index - 1)?.let { CustomAccessibilityAction(moveUp) { reorder(order.swap(index, index - 1)); true } },
                            order.getOrNull(index + 1)?.let { CustomAccessibilityAction(moveDown) { reorder(order.swap(index, index + 1)); true } },
                        )
                        val modifier = placement.onSizeChanged { drag.heights[key] = it.height }
                        when (source) {
                            is DictionarySource.Downloaded -> DownloadedItem(
                                dictionary = source.info,
                                enabled = source.info.id !in settings.disabled,
                                onEnabledChange = { on ->
                                    val id = source.info.id
                                    onSettingsChange { it.copy(disabled = if (on) it.disabled - id else it.disabled + id) }
                                },
                                download = downloads[source.info.id],
                                onRemove = {
                                    viewModel.remove(source.info.id)
                                    onSettingsChange { it.forget(source.info.id) }
                                },
                                onCancel = { viewModel.cancel(source.info.id) },
                                handle = handle,
                                actions = actions,
                                dragged = dragged,
                                modifier = modifier,
                            )
                            is DictionarySource.Online -> OnlineItem(
                                dictionary = source.dictionary,
                                enabled = source.dictionary in settings.online,
                                onEnabledChange = { on ->
                                    val dictionary = source.dictionary
                                    onSettingsChange { it.copy(online = if (on) it.online + dictionary else it.online - dictionary) }
                                },
                                yandexKey = yandexKey,
                                onYandexKeyChange = { value ->
                                    yandexKey = value
                                    onSettingsChange { it.copy(yandexKey = value) }
                                },
                                handle = handle,
                                actions = actions,
                                dragged = dragged,
                                modifier = modifier,
                            )
                        }
                    }
                    // Downloads of dictionaries that aren't installed yet go after the installed ones.
                    val pending = downloads.filterKeys { it !in installedIds }
                    items(pending.entries.toList(), key = { it.key }) { (id, download) ->
                        PendingItem(
                            download = download,
                            onRetry = { viewModel.download(download.edition, download.language) },
                            onCancel = { viewModel.cancel(id) },
                        )
                    }

                    item { SectionHeader(R.string.dictionaries_available) }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                text = stringResource(R.string.dictionaries_edition_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            EditionChip(edition, onEditionChange = { editionCode = it.code })
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(stringResource(R.string.dictionaries_search)) },
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
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    languageItems(
                        list = languageList,
                        query = query,
                        edition = edition,
                        installedIds = installedIds,
                        downloads = downloads,
                        onDownload = { viewModel.download(edition, it) },
                        onRetry = { viewModel.loadLanguages(edition) },
                    )
                    item {
                        Text(
                            text = stringResource(R.string.dictionaries_attribution),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The dragging of a dictionary to another place in the list. The order shown follows it, ahead of the settings,
 * which only store the new one once the finger is lifted.
 */
@Stable
private class DictionaryDrag {
    /** The order being dragged, or dragged and not yet stored; null to show the stored one. */
    private var order by mutableStateOf<List<String>?>(null)

    /** The order key of the dictionary being dragged. */
    var key by mutableStateOf<String?>(null)
        private set

    /** How far it's moved from its place in the order, in px. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** Where the finger would be if it had stayed at the place of the dictionary in the order, in root px. */
    private var originY = 0f

    /** The heights of the dictionaries by order key, in px: one is dragged past another by half of its height. */
    val heights = HashMap<String, Int>()

    /** [stored], unless another order is being dragged; dictionaries added or removed meanwhile are added or removed. */
    fun shownOrder(stored: List<String>): List<String> {
        val dragged = order ?: return stored
        return dragged.filter { it in stored } + stored.filter { it !in dragged }
    }

    /** @param y where the finger is, in root px. */
    fun start(key: String, y: Float, order: List<String>) {
        this.order = order
        this.key = key
        originY = y
        offset = 0f
    }

    fun dragTo(y: Float) {
        val key = key ?: return
        val order = order?.toMutableList() ?: return
        var index = order.indexOf(key)
        if (index < 0) return
        offset = y - originY
        // A fast drag can go past several.
        while (true) {
            val next = order.getOrNull(index + 1)?.let { it to (heights[it] ?: 0) }
            val previous = order.getOrNull(index - 1)?.let { it to (heights[it] ?: 0) }
            if (next != null && offset > next.second / 2f) {
                Collections.swap(order, index, index + 1)
                index++
                // Its place in the order is lower by the height of the one it's gone past.
                originY += next.second
                offset -= next.second
            } else if (previous != null && offset < -previous.second / 2f) {
                Collections.swap(order, index, index - 1)
                index--
                originY -= previous.second
                offset += previous.second
            } else {
                break
            }
        }
        this.order = order
    }

    /** Returns the new order, which is still shown until the settings have it. */
    fun end(): List<String>? {
        key = null
        offset = 0f
        return order
    }

    /** Shows the stored order, unless one is being dragged. */
    fun forgetOrder() {
        if (key == null) order = null
    }
}

private fun List<String>.swap(from: Int, to: Int): List<String> = toMutableList().also { Collections.swap(it, from, to) }

private val DRAGGED_ELEVATION = 8.dp

/**
 * Drags at once, unlike a long press: it's there only for dragging. The positions are in root coordinates, as the
 * handle moves along with the finger.
 */
@Composable
private fun DragHandle(onStart: (y: Float) -> Unit, onDrag: (y: Float) -> Unit, onEnd: () -> Unit) {
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnEnd by rememberUpdatedState(onEnd)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Icon(
        painterResource(R.drawable.ic_drag_indicator),
        // The item has actions for moving it instead.
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .onGloballyPositioned { coordinates[0] = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Not a tap of the item, which would turn it on or off.
                    down.consume()
                    val layout = coordinates[0]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                    currentOnStart(layout.localToRoot(down.position).y)
                    try {
                        drag(down.id) { change ->
                            change.consume()
                            if (layout.isAttached) currentOnDrag(layout.localToRoot(change.position).y)
                        }
                    } finally {
                        currentOnEnd()
                    }
                }
            }
            .padding(12.dp),
    )
}

@Composable
private fun OnlineItem(
    dictionary: OnlineDictionary,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    yandexKey: String,
    onYandexKeyChange: (String) -> Unit,
    handle: @Composable () -> Unit,
    actions: List<CustomAccessibilityAction>,
    dragged: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(color = itemColor(dragged), modifier = modifier) {
        Column {
            ListItem(
                headlineContent = { Text(stringResource(dictionary.title)) },
                overlineContent = { Text(stringResource(R.string.dictionaries_online)) },
                supportingContent = { Text(stringResource(dictionary.summary)) },
                leadingContent = handle,
                trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                    .semantics { customActions = actions },
            )
            if (dictionary == OnlineDictionary.YANDEX && enabled) {
                OutlinedTextField(
                    value = yandexKey,
                    onValueChange = onYandexKeyChange,
                    label = { Text(stringResource(R.string.yandex_key)) },
                    supportingText = { Text(htmlString(R.string.yandex_key_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/** Lifted off the list while it's dragged. */
@Composable
private fun itemColor(dragged: Boolean): Color =
    if (dragged) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface

@get:StringRes
private val OnlineDictionary.summary: Int
    get() = when (this) {
        OnlineDictionary.YANDEX -> R.string.online_yandex_summary
        OnlineDictionary.GOOGLE -> R.string.online_google_summary
        OnlineDictionary.WIKTIONARY -> R.string.online_wiktionary_summary
    }

private fun LazyListScope.languageItems(
    list: LanguageList?,
    query: String,
    edition: Edition,
    installedIds: Set<String>,
    downloads: Map<String, Download>,
    onDownload: (CatalogLanguage) -> Unit,
    onRetry: () -> Unit,
) {
    when (list) {
        null, LanguageList.Loading -> item {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        LanguageList.Failed -> item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(32.dp)) {
                Text(
                    text = stringResource(R.string.dictionaries_languages_failed),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                FilledTonalButton(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
        is LanguageList.Loaded -> {
            val shown = list.languages.filter { it.name.contains(query.trim(), ignoreCase = true) }
            if (shown.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.dictionaries_languages_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(shown, key = { "${edition.code}/${it.path}" }) { language ->
                val id = dictionaryId(edition, language)
                LanguageItem(
                    language = language,
                    installed = id in installedIds,
                    downloading = downloads[id]?.state is DownloadState.Running,
                    onDownload = { onDownload(language) },
                )
            }
        }
    }
}

/** @param download of the dictionary again, which replaces it once it's done. */
@Composable
private fun DownloadedItem(
    dictionary: DictionaryInfo,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    download: Download?,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    handle: @Composable () -> Unit,
    actions: List<CustomAccessibilityAction>,
    dragged: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(color = itemColor(dragged), modifier = modifier) {
        ListItem(
            headlineContent = { Text(dictionary.name) },
            overlineContent = { Text(stringResource(R.string.dictionaries_downloaded)) },
            supportingContent = {
                if (download != null) {
                    DownloadProgress(download.state)
                } else {
                    Text(
                        stringResource(
                            R.string.dictionary_info,
                            dictionary.edition.name,
                            pluralStringResource(R.plurals.dictionary_words, dictionary.entries, formatNumber(dictionary.entries)),
                            Formatter.formatShortFileSize(context, dictionary.sizeBytes),
                        ),
                    )
                }
            },
            leadingContent = handle,
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (download != null) {
                        CancelButton(onCancel)
                    } else {
                        IconButton(onClick = onRemove) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.dictionary_remove))
                        }
                    }
                    Switch(checked = enabled, onCheckedChange = null)
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                .semantics { customActions = actions },
        )
    }
}

@Composable
private fun PendingItem(download: Download, onRetry: () -> Unit, onCancel: () -> Unit) {
    ListItem(
        headlineContent = { Text(download.language.name) },
        overlineContent = { Text(stringResource(R.string.dictionary_edition, download.edition.name)) },
        supportingContent = { DownloadProgress(download.state) },
        trailingContent = {
            Row {
                if (download.state is DownloadState.Failed) {
                    IconButton(onClick = onRetry) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.retry))
                    }
                }
                CancelButton(onCancel)
            }
        },
    )
}

@Composable
private fun DownloadProgress(state: DownloadState) {
    val context = LocalContext.current
    when (state) {
        is DownloadState.Failed -> Text(stringResource(downloadErrorText(state.error)), color = MaterialTheme.colorScheme.error)
        is DownloadState.Running -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val bytes = Formatter.formatShortFileSize(context, state.bytes)
            val total = state.totalBytes?.let { Formatter.formatShortFileSize(context, it) }
            Text(
                when {
                    state.indexing -> stringResource(R.string.dictionary_indexing)
                    total == null -> stringResource(R.string.dictionary_downloading_unknown, bytes)
                    state.wholeEdition -> stringResource(R.string.dictionary_downloading_edition, bytes, total)
                    else -> stringResource(R.string.dictionary_downloading, bytes, total)
                },
            )
            val totalBytes = state.totalBytes
            if (state.indexing || totalBytes == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { state.bytes.toFloat() / totalBytes }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LanguageItem(language: CatalogLanguage, installed: Boolean, downloading: Boolean, onDownload: () -> Unit) {
    val enabled = !installed && !downloading
    ListItem(
        headlineContent = { Text(language.name) },
        supportingContent = {
            Text(pluralStringResource(R.plurals.dictionary_senses, language.senses, formatNumber(language.senses)))
        },
        trailingContent = {
            when {
                installed -> Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.dictionaries_downloaded),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp),
                )
                downloading -> CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                else -> IconButton(onClick = onDownload) {
                    Icon(painterResource(R.drawable.ic_download), contentDescription = stringResource(R.string.dictionary_download))
                }
            }
        },
        modifier = if (enabled) Modifier.clickable(onClick = onDownload) else Modifier,
    )
}

@Composable
private fun EditionChip(edition: Edition, onEditionChange: (Edition) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(stringResource(R.string.dictionary_edition, edition.name)) },
            trailingIcon = {
                Icon(painterResource(R.drawable.ic_arrow_drop_down), contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in Kaikki.editions) {
                DropdownMenuItem(
                    text = { Text(option.name) },
                    leadingIcon = { RadioButton(selected = option == edition, onClick = null) },
                    onClick = {
                        onEditionChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CancelButton(onCancel: () -> Unit) {
    IconButton(onClick = onCancel) {
        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel))
    }
}

@Composable
private fun SectionHeader(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@StringRes
private fun downloadErrorText(error: DownloadError): Int = when (error) {
    DownloadError.NETWORK -> R.string.dictionary_error_network
    DownloadError.NOT_FOUND -> R.string.dictionary_error_not_found
    DownloadError.EMPTY -> R.string.dictionary_error_empty
    DownloadError.TOO_BIG -> R.string.dictionary_error_too_big
    DownloadError.OTHER -> R.string.dictionary_error_other
}

private fun formatNumber(number: Int): String = NumberFormat.getIntegerInstance().format(number)
