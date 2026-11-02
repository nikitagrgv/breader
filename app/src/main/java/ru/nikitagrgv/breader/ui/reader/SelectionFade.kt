package ru.nikitagrgv.breader.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import ru.nikitagrgv.breader.translation.BookSelection

/** See-through, as it's under the text in every theme. Not the color of the selection, since it can't be acted on. */
internal val SelectionFadeColor = Color(0xFFFF9800).copy(alpha = 0.6f)

/**
 * Where the selection was when a dialog about it took it away. The place is marked while the dialog is open and fades
 * out after that, so that the place in the book isn't lost.
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
internal fun SelectionFadeEffect(fade: SelectionFade, dialogOpen: Boolean, durationMs: Int, hasSelection: Boolean) {
    val place = fade.place
    LaunchedEffect(place, dialogOpen) {
        if (place != null) fade.show(dialogOpen, durationMs)
    }
    // A new selection is where the reader is now.
    LaunchedEffect(hasSelection) {
        if (hasSelection) fade.place = null
    }
}
