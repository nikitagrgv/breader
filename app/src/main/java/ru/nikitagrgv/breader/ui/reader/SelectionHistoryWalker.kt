package ru.nikitagrgv.breader.ui.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import ru.nikitagrgv.breader.data.ReadingPosition
import ru.nikitagrgv.breader.translation.BookSelection
import kotlin.math.roundToInt

/** Quick, but long enough to see which way the text goes. */
internal const val JUMP_ANIMATION_MS = 280

/** For the rest of the way in a long paragraph, whose layout isn't known before getting there. */
private const val JUMP_FINISH_ANIMATION_MS = 150

/** Where a selection jumped to ends up, as part of the screen: clear of what's shown at the top and at the bottom. */
private const val JUMP_LANDING = 0.4f

/** Takes the reader through the [SelectionHistoryState], selecting what's there and bringing it into view. */
@Stable
internal class SelectionHistoryWalker(
    private val state: SelectionHistoryState,
    private val selection: ReaderSelection,
    private val list: LazyListState,
    private val viewport: PdfViewport,
    private val fade: SelectionFade,
    private val scope: CoroutineScope,
) {
    /** Whether something is about to be selected, once it's on the screen, which is when nothing may be selected. */
    var isSelectionOnItsWay by mutableStateOf(false)
        private set

    private var job: Job? = null

    /**
     * @param fadeMs how long the fragment that's left fades out for; 0 for it not to be shown.
     * @param freeArea what part of the screen isn't covered by anything, in px; what's in there isn't scrolled to.
     */
    fun walk(to: SelectionHistory, fadeMs: Int, freeArea: IntRange) {
        val origin = state.origin ?: here()
        val previous = selection.locate()
        state.update(to)
        if (to.isWalking) {
            state.origin = origin
            state.holdsPosition = true
        }
        val range = to.current
        job?.cancel()
        isSelectionOnItsWay = range != null
        job = scope.launch {
            val thisJob = currentCoroutineContext().job
            try {
                val target = range?.let {
                    selection.prepare(it)
                    it.inBook(selection.paragraphs)
                }
                if (range != null && target == null) {
                    state.update(state.history.without(range))
                    return@launch
                }
                if (previous != null && fadeMs > 0) fade.place = previous
                val selected = target != null && select(target, partly = false)
                if (!selected) selection.clear()
                try {
                    if (to.isWalking && target != null) bringIntoView(target, freeArea) else bringBack(origin)
                } catch (e: CancellationException) {
                    // A finger has stopped the scrolling, unless this jump is cancelled itself.
                    currentCoroutineContext().ensureActive()
                }
                if (target != null && !selected) select(target, partly = true)
            } finally {
                if (job === thisJob) isSelectionOnItsWay = false
            }
        }
    }

    private fun here() = ReadingPosition(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, viewport.zoom, viewport.pan.roundToInt())

    private fun select(target: BookSelection, partly: Boolean): Boolean {
        val selected = selection.select(target, partly)
        state.selectedByWalk = selection.locate()?.range
        return selected
    }

    private suspend fun bringIntoView(target: BookSelection, freeArea: IntRange) {
        val landing = (list.layoutInfo.viewportSize.height * JUMP_LANDING).roundToInt()
        val start = target.start.paragraph
        when (selection) {
            is PdfSelection -> {
                val rect = selection.book.loadedText(start)?.let { target.rectsOn(start, it).firstOrNull() }
                val scale = viewport.pageWidth(viewport.width) / selection.book.pageSizes[start].width
                coroutineScope {
                    launch { list.glideTo(start, landing, freeArea) { ((rect?.top ?: 0f) * scale).roundToInt() } }
                    if (rect != null) launch { viewport.animatePanToShow(rect.left * scale, rect.right * scale) }
                }
            }
            is TextSelection -> {
                // A place in a paragraph is known only roughly until it's laid out, which is good enough for landing.
                val share = target.start.offset.toFloat() / selection.paragraphs[start].text.length.coerceAtLeast(1)
                list.glideTo(start, landing, freeArea) { size -> ((size ?: 0) * share).roundToInt() }
            }
        }
    }

    private suspend fun bringBack(origin: ReadingPosition) = coroutineScope {
        launch { viewport.animatePanTo(origin.pan.toFloat()) }
        list.glideTo(origin.paragraph, list.layoutInfo.beforeContentPadding, IntRange.EMPTY) { origin.offset }
        // Exactly where it was, whatever the rounding on the way.
        list.scrollToItem(origin.paragraph, origin.offset)
    }
}

/**
 * Brings a place in an item to [landing] on the screen with a quick animation, unless it's in [freeArea] already.
 * What's far away is reached by first jumping to one screen from it: the whole way would take long or be a blur, and
 * this takes the same short time however far it is, while still showing which way the text has gone.
 * @param placeIn where the place is in the item, given the height of the item if it's known.
 */
private suspend fun LazyListState.glideTo(index: Int, landing: Int, freeArea: IntRange, placeIn: (size: Int?) -> Int) {
    val spec = tween<Float>(JUMP_ANIMATION_MS, easing = FastOutSlowInEasing)
    fun placeOnScreen(): Int? {
        val layout = layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return null
        return item.offset - layout.viewportStartOffset + placeIn(item.size)
    }
    val place = placeOnScreen()
    if (place != null) {
        if (place !in freeArea) animateScrollBy((place - landing).toFloat(), spec)
        return
    }
    val height = layoutInfo.viewportSize.height
    val direction = if (index > firstVisibleItemIndex) 1 else -1
    scrollToItem(index, layoutInfo.beforeContentPadding - (landing - placeIn(null) + direction * height))
    animateScrollBy((direction * height).toFloat(), spec)
    // In a long paragraph, the start of it having arrived may still not be enough.
    val landed = placeOnScreen() ?: return
    if (landed !in freeArea) {
        animateScrollBy((landed - landing).toFloat(), tween(JUMP_FINISH_ANIMATION_MS, easing = FastOutSlowInEasing))
    }
}
