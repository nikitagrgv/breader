package ru.nikitagrgv.breader.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R
import ru.nikitagrgv.breader.data.Book
import ru.nikitagrgv.breader.data.PERCENTS
import ru.nikitagrgv.breader.data.ReadingStats
import ru.nikitagrgv.breader.ui.BottomSheet
import ru.nikitagrgv.breader.ui.reader.formatCount
import ru.nikitagrgv.breader.ui.reader.formatReadingTime
import ru.nikitagrgv.breader.ui.reader.wordsProgress
import kotlin.math.roundToInt

private enum class StatsMode { TIME, SPEED }

/**
 * How a book is read: the time spent at each percent of it and the speed there, in words per hour, with the totals
 * above. A bar is told in full by touching it.
 */
@Composable
fun ReadingStatsSheet(book: Book, onDismiss: () -> Unit) {
    val stats = remember(book) { ReadingStats(book) }
    var mode by rememberSaveable { mutableStateOf(StatsMode.TIME) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val noTime = stringResource(R.string.stats_under_minute)
    fun time(ms: Long) = formatReadingTime(ms) ?: noTime

    BottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatTile(
                    label = stringResource(R.string.stats_read),
                    value = stringResource(R.string.progress_percent, book.progress * 100),
                    detail = stats.wordCount?.let { wordsProgress(stats.wordsRead ?: 0, it) } ?: stringResource(R.string.stats_words_unknown),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.stats_time),
                    value = time(stats.totalMs),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatTile(
                    label = stringResource(R.string.stats_speed),
                    value = stats.averageSpeed?.let { speedText(it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.stats_time_left),
                    value = stats.timeLeftMs?.let { time(it) } ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(24.dp))

            if (stats.trackedMs == 0L) {
                Text(
                    text = stringResource(R.string.stats_nothing_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                StatsMode.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = mode == item,
                        onClick = { mode = item },
                        // Speeds need the words counted.
                        enabled = item == StatsMode.TIME || stats.wordCount != null,
                        shape = SegmentedButtonDefaults.itemShape(index, StatsMode.entries.size),
                        label = { Text(stringResource(if (item == StatsMode.TIME) R.string.stats_time_per_percent else R.string.stats_speed_per_percent)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            val values: List<Double?> = when (mode) {
                StatsMode.TIME -> stats.times.map { ms -> ms.takeIf { it > 0 }?.toDouble() }
                StatsMode.SPEED -> stats.speeds
            }
            // The value of the touched bar, in the place of a tooltip, which a finger would cover.
            val percent = selected
            Text(
                text = if (percent == null) {
                    stringResource(R.string.stats_touch_hint)
                } else {
                    val time = stats.times[percent]
                    val speed = stats.speeds[percent]
                    listOfNotNull(
                        stringResource(R.string.stats_percent, percent, percent + 1),
                        if (time > 0) time(time) else stringResource(R.string.stats_not_read),
                        speed?.let { speedText(it) },
                        stringResource(R.string.stats_here).takeIf { percent == stats.currentPercent },
                    ).joinToString(" · ")
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (percent == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            val max = values.filterNotNull().maxOrNull() ?: 0.0
            Text(
                text = when {
                    max <= 0 -> ""
                    mode == StatsMode.TIME -> time(max.toLong())
                    else -> speedText(max)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PercentChart(
                values = values,
                max = max,
                current = stats.currentPercent,
                average = if (mode == StatsMode.SPEED) stats.averageSpeed else null,
                selected = selected,
                onSelect = { selected = it },
                description = stringResource(if (mode == StatsMode.TIME) R.string.stats_time_per_percent else R.string.stats_speed_per_percent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
            Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                for ((share, alignment) in listOf(0 to Alignment.CenterStart, 50 to Alignment.Center, 100 to Alignment.CenterEnd)) {
                    Text(
                        text = "$share%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(alignment),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    if (mode == StatsMode.TIME) R.string.stats_time_summary else R.string.stats_speed_summary,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (stats.trackedMs < stats.totalMs && mode == StatsMode.TIME) {
                Text(
                    text = stringResource(R.string.stats_tracked_since, time(stats.trackedMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun speedText(wordsPerHour: Double): String = stringResource(R.string.stats_words_per_hour, formatCount(wordsPerHour.roundToInt()))

/**
 * A column per percent of the book, growing from the baseline; none where there's no value. The percent the reader is
 * at is marked with a hairline, and [average], if there is one, with another across.
 */
@Composable
private fun PercentChart(
    values: List<Double?>,
    max: Double,
    current: Int,
    average: Double?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val markColor = MaterialTheme.colorScheme.onSurfaceVariant
    fun percentAt(x: Float, width: Int): Int = (x / width * PERCENTS).toInt().coerceIn(0, PERCENTS - 1)
    Canvas(
        modifier
            .semantics { contentDescription = description }
            // The whole height of a column is its touch target, not just its bar, which may be tiny.
            .pointerInput(Unit) {
                detectTapGestures { onSelect(percentAt(it.x, size.width)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> onSelect(percentAt(change.position.x, size.width)) }
            },
    ) {
        val slot = size.width / PERCENTS
        // The surface between columns separates them, as no stroke should.
        val gap = minOf(1.dp.toPx(), slot / 2)
        val barWidth = slot - gap
        val radius = minOf(4.dp.toPx() / 2, barWidth / 2)
        val hairline = 1.dp.toPx()
        // Recessive: the baseline and the top of the scale.
        drawLine(lineColor, Offset(0f, 0f), Offset(size.width, 0f), hairline)
        drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), hairline)
        if (max > 0) {
            values.forEachIndexed { index, value ->
                if (value == null || value <= 0) return@forEachIndexed
                val height = (value / max * size.height).toFloat().coerceAtLeast(hairline * 2)
                val left = index * slot + gap / 2
                val bar = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = size.height - height,
                            right = left + barWidth,
                            bottom = size.height,
                            topLeftCornerRadius = CornerRadius(radius),
                            topRightCornerRadius = CornerRadius(radius),
                        ),
                    )
                }
                // With one touched, the rest step back.
                val alpha = if (selected == null || selected == index) 1f else 0.35f
                drawPath(bar, barColor, alpha = alpha)
            }
            if (average != null && average <= max) {
                val y = size.height - (average / max * size.height).toFloat()
                drawLine(markColor, Offset(0f, y), Offset(size.width, y), hairline)
            }
        }
        val here = (current + 0.5f) * slot
        drawLine(markColor.copy(alpha = 0.6f), Offset(here, 0f), Offset(here, size.height), hairline)
    }
}
