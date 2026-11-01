package ru.nikitagrgv.breader.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.AppSettings
import ru.nikitagrgv.breader.data.AppTheme
import ru.nikitagrgv.breader.data.SeekBarPosition
import ru.nikitagrgv.breader.data.TranslationSettings
import ru.nikitagrgv.breader.ui.reader.ReaderTextPreview
import ru.nikitagrgv.breader.ui.reader.toReaderSettings
import kotlin.math.roundToInt

private val THEMES = listOf(AppTheme.DARK to R.string.theme_dark, AppTheme.LIGHT to R.string.theme_light)

private val SEEK_BAR_POSITIONS = listOf(
    SeekBarPosition.LEFT to R.string.position_left,
    SeekBarPosition.RIGHT to R.string.position_right,
    SeekBarPosition.TOP to R.string.position_top,
    SeekBarPosition.BOTTOM to R.string.position_bottom,
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    contentPadding: PaddingValues,
    onThemeChange: (AppTheme) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onJustifyChange: (Boolean) -> Unit,
    onHyphenationChange: (Boolean) -> Unit,
    onSeekBarPositionChange: (SeekBarPosition) -> Unit,
    onTranslationChange: ((TranslationSettings) -> TranslationSettings) -> Unit,
    cachedTranslations: Int,
    onClearTranslationCache: () -> Unit,
    dictionaries: Int,
    onOpenDictionaries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Follows the slider while it's dragged; saved when the finger is lifted.
    var fontSize by remember(settings.fontSize) { mutableFloatStateOf(settings.fontSize) }

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        SectionHeader(R.string.settings_theme)
        Column(Modifier.selectableGroup()) {
            for ((theme, label) in THEMES) {
                val selected = settings.theme == theme
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { onThemeChange(theme) },
                )
            }
        }

        SectionHeader(R.string.settings_text)
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.font_size),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.font_size_value, fontSize.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                onValueChangeFinished = { onFontSizeChange(fontSize) },
                valueRange = AppSettings.MIN_FONT_SIZE..AppSettings.MAX_FONT_SIZE,
                // One step per sp.
                steps = (AppSettings.MAX_FONT_SIZE - AppSettings.MIN_FONT_SIZE).toInt() - 1,
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.justify)) },
            supportingContent = { Text(stringResource(R.string.justify_summary)) },
            trailingContent = { Switch(checked = settings.justify, onCheckedChange = null) },
            modifier = Modifier.toggleable(
                value = settings.justify,
                role = Role.Switch,
                onValueChange = onJustifyChange,
            ),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.hyphenation)) },
            supportingContent = { Text(stringResource(R.string.hyphenation_summary)) },
            trailingContent = { Switch(checked = settings.hyphenation, onCheckedChange = null) },
            modifier = Modifier.toggleable(
                value = settings.hyphenation,
                role = Role.Switch,
                onValueChange = onHyphenationChange,
            ),
        )

        // Rendered with the reader's own text style, so the settings above can be judged right here.
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            ReaderTextPreview(
                text = stringResource(R.string.text_preview),
                settings = settings.copy(fontSize = fontSize).toReaderSettings(),
                modifier = Modifier.padding(12.dp),
            )
        }

        SectionHeader(R.string.settings_seek_bar)
        Text(
            text = stringResource(R.string.seek_bar_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SEEK_BAR_POSITIONS.forEachIndexed { index, (position, label) ->
                SegmentedButton(
                    selected = settings.seekBarPosition == position,
                    onClick = { onSeekBarPositionChange(position) },
                    shape = SegmentedButtonDefaults.itemShape(index, SEEK_BAR_POSITIONS.size),
                    label = { Text(stringResource(label)) },
                )
            }
        }

        SectionHeader(R.string.settings_translation)
        TranslationSection(settings.translation, onTranslationChange, cachedTranslations, onClearTranslationCache)

        SectionHeader(R.string.dictionaries_title)
        ListItem(
            headlineContent = { Text(stringResource(R.string.manage_dictionaries)) },
            supportingContent = {
                Text(
                    if (dictionaries == 0) {
                        stringResource(R.string.dictionaries_summary_none)
                    } else {
                        pluralStringResource(R.plurals.dictionaries_summary, dictionaries, dictionaries)
                    },
                )
            },
            modifier = Modifier.clickable(onClick = onOpenDictionaries),
        )
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
