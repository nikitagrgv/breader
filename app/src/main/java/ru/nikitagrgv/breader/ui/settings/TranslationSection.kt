package ru.nikitagrgv.breader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.TranslationSettings
import ru.nikitagrgv.breader.ui.translation.shortModelName
import kotlin.math.roundToInt

/**
 * @param onChange gets a function that makes the new settings out of the stored ones.
 * @param cached the number of fragments in the translation cache.
 */
@Composable
fun TranslationSection(
    settings: TranslationSettings,
    onChange: ((TranslationSettings) -> TranslationSettings) -> Unit,
    cached: Int,
    onClearCache: () -> Unit,
) {
    // The fields keep their own text: the stored one comes back a moment later and would move the cursor.
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var keyVisible by rememberSaveable { mutableStateOf(false) }
    var sourceLanguage by remember { mutableStateOf(settings.sourceLanguage) }
    var targetLanguage by remember { mutableStateOf(settings.targetLanguage) }
    // Follows the slider while it's dragged; saved when the finger is lifted.
    var contextWords by remember(settings.contextWords) { mutableFloatStateOf(settings.contextWords.toFloat()) }
    var cacheSize by remember(settings.cacheSize) { mutableFloatStateOf(settings.cacheSize.toFloat()) }
    var pickingModels by rememberSaveable { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = { value ->
                apiKey = value
                onChange { it.copy(apiKey = value) }
            },
            label = { Text(stringResource(R.string.openrouter_key)) },
            supportingText = { Text(htmlString(R.string.openrouter_key_hint)) },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        painterResource(if (keyVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                        contentDescription = stringResource(if (keyVisible) R.string.hide_key else R.string.show_key),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = sourceLanguage,
                onValueChange = { value ->
                    sourceLanguage = value
                    onChange { it.copy(sourceLanguage = value) }
                },
                label = { Text(stringResource(R.string.source_language)) },
                placeholder = { Text(stringResource(R.string.source_language_auto)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = targetLanguage,
                onValueChange = { value ->
                    targetLanguage = value
                    onChange { it.copy(targetLanguage = value) }
                },
                label = { Text(stringResource(R.string.target_language)) },
                placeholder = { Text(TranslationSettings.deviceLanguage()) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
            )
        }
        // The placeholders are only seen in a focused field.
        Text(
            text = stringResource(R.string.languages_hint, TranslationSettings.deviceLanguage()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.translation_models)) },
        supportingContent = {
            Text(
                text = if (settings.models.isEmpty()) {
                    stringResource(R.string.translation_models_none)
                } else {
                    settings.models.joinToString { it.shortModelName() }
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier.clickable { pickingModels = true },
    )

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.context_words),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(R.plurals.context_words_value, contextWords.roundToInt(), contextWords.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.context_words_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = contextWords,
            onValueChange = { contextWords = it },
            onValueChangeFinished = {
                val words = contextWords.roundToInt()
                onChange { it.copy(contextWords = words) }
            },
            valueRange = 0f..TranslationSettings.MAX_CONTEXT_WORDS.toFloat(),
            steps = TranslationSettings.MAX_CONTEXT_WORDS / TranslationSettings.CONTEXT_WORDS_STEP - 1,
        )
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.translation_cache),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            val size = cacheSize.roundToInt()
            Text(
                text = if (size == 0) {
                    stringResource(R.string.translation_cache_off)
                } else {
                    pluralStringResource(R.plurals.translation_cache_value, size, size)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.translation_cache_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = cacheSize,
            onValueChange = { cacheSize = it },
            onValueChangeFinished = {
                val size = cacheSize.roundToInt()
                onChange { it.copy(cacheSize = size) }
            },
            valueRange = 0f..TranslationSettings.MAX_CACHE_SIZE.toFloat(),
            steps = TranslationSettings.MAX_CACHE_SIZE / TranslationSettings.CACHE_SIZE_STEP - 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pluralStringResource(R.plurals.translation_cache_used, cached, cached),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onClearCache, enabled = cached > 0) {
                Text(stringResource(R.string.clear_cache))
            }
        }
    }

    if (pickingModels) {
        ModelPickerDialog(
            selected = settings.models,
            onSave = { models ->
                onChange { it.copy(models = models) }
                pickingModels = false
            },
            onDismiss = { pickingModels = false },
        )
    }
}

@Composable
private fun htmlString(id: Int): AnnotatedString {
    val html = stringResource(id)
    val linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
    return remember(html, linkStyle) { AnnotatedString.fromHtml(html, TextLinkStyles(style = linkStyle)) }
}
