package ru.nikitagrgv.breader.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.sp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.AppSettings
import ru.nikitagrgv.breader.data.AppTheme
import ru.nikitagrgv.breader.data.ReaderFont
import ru.nikitagrgv.breader.data.SeekBarPosition
import ru.nikitagrgv.breader.data.TranslationSettings
import ru.nikitagrgv.breader.ui.reader.ReaderTextPreview
import ru.nikitagrgv.breader.ui.reader.fontFamily
import ru.nikitagrgv.breader.ui.reader.toReaderSettings
import kotlin.math.roundToInt

private val THEMES = listOf(
    AppTheme.DARK to R.string.theme_dark,
    AppTheme.BLACK to R.string.theme_black,
    AppTheme.LIGHT to R.string.theme_light,
    AppTheme.SEPIA to R.string.theme_sepia,
)

/** Pairs of a font's name and what it's good for. */
private val FONTS = mapOf(
    ReaderFont.SANS to (R.string.font_sans to R.string.font_sans_summary),
    ReaderFont.SERIF to (R.string.font_serif to R.string.font_serif_summary),
    ReaderFont.LITERATA to (R.string.font_literata to R.string.font_literata_summary),
    ReaderFont.ATKINSON to (R.string.font_atkinson to R.string.font_atkinson_summary),
)

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
    onFontChange: (ReaderFont) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onMarginsChange: (Int) -> Unit,
    onJustifyChange: (Boolean) -> Unit,
    onHyphenationChange: (Boolean) -> Unit,
    onSeekBarPositionChange: (SeekBarPosition) -> Unit,
    onSelectionFadeChange: (Int) -> Unit,
    onSelectionHistorySizeChange: (Int) -> Unit,
    onTranslationChange: ((TranslationSettings) -> TranslationSettings) -> Unit,
    cachedTranslations: Int,
    onClearTranslationCache: () -> Unit,
    dictionaries: Int,
    onOpenDictionaries: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Follow the sliders while they're dragged; saved when the finger is lifted.
    var fontSize by remember(settings.fontSize) { mutableFloatStateOf(settings.fontSize) }
    var lineSpacing by remember(settings.lineSpacing) { mutableFloatStateOf(settings.lineSpacing) }
    var margins by remember(settings.margins) { mutableFloatStateOf(settings.margins.toFloat()) }
    var selectionFade by remember(settings.selectionFadeMs) { mutableFloatStateOf(settings.selectionFadeMs.toFloat()) }
    var selectionHistorySize by remember(settings.selectionHistorySize) { mutableFloatStateOf(settings.selectionHistorySize.toFloat()) }

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
            Text(stringResource(R.string.font), style = MaterialTheme.typography.bodyLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((font, texts) in FONTS) {
                    FilterChip(
                        selected = settings.font == font,
                        onClick = { onFontChange(font) },
                        // In its own font, so the choice can be seen.
                        label = { Text(stringResource(texts.first), fontFamily = font.fontFamily(14.sp)) },
                    )
                }
            }
            Text(
                text = stringResource(FONTS.getValue(settings.font).second),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SliderSetting(
            title = R.string.font_size,
            value = stringResource(R.string.font_size_value, fontSize.roundToInt()),
            position = fontSize,
            onPositionChange = { fontSize = it },
            onPositionChangeFinished = { onFontSizeChange(fontSize) },
            range = AppSettings.MIN_FONT_SIZE..AppSettings.MAX_FONT_SIZE,
            // One step per sp.
            steps = (AppSettings.MAX_FONT_SIZE - AppSettings.MIN_FONT_SIZE).toInt() - 1,
        )
        SliderSetting(
            title = R.string.line_spacing,
            summary = R.string.line_spacing_summary,
            value = stringResource(R.string.line_spacing_value, lineSpacing),
            position = lineSpacing,
            onPositionChange = { lineSpacing = it },
            onPositionChangeFinished = { onLineSpacingChange((lineSpacing * 10).roundToInt() / 10f) },
            range = AppSettings.MIN_LINE_SPACING..AppSettings.MAX_LINE_SPACING,
            // One step per 0.1.
            steps = ((AppSettings.MAX_LINE_SPACING - AppSettings.MIN_LINE_SPACING) * 10).roundToInt() - 1,
        )
        SliderSetting(
            title = R.string.margins,
            summary = R.string.margins_summary,
            value = stringResource(R.string.margins_value, margins.roundToInt()),
            position = margins,
            onPositionChange = { margins = it },
            onPositionChangeFinished = { onMarginsChange(margins.roundToInt()) },
            range = 0f..AppSettings.MAX_MARGINS.toFloat(),
            steps = AppSettings.MAX_MARGINS / AppSettings.MARGINS_STEP - 1,
        )
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
                settings = settings.copy(fontSize = fontSize, lineSpacing = lineSpacing).toReaderSettings(),
                modifier = Modifier.padding(horizontal = margins.dp, vertical = 12.dp),
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

        SectionHeader(R.string.settings_selection)
        SliderSetting(
            title = R.string.selection_fade,
            summary = R.string.selection_fade_summary,
            value = if (selectionFade.roundToInt() == 0) {
                stringResource(R.string.selection_fade_off)
            } else {
                stringResource(R.string.selection_fade_value, selectionFade / 1000)
            },
            position = selectionFade,
            onPositionChange = { selectionFade = it },
            onPositionChangeFinished = { onSelectionFadeChange(selectionFade.roundToInt()) },
            range = 0f..AppSettings.MAX_SELECTION_FADE_MS.toFloat(),
            steps = AppSettings.MAX_SELECTION_FADE_MS / AppSettings.SELECTION_FADE_STEP_MS - 1,
        )
        SliderSetting(
            title = R.string.selection_history_size,
            summary = R.string.selection_history_size_summary,
            value = selectionHistorySize.roundToInt().let { pluralStringResource(R.plurals.selection_history_size_value, it, it) },
            position = selectionHistorySize,
            onPositionChange = { selectionHistorySize = it },
            onPositionChangeFinished = { onSelectionHistorySizeChange(selectionHistorySize.roundToInt()) },
            range = AppSettings.MIN_SELECTION_HISTORY_SIZE.toFloat()..AppSettings.MAX_SELECTION_HISTORY_SIZE.toFloat(),
            steps = (AppSettings.MAX_SELECTION_HISTORY_SIZE - AppSettings.MIN_SELECTION_HISTORY_SIZE) / AppSettings.SELECTION_HISTORY_SIZE_STEP - 1,
        )

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

/** A setting set with a slider: its [title] and [value] above, the [summary] under them. */
@Composable
private fun SliderSetting(
    @StringRes title: Int,
    value: String,
    position: Float,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: () -> Unit,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    @StringRes summary: Int? = null,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary != null) {
            Text(
                text = stringResource(summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = position,
            onValueChange = onPositionChange,
            onValueChangeFinished = onPositionChangeFinished,
            valueRange = range,
            steps = steps,
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
