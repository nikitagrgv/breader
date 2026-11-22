package ru.nikitagrgv.breader.ui.translation

import androidx.annotation.StringRes
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.translation.MachineTranslationService
import ru.nikitagrgv.breader.ui.BottomSheet
import java.util.Locale

/** The translation of the selected text by a free service; the service can be switched right here. */
@Composable
fun MachineTranslationSheet(
    state: MachineTranslationState,
    onServiceChange: (MachineTranslationService) -> Unit,
    onRetry: () -> Unit,
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
                text = state.text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                for (service in MachineTranslationService.entries) {
                    FilterChip(
                        selected = service == state.service,
                        onClick = { onServiceChange(service) },
                        label = { Text(stringResource(service.title)) },
                    )
                }
            }
            when (val result = state.result) {
                null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is MachineResult.Done -> {
                    val translation = result.translation
                    SelectionContainer {
                        Text(
                            text = translation.text,
                            // A word or a phrase stands out; a paragraph is to be read.
                            style = if (translation.text.length <= SHORT_TEXT) {
                                MaterialTheme.typography.headlineSmall
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                        )
                    }
                    val detected = translation.detectedLanguage?.takeIf { state.from == null }
                    if (detected != null) {
                        Text(
                            text = stringResource(R.string.machine_translation_from, Locale.forLanguageTag(detected).displayLanguage),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                is MachineResult.Failed -> {
                    Text(
                        text = errorText(result.error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(16.dp))
                    FilledTonalButton(onClick = onRetry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}

@get:StringRes
val MachineTranslationService.title: Int
    get() = when (this) {
        MachineTranslationService.GOOGLE -> R.string.machine_google
        MachineTranslationService.MYMEMORY -> R.string.machine_mymemory
    }

private const val SHORT_TEXT = 60
