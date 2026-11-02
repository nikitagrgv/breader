package ru.nikitagrgv.breader.ui.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.translation.GrammarExplanation
import ru.nikitagrgv.breader.ui.BottomSheet

/**
 * The grammar of the selected fragment, explained by each model. One explanation is shown at a time, the
 * models are switched by chips: unlike translations, explanations are too long to read one after another.
 */
@Composable
fun GrammarSheet(
    state: GrammarState,
    onRetry: (model: String) -> Unit,
    onOpenSettings: () -> Unit,
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
                text = state.excerpt.fragment,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            if (state.configured) {
                Explanations(state, onRetry)
            } else {
                Text(stringResource(R.string.grammar_setup_needed), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.open_settings))
                }
            }
        }
    }
}

@Composable
private fun Explanations(state: GrammarState, onRetry: (model: String) -> Unit) {
    // Until a model is picked, the first one that answered: later answers don't replace what's being read.
    var picked by rememberSaveable(state.excerpt) { mutableStateOf<String?>(null) }
    val shown = picked?.takeIf { it in state.models }
        ?: state.results.entries.firstOrNull { it.value is GrammarResult.Done }?.key
        ?: state.models.first()

    if (state.models.size > 1) {
        // Wrapped to more lines rather than scrolled: all of them are seen, the picked one too.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (model in state.models) {
                val result = state.results[model]
                FilterChip(
                    selected = model == shown,
                    onClick = { picked = model },
                    label = { Text(model.shortModelName()) },
                    leadingIcon = when (result) {
                        null -> {
                            { CircularProgressIndicator(Modifier.size(FilterChipDefaults.IconSize), strokeWidth = 2.dp) }
                        }
                        is GrammarResult.Failed -> {
                            {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        }
                        is GrammarResult.Done -> null
                    },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    when (val result = state.results[shown]) {
        null -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.grammar_waiting, shown.shortModelName()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        is GrammarResult.Failed -> Column {
            Text(errorText(result.error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = { onRetry(shown) }) {
                Text(stringResource(R.string.retry))
            }
        }
        is GrammarResult.Done -> SelectionContainer { Explanation(result.explanation) }
    }
}

@Composable
private fun Explanation(explanation: GrammarExplanation) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (explanation.translation.isNotEmpty()) {
            Text(explanation.translation, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        }
        for (point in explanation.points) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (point.words.isNotEmpty()) Text(point.words, style = MaterialTheme.typography.titleMedium)
                if (point.rule.isNotEmpty()) {
                    Text(point.rule, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (point.explanation.isNotEmpty()) Text(point.explanation, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (explanation.text.isNotEmpty()) {
            // A reply that ignored the format is often markdown.
            Text(explanation.text.replace("**", ""), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
