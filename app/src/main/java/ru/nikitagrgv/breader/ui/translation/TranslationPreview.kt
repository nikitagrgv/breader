package ru.nikitagrgv.breader.ui.translation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.dictionary.BriefEntry
import ru.nikitagrgv.breader.dictionary.dictionaryKey
import ru.nikitagrgv.breader.translation.translationKey

/**
 * Brief translations of the selected text, shown at the top. The meanings from the dictionaries come first,
 * a line per part of speech, marked with an icon; they're found at once. Then one line per distinct translation
 * of the models, in the order they came. New ones are added at the end, and a repeated one adds nothing, so the
 * lines being read don't move. Takes only the room it needs and scrolls when limited by the height from [modifier].
 * @param onOpenDictionary called on a tap on the meanings, to show the dictionary entries in full.
 * @param cornerSpace what's to be left free in the top right corner, for what's shown over it.
 */
@Composable
fun TranslationPreview(
    state: TranslationState,
    onOpenDictionary: () -> Unit,
    modifier: Modifier = Modifier,
    cornerSpace: DpSize = DpSize.Zero,
) {
    val brief = state.dictionaryBrief
    val translations = state.results.values
        .mapNotNull { (it as? ModelResult.Done)?.translation?.text }
        .distinctBy { it.translationKey() }
    val failed = state.results.values.count { it is ModelResult.Failed }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .heightIn(min = cornerSpace.height)
                .absolutePadding(left = 16.dp, right = 16.dp + cornerSpace.width, top = 8.dp, bottom = 8.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (brief.isNotEmpty()) {
                    Row(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = stringResource(R.string.dictionary), onClick = onOpenDictionary),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_dictionary),
                            contentDescription = stringResource(R.string.from_dictionary),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Centered on the first line of the text.
                            modifier = Modifier
                                .padding(top = 3.dp, end = 8.dp)
                                .size(18.dp),
                        )
                        DictionaryBrief(brief, looksUp = state.excerpt.fragment)
                    }
                    // Without it, a short translation under a short dictionary line reads as its continuation.
                    if (translations.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                for (text in translations) {
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
            // Outside of the scrolled lines, so it's always seen. Only while the models are asked, not for cached results.
            if (state.modelsAsked && state.models.isNotEmpty()) Column(horizontalAlignment = Alignment.End) {
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

/**
 * A line per part of speech: its name, then the meanings set apart by dots, as a meaning may have commas of its own.
 * The word comes first where it isn't the one that [looksUp], such as the base form of an inflected one.
 */
@Composable
private fun DictionaryBrief(brief: List<BriefEntry>, looksUp: String) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val key = dictionaryKey(looksUp.trim { !it.isLetterOrDigit() })
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (entry in brief) {
            Text(
                text = buildAnnotatedString {
                    if (dictionaryKey(entry.word) != key) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(entry.word) }
                        append(' ')
                    }
                    if (entry.pos.isNotEmpty()) {
                        withStyle(SpanStyle(color = secondary, fontStyle = FontStyle.Italic)) { append(entry.pos) }
                        append("  ")
                    }
                    entry.meanings.forEachIndexed { index, meaning ->
                        // The no-break space keeps the dot at the end of a line, by the meaning before it.
                        if (index > 0) withStyle(SpanStyle(color = secondary)) { append(" · ") }
                        append(meaning)
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
