package ru.nikitagrgv.breader.ui.dictionary

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.dictionary.DictionaryEntry
import ru.nikitagrgv.breader.dictionary.DictionaryResult
import ru.nikitagrgv.breader.dictionary.DictionarySource
import ru.nikitagrgv.breader.dictionary.LookupFailure
import ru.nikitagrgv.breader.dictionary.OnlineDictionary
import ru.nikitagrgv.breader.ui.BottomSheet

/**
 * The dictionary entries of a text in detail: all the senses with examples. The text is in a field, so that another
 * word can be looked up from here: the words of the downloaded dictionaries that start with what's typed are offered.
 * @param hasDictionaries whether any are downloaded or online; if not, [onOpenDictionaries] is offered.
 */
@Composable
fun DictionarySheet(
    lookup: DictionaryLookup,
    suggestions: List<String>,
    hasDictionaries: Boolean,
    onLookUp: (String) -> Unit,
    onSuggest: (String) -> Unit,
    onOpenDictionaries: () -> Unit,
    onDismiss: () -> Unit,
) {
    // A selection may run over several lines.
    var query by rememberSaveable(lookup.text) { mutableStateOf(lookup.text.replace('\n', ' ')) }
    val editing = query.trim() != lookup.text.replace('\n', ' ').trim()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    fun submit(text: String) {
        query = text
        keyboard?.hide()
        if (text.isNotBlank()) onLookUp(text.trim())
    }

    LaunchedEffect(query, editing) {
        if (editing) onSuggest(query)
    }

    BottomSheet(onDismissRequest = onDismiss) {
        // Opened to type a word in. Here, as the sheet is a window of its own, which the field is in.
        LaunchedEffect(Unit) {
            if (lookup.text.isEmpty()) focus.requestFocus()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.dictionary_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            query = ""
                            focus.requestFocus()
                        }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
            Spacer(Modifier.height(16.dp))
            when {
                editing -> Suggestions(suggestions, onPick = ::submit)
                lookup.text.isEmpty() -> Unit
                else -> Results(lookup, hasDictionaries, onOpenDictionaries)
            }
        }
    }
}

@Composable
private fun Suggestions(suggestions: List<String>, onPick: (String) -> Unit) {
    if (suggestions.isEmpty()) {
        Text(
            text = stringResource(R.string.dictionary_search_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column {
        for (word in suggestions) {
            Text(
                text = word,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(word) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Results(lookup: DictionaryLookup, hasDictionaries: Boolean, onOpenDictionaries: () -> Unit) {
    val results = lookup.results
    if (results == null) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (results.isNotEmpty()) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    results.forEach { Result(it) }
                }
            }
        }
        if (lookup.pending.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.dictionary_asking, lookup.pending.map { stringResource(it.title) }.joinToString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        if (lookup.failed.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((dictionary, failure) in lookup.failed) {
                    Text(
                        text = stringResource(R.string.dictionary_failed, stringResource(dictionary.title), failureText(failure)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (results.isEmpty() && lookup.pending.isEmpty()) {
            Column {
                Text(
                    text = stringResource(if (hasDictionaries) R.string.dictionary_nothing_found else R.string.dictionary_none),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onOpenDictionaries) {
                    Text(stringResource(R.string.manage_dictionaries))
                }
            }
        }
    }
}

@Composable
private fun Result(result: DictionaryResult) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = when (val source = result.source) {
                is DictionarySource.Downloaded -> stringResource(R.string.dictionary_source, source.info.name, source.info.edition.name)
                is DictionarySource.Online -> stringResource(source.dictionary.title)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        result.entries.forEach { Entry(it) }
    }
}

@Composable
private fun Entry(entry: DictionaryEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(entry.word, style = MaterialTheme.typography.titleLarge)
                val details = listOf(entry.transcription.takeIf { it.isNotEmpty() }?.let { "[$it]" }, entry.pos.ifEmpty { null })
                    .filterNotNull()
                    .joinToString("  ")
                if (details.isNotEmpty()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.labelLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                    )
                }
            }
            if (entry.forms.isNotEmpty()) {
                Text(
                    text = entry.forms.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        entry.senses.forEachIndexed { index, sense ->
            Row {
                Text("${index + 1}.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (sense.tags.isNotEmpty()) {
                        Text(
                            text = sense.tags.joinToString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(sense.gloss, style = MaterialTheme.typography.bodyLarge)
                    if (sense.backTranslations.isNotEmpty()) {
                        Text(
                            text = "(${sense.backTranslations.joinToString()})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    for (example in sense.examples) {
                        Text(
                            text = if (example.translation.isEmpty()) example.text else "${example.text} — ${example.translation}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@get:StringRes
val OnlineDictionary.title: Int
    get() = when (this) {
        OnlineDictionary.YANDEX -> R.string.online_yandex
        OnlineDictionary.GOOGLE -> R.string.online_google
        OnlineDictionary.WIKTIONARY -> R.string.online_wiktionary
    }

@Composable
private fun failureText(failure: LookupFailure): String = when (failure.kind) {
    LookupFailure.Kind.NETWORK -> stringResource(R.string.error_network)
    LookupFailure.Kind.RATE_LIMITED -> stringResource(R.string.error_rate_limited)
    LookupFailure.Kind.INVALID_KEY -> stringResource(R.string.error_invalid_key)
    LookupFailure.Kind.NEEDS_SOURCE_LANGUAGE -> stringResource(R.string.dictionary_needs_source_language)
    LookupFailure.Kind.OTHER -> failure.details ?: stringResource(R.string.error_unknown)
}
