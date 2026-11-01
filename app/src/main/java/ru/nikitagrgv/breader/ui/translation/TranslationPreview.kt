package ru.nikitagrgv.breader.ui.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.translation.translationKey

/**
 * Brief translations of the selected text, shown at the top. The meanings from the dictionaries come first,
 * all in one line marked with an icon; they're found at once. Then one line per distinct translation of the
 * models, in the order they came. New ones are added at the end, and a repeated one adds nothing, so the lines
 * being read don't move. Takes only the room it needs and scrolls when limited by the height from [modifier].
 */
@Composable
fun TranslationPreview(state: TranslationState, modifier: Modifier = Modifier) {
    val dictionaryLine = state.dictionaryLine
    val translations = state.results.values
        .mapNotNull { (it as? ModelResult.Done)?.translation?.text }
        .distinctBy { it.translationKey() }
    val failed = state.results.values.count { it is ModelResult.Failed }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (dictionaryLine != null) {
                    Row {
                        Icon(
                            painterResource(R.drawable.ic_dictionary),
                            contentDescription = stringResource(R.string.from_dictionary),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Centered on the first line of the text.
                            modifier = Modifier
                                .padding(top = 3.dp, end = 8.dp)
                                .size(18.dp),
                        )
                        Text(dictionaryLine, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                for (text in translations) {
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
            // Outside of the scrolled lines, so it's always seen. Only about the models.
            if (state.models.isNotEmpty()) Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.translation_progress, state.results.size, state.models.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (failed > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.translation_failed, failed, failed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
