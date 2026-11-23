package ru.nikitagrgv.breader.ui.dictionary

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import ru.nikitagrgv.breader.ui.KeepListPlace
import ru.nikitagrgv.breader.ui.ReorderableItem
import ru.nikitagrgv.breader.ui.reorderableItem
import ru.nikitagrgv.breader.ui.rememberReorderState
import ru.nikitagrgv.breader.ui.settings.htmlString
import java.text.NumberFormat

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
    val reorder = rememberReorderState(storedOrder) { keys -> onSettingsChange { it.copy(order = keys) } }
    val listState = rememberLazyListState()
    KeepListPlace(listState, reorder.order)

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

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item { SectionHeader(R.string.dictionaries_yours) }
                    item {
                        Text(
                            text = stringResource(R.string.dictionaries_yours_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    items(reorder.order, key = { it }) { key ->
                        val source = byKey[key] ?: return@items
                        val item = reorderableItem(reorder, key)
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
                                item = item,
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
                                item = item,
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

@Composable
private fun OnlineItem(
    dictionary: OnlineDictionary,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    yandexKey: String,
    onYandexKeyChange: (String) -> Unit,
    item: ReorderableItem,
) {
    Surface(color = itemColor(item.dragged), modifier = item.modifier) {
        Column {
            ListItem(
                headlineContent = { Text(stringResource(dictionary.title)) },
                overlineContent = { Text(stringResource(R.string.dictionaries_online)) },
                supportingContent = { Text(stringResource(dictionary.summary)) },
                leadingContent = item.handle,
                trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .toggleable(value = enabled, role = Role.Switch, onValueChange = onEnabledChange)
                    .semantics { customActions = item.actions },
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
    item: ReorderableItem,
) {
    val context = LocalContext.current
    Surface(color = itemColor(item.dragged), modifier = item.modifier) {
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
            leadingContent = item.handle,
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
                .semantics { customActions = item.actions },
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
