package ru.nikitagrgv.breader.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.R

private val TrackInset = 20.dp
private val Thickness = 40.dp

/**
 * Slider for jumping through the book: drag the thumb or tap anywhere on the track.
 * The start of the book is at the top or on the left.
 *
 * @param fraction the current position in the book, 0..1.
 * @param vertical whether the track goes from top to bottom rather than from left to right.
 * @param onSeek called with the requested position, 0..1, while the slider is touched.
 */
@Composable
fun BookSeekBar(
    fraction: Float,
    vertical: Boolean,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekFinished by rememberUpdatedState(onSeekFinished)
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.book_position)

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = if (vertical) modifier.width(Thickness) else modifier.height(Thickness),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { value ->
                        currentOnSeek(value.coerceIn(0f, 1f))
                        currentOnSeekFinished()
                        true
                    }
                }
                .pointerInput(vertical) {
                    val inset = TrackInset.toPx()
                    fun fractionAt(position: Offset): Float {
                        val along = if (vertical) position.y else position.x
                        val length = if (vertical) size.height else size.width
                        return ((along - inset) / (length - 2 * inset)).coerceIn(0f, 1f)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        currentOnSeek(fractionAt(down.position))
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            currentOnSeek(fractionAt(change.position))
                        }
                        currentOnSeekFinished()
                    }
                },
        ) {
            // Distances along the track become points on its center line.
            fun point(along: Float) = if (vertical) Offset(size.width / 2, along) else Offset(along, size.height / 2)
            val start = TrackInset.toPx()
            val end = (if (vertical) size.height else size.width) - start
            val thumb = start + (end - start) * fraction.coerceIn(0f, 1f)
            val stroke = 4.dp.toPx()
            drawLine(trackColor, point(start), point(end), stroke, StrokeCap.Round)
            drawLine(activeColor, point(start), point(thumb), stroke, StrokeCap.Round)
            drawCircle(activeColor, radius = 8.dp.toPx(), center = point(thumb))
        }
    }
}
