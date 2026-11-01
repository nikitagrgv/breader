package ru.nikitagrgv.breader.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * Vertical slider for jumping through the book: drag the thumb or tap anywhere on the track.
 *
 * @param fraction the current position in the book, 0..1.
 * @param onSeek called with the requested position, 0..1, while the slider is touched.
 */
@Composable
fun BookSeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekFinished by rememberUpdatedState(onSeekFinished)
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.book_position)

    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.width(40.dp)) {
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
                .pointerInput(Unit) {
                    val inset = TrackInset.toPx()
                    fun fractionAt(y: Float) = ((y - inset) / (size.height - 2 * inset)).coerceIn(0f, 1f)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        currentOnSeek(fractionAt(down.position.y))
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            currentOnSeek(fractionAt(change.position.y))
                        }
                        currentOnSeekFinished()
                    }
                },
        ) {
            val inset = TrackInset.toPx()
            val x = size.width / 2
            val top = inset
            val bottom = size.height - inset
            val thumbY = top + (bottom - top) * fraction.coerceIn(0f, 1f)
            val stroke = 4.dp.toPx()
            drawLine(trackColor, Offset(x, top), Offset(x, bottom), stroke, StrokeCap.Round)
            drawLine(activeColor, Offset(x, top), Offset(x, thumbY), stroke, StrokeCap.Round)
            drawCircle(activeColor, radius = 8.dp.toPx(), center = Offset(x, thumbY))
        }
    }
}
