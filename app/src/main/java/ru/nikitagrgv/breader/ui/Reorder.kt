package ru.nikitagrgv.breader.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ru.nikitagrgv.breader.R
import java.util.Collections

/**
 * The order of a list whose items are dragged by their [DragHandle]s to other places in it. The order shown follows
 * the dragging, ahead of wherever the order is stored, which only gets the new one once the finger is lifted.
 * Made by [rememberReorderState].
 */
@Stable
class ReorderState internal constructor() {
    /** The keys of the items in the stored order. */
    internal var stored: List<String> = emptyList()
    internal var onReorder: (List<String>) -> Unit = {}

    /** The order being dragged, or dragged and not yet stored; null to show the stored one. */
    private var dragged by mutableStateOf<List<String>?>(null)

    /** The key of the item being dragged. */
    var key by mutableStateOf<String?>(null)
        private set

    /** How far it's moved from its place in the order, in px. */
    var offset by mutableFloatStateOf(0f)
        private set

    /** Where the finger would be if it had stayed at the place of the item in the order, in root px. */
    private var originY = 0f

    /** The heights of the items by key, in px: one is dragged past another by half of its height. */
    internal val heights = HashMap<String, Int>()

    /** The keys of the items in the order to show them in; items added or removed while dragging are too. */
    val order: List<String>
        get() {
            val dragged = dragged ?: return stored
            return dragged.filter { it in stored } + stored.filter { it !in dragged }
        }

    /** @param y where the finger is, in root px. */
    internal fun start(key: String, y: Float) {
        dragged = order
        this.key = key
        originY = y
        offset = 0f
    }

    internal fun dragTo(y: Float) {
        val key = key ?: return
        val order = dragged?.toMutableList() ?: return
        var index = order.indexOf(key)
        if (index < 0) return
        offset = y - originY
        // A fast drag can go past several.
        while (true) {
            val next = order.getOrNull(index + 1)?.let { it to (heights[it] ?: 0) }
            val previous = order.getOrNull(index - 1)?.let { it to (heights[it] ?: 0) }
            if (next != null && offset > next.second / 2f) {
                Collections.swap(order, index, index + 1)
                index++
                // Its place in the order is lower by the height of the one it's gone past.
                originY += next.second
                offset -= next.second
            } else if (previous != null && offset < -previous.second / 2f) {
                Collections.swap(order, index, index - 1)
                index--
                originY -= previous.second
                offset += previous.second
            } else {
                break
            }
        }
        dragged = order
    }

    /** Stores the new order, which is shown until the stored one is it. */
    internal fun end() {
        val order = dragged
        key = null
        offset = 0f
        if (order != null && order != stored) onReorder(order) else dragged = null
    }

    internal fun move(from: Int, to: Int) {
        onReorder(order.toMutableList().also { Collections.swap(it, from, to) })
    }

    /** Shows the stored order, unless one is being dragged. */
    internal fun forgetOrder() {
        if (key == null) dragged = null
    }
}

/**
 * @param stored the keys of the items in the order that's stored.
 * @param onReorder gets the keys in the new order, to store it.
 */
@Composable
fun rememberReorderState(stored: List<String>, onReorder: (List<String>) -> Unit): ReorderState {
    val state = remember { ReorderState() }
    // Not observed: whoever passes them reads the order right after.
    state.stored = stored
    state.onReorder = onReorder
    // Once it has the order that was dragged, or anything else changes it.
    LaunchedEffect(stored) { state.forgetOrder() }
    return state
}

/**
 * What an item of a reorderable list needs.
 * @param modifier to be put on the item: it lifts the item and moves it with the finger while it's dragged.
 * @param handle to be put in the item, by which it's dragged.
 * @param actions for the item's semantics, for those who can't drag.
 */
class ReorderableItem(
    val modifier: Modifier,
    val dragged: Boolean,
    val handle: @Composable () -> Unit,
    val actions: List<CustomAccessibilityAction>,
)

/** The item with the [key] in the [state]'s order. */
@Composable
fun LazyItemScope.reorderableItem(state: ReorderState, key: String): ReorderableItem {
    val order = state.order
    val index = order.indexOf(key)
    val dragged = state.key == key
    val placement = if (dragged) {
        // Read in the layer, so that following the finger takes no recomposition.
        Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationY = state.offset
                shadowElevation = DRAGGED_ELEVATION.toPx()
            }
    } else {
        Modifier.animateItem()
    }
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    return ReorderableItem(
        modifier = placement.onSizeChanged { state.heights[key] = it.height },
        dragged = dragged,
        handle = { DragHandle(onStart = { y -> state.start(key, y) }, onDrag = state::dragTo, onEnd = state::end) },
        actions = listOfNotNull(
            CustomAccessibilityAction(moveUp) { state.move(index, index - 1); true }.takeIf { index > 0 },
            CustomAccessibilityAction(moveDown) { state.move(index, index + 1); true }.takeIf { index in 0 until order.lastIndex },
        ),
    )
}

/**
 * A lazy list keeps its first visible item in place when the items before it change, so dragging that item away, or
 * a new item at the top, would scroll the list. With this, the list stays where it is when its [keys] change order.
 */
@Composable
fun KeepListPlace(state: LazyListState, keys: List<Any>) {
    val last = remember { arrayOfNulls<List<Any>>(1) }
    // Before the list is measured with the new keys.
    SideEffect {
        if (last[0] != null && last[0] != keys) {
            state.requestScrollToItem(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
        last[0] = keys
    }
}

private val DRAGGED_ELEVATION = 8.dp

/**
 * Drags at once, unlike a long press: it's there only for dragging. The positions are in root coordinates, as the
 * handle moves along with the finger.
 */
@Composable
private fun DragHandle(onStart: (y: Float) -> Unit, onDrag: (y: Float) -> Unit, onEnd: () -> Unit) {
    val currentOnStart by rememberUpdatedState(onStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnEnd by rememberUpdatedState(onEnd)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Icon(
        painterResource(R.drawable.ic_drag_indicator),
        // The item has actions for moving it instead.
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .onGloballyPositioned { coordinates[0] = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Not a tap of the item, which does something else.
                    down.consume()
                    val layout = coordinates[0]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                    currentOnStart(layout.localToRoot(down.position).y)
                    try {
                        drag(down.id) { change ->
                            change.consume()
                            if (layout.isAttached) currentOnDrag(layout.localToRoot(change.position).y)
                        }
                    } finally {
                        currentOnEnd()
                    }
                }
            }
            .padding(12.dp),
    )
}
