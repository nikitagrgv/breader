package ru.nikitagrgv.breader.ui.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.ui.BottomSheet
import ru.nikitagrgv.breader.dictionary.DictionaryEntry
import ru.nikitagrgv.breader.dictionary.DictionaryResult

/**
 * The dictionary entries of the selected text in detail: all the senses with examples.
 * @param hasDictionaries whether any are downloaded; if not, [onOpenDictionaries] is offered.
 */
@Composable
fun DictionarySheet(
    lookup: DictionaryLookup,
    hasDictionaries: Boolean,
    onOpenDictionaries: () -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                text = lookup.text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            val results = lookup.results
            when {
                results == null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                results.isNotEmpty() -> SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        results.forEach { Result(it) }
                    }
                }
                else -> {
                    Text(
                        text = stringResource(if (hasDictionaries) R.string.dictionary_nothing_found else R.string.dictionary_none_downloaded),
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
}

@Composable
private fun Result(result: DictionaryResult) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.dictionary_source, result.dictionary.name, result.dictionary.edition.name),
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
                Text(
                    text = entry.pos,
                    style = MaterialTheme.typography.labelLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                )
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
