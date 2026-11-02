package ru.nikitagrgv.breader.ui.translation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.ui.BottomSheet
import ru.nikitagrgv.breader.translation.CombinedTranslation
import ru.nikitagrgv.breader.translation.combine

@Composable
fun TranslationSheet(
    state: TranslationState,
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
                Results(state, onRetry)
            } else {
                Text(stringResource(R.string.translation_setup_needed), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.open_settings))
                }
            }
        }
    }
}

@Composable
private fun Results(state: TranslationState, onRetry: (model: String) -> Unit) {
    // In the order of the settings, unlike the brief preview: equal translations are ranked by it.
    val done = state.models.mapNotNull { model -> (state.results[model] as? ModelResult.Done)?.let { model to it.translation } }
    val combined = remember(done) { combine(done) }
    val pending = state.pending
    val failed = state.models.mapNotNull { model -> (state.results[model] as? ModelResult.Failed)?.let { model to it.error } }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (done.isNotEmpty()) {
            // Lets the translations be copied.
            SelectionContainer { Translations(combined) }
        }
        if (pending.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = pluralStringResource(
                        R.plurals.translation_waiting,
                        pending.size,
                        pending.size,
                        pending.joinToString { it.shortModelName() },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        for ((model, error) in failed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.shortModelName(), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = errorText(error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onRetry(model) }) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.retry))
                }
            }
        }
    }
}

@Composable
private fun Translations(combined: CombinedTranslation) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            combined.variants.forEachIndexed { index, variant ->
                Column {
                    Text(
                        text = variant.text,
                        style = if (index == 0) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = variant.models.joinToString { it.shortModelName() },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (combined.alternatives.isNotEmpty()) {
            Section(R.string.translation_alternatives) {
                Text(combined.alternatives.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (combined.notes.isNotEmpty()) {
            Section(R.string.translation_notes) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((model, note) in combined.notes) {
                        Column {
                            Text(note, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = model.shortModelName(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable () -> Unit) {
    Column {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

@Composable
internal fun errorText(error: TranslationError): String = when (error.kind) {
    TranslationError.Kind.INVALID_KEY -> stringResource(R.string.error_invalid_key)
    TranslationError.Kind.NO_CREDITS -> stringResource(R.string.error_no_credits)
    TranslationError.Kind.RATE_LIMITED -> stringResource(R.string.error_rate_limited)
    TranslationError.Kind.TIMEOUT -> stringResource(R.string.error_timeout)
    TranslationError.Kind.NETWORK -> stringResource(R.string.error_network)
    TranslationError.Kind.BAD_REPLY -> stringResource(R.string.error_bad_reply)
    TranslationError.Kind.OTHER -> error.details ?: stringResource(R.string.error_unknown)
}

/** "google/gemma-4-31b-it:free" → "gemma-4-31b-it": the vendor and the free tier only take room. */
fun String.shortModelName(): String = substringAfter('/').removeSuffix(":free")
