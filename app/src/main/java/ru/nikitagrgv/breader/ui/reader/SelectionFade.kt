package ru.nikitagrgv.breader.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ru.nikitagrgv.breader.translation.BookSelection

/** See-through, as it's under the text in every theme. Not the color of the selection, since it can't be acted on. */
internal val SelectionFadeColor = Color(0xFFFF9800).copy(alpha = 0.6f)

/** How far from the marked text a tap still counts as one on it: a short word is smaller than a finger. */
internal val MarkTouchReach = 8.dp

/**
 * Where the selection was when something took it away: a dialog about it, or going on to another one in the history.
 * The place is marked while the dialog is open and fades out after that, so that the place in the book isn't lost.
 * A match found by searching the book is marked the same way, while the search is open. A tap on the mark can select
 * the place again, see [selectMarkedOnTap].
 */
@Stable
internal class SelectionFade {
    private val animatedAlpha = Animatable(1f)

    /** Null when nothing is marked. */
    var place by mutableStateOf<BookSelection?>(null)

    /** Changes with every frame, so it's only to be read while drawing. */
    val alpha: Float get() = animatedAlpha.value

    /** Marks the place fully, and fades it out unless there's still a dialog over it. */
    suspend fun show(dialogOpen: Boolean, durationMs: Int) {
        animatedAlpha.snapTo(1f)
        if (dialogOpen) return
        animatedAlpha.animateTo(0f, tween(durationMs, easing = FastOutLinearInEasing))
        place = null
    }
}

/**
 * Runs [fade]. The dialog is reported open a little later than the place is set, which only restarts the fading before
 * it can be noticed. If no dialog gets opened after all, the place just fades out.
 */
@Composable
internal fun SelectionFadeEffect(fade: SelectionFade, dialogOpen: Boolean, durationMs: Int) {
    val place = fade.place
    LaunchedEffect(place, dialogOpen) {
        if (place != null) fade.show(dialogOpen, durationMs)
    }
}

/**
 * Selects [place], which is marked, again, so that what was to be done with the selection can be done after all, and
 * takes the mark away, as the selection is there instead.
 * @return whether anything got selected. Text laid out by the reader can only be selected where it's composed, so a
 *   selection of several paragraphs whose start is far off the screen can't be.
 */
internal fun ReaderSelection.selectMarked(place: BookSelection, fade: SelectionFade): Boolean {
    select(place, partly = true)
    if (!hasSelection) return false
    fade.place = null
    return true
}

/**
 * Selects the marked place again when it's tapped. What counts is that it was marked when the finger came down, as the
 * mark may be about to go. The tap is taken from what's below only if something got selected, which is how a tap
 * that clears the selection or toggles the bars is told apart from it.
 * @param hits whether a point of this element is on the part of the place shown in it.
 * @param select selects the place, see [selectMarked].
 */
internal fun Modifier.selectMarkedOnTap(
    fade: SelectionFade,
    hits: (BookSelection, Offset) -> Boolean,
    select: (BookSelection) -> Boolean,
): Modifier = pointerInput(fade, hits, select) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val place = fade.place?.takeIf { hits(it, down.position) } ?: return@awaitEachGesture
        // Not a long press, which selects a word instead, nor a scroll or a pinch, which takes the touch.
        val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
            ?: return@awaitEachGesture
        if (select(place)) up.consume()
    }
}
