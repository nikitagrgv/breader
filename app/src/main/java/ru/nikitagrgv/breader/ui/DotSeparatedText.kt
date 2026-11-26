package ru.nikitagrgv.breader.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * Short pieces of text in a row, separated by dots, like "17% · 12,300 / 72,000 words · 1h 12m". What doesn't fit in a
 * line goes on to the next one between the pieces, never inside one, and the dot at the break is left out: a number
 * cut in half, or one hidden behind an ellipsis, says nothing. Only a piece too long for a whole line is cut off.
 */
@Composable
fun DotSeparatedText(
    parts: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    Layout(
        content = {
            for (part in parts) Text(part, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // As wide as the spaces around a dot would be.
            repeat((parts.size - 1).coerceAtLeast(0)) {
                Text("·", style = style, color = color, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val pieces = measurables.take(parts.size).map { it.measure(loose) }
        val dots = measurables.drop(parts.size).map { it.measure(loose) }
        // The pieces of each line, by index.
        val lines = ArrayList<MutableList<Int>>()
        var lineWidth = 0
        for ((index, piece) in pieces.withIndex()) {
            val dot = if (index > 0) dots[index - 1].width else 0
            if (lines.isEmpty() || lineWidth + dot + piece.width > constraints.maxWidth) {
                lines += mutableListOf(index)
                lineWidth = piece.width
            } else {
                lines.last() += index
                lineWidth += dot + piece.width
            }
        }
        val widths = lines.map { line -> line.sumOf { pieces[it].width } + line.drop(1).sumOf { dots[it - 1].width } }
        val heights = lines.map { line -> line.maxOf { pieces[it].height } }
        val width = (widths.maxOrNull() ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = heights.sum().coerceIn(constraints.minHeight, if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity)
        layout(width, height) {
            var y = 0
            for ((number, line) in lines.withIndex()) {
                var x = 0
                for (index in line) {
                    if (index != line.first()) {
                        dots[index - 1].placeRelative(x, y)
                        x += dots[index - 1].width
                    }
                    pieces[index].placeRelative(x, y)
                    x += pieces[index].width
                }
                y += heights[number]
            }
        }
    }
}
