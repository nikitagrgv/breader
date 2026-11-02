package ru.nikitagrgv.breader.ui.reader

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.nikitagrgv.breader.data.ReadingPosition
import ru.nikitagrgv.breader.translation.BookRange

/**
 * What was selected in a book and where in it the reader is. It's walked like undo and redo, except that nothing is
 * lost: a new selection goes on top instead of dropping what's ahead, so looking something up in the middle of a walk
 * doesn't cost anything. A fragment selected again moves to the top rather than being there twice.
 *
 * @param entries the oldest first.
 * @param cursor the entry the reader is on; the size of [entries] above the newest one, with nothing selected.
 * @param home where the current walk began, and so where going forward ends.
 * @param onEntry whether the entry at [cursor] is what's selected now. It isn't after clearing the selection in a walk,
 *   which stays where it is: going on from there is more likely than starting over.
 */
data class SelectionHistory(
    val entries: List<BookRange> = emptyList(),
    val cursor: Int = entries.size,
    val home: Int = entries.size,
    val onEntry: Boolean = false,
) {
    val current: BookRange? get() = entries.getOrNull(cursor)

    /** How many steps can be taken each way. */
    val stepsBack: Int get() = cursor
    val stepsForward: Int get() = home - cursor

    val isWalking: Boolean get() = cursor != home

    fun back(): SelectionHistory = moveTo(cursor - 1)

    fun forward(): SelectionHistory = moveTo(cursor + 1)

    /** Right back to where the walk began. */
    fun toHome(): SelectionHistory = moveTo(home)

    private fun moveTo(index: Int) = copy(cursor = index, onEntry = index in entries.indices)

    /**
     * The reader has selected [range]. Changing the current selection, as dragging does on the way to what's wanted,
     * only updates its entry: otherwise, every step of the way would be one to go back through.
     * @param maxEntries how many are kept when one is added, which loses the oldest. A limit that's been lowered takes
     *   effect only then, so that moving it by mistake doesn't lose anything at once.
     */
    fun selected(range: BookRange, maxEntries: Int): SelectionHistory {
        val current = current
        if (range == current) return copy(onEntry = true)
        if (onEntry && current != null && range.touches(current)) {
            return copy(entries = entries.toMutableList().apply { this[cursor] = range })
        }
        val updated = (entries - range + range).takeLast(maxEntries)
        return SelectionHistory(updated, cursor = updated.lastIndex, home = updated.lastIndex, onEntry = true)
    }

    /** Nothing is selected any more. Out of a walk, that puts the reader above the newest entry, on top of the history. */
    fun cleared(): SelectionHistory = if (isWalking) copy(onEntry = false) else SelectionHistory(entries)

    /** Without a place that turned out not to be in the book any more. */
    fun without(range: BookRange): SelectionHistory {
        val index = entries.indexOf(range)
        if (index < 0) return this
        fun shifted(position: Int) = if (index < position) position - 1 else position
        return copy(entries = entries - range, cursor = shifted(cursor), home = shifted(home))
    }
}

/**
 * The history of the open book with what goes along with it. It's kept with the open book rather than with what's on
 * the screen, so a walk goes on after switching tabs or turning the screen.
 * @param onEntriesChange gets the entries whenever they change, for keeping them.
 */
@Stable
class SelectionHistoryState(entries: List<BookRange>, private val onEntriesChange: (List<BookRange>) -> Unit) {
    var history by mutableStateOf(SelectionHistory(entries))
        private set

    /** Where the reader was when the walk began, to be brought back to at its end. */
    internal var origin: ReadingPosition? = null

    /**
     * Whether the list is somewhere only because of a walk. That isn't where the reader has got to in the book, so it
     * isn't kept as the reading position.
     */
    internal var holdsPosition = false

    /** What a walk has selected, which comes back as a change of the selection like any other. */
    internal var selectedByWalk: BookRange? = null

    internal fun update(new: SelectionHistory) {
        val old = history
        history = new
        if (new.entries != old.entries) onEntriesChange(new.entries)
        if (!new.isWalking) {
            origin = null
            holdsPosition = false
        }
    }

    /**
     * @param maxEntries see [SelectionHistory.selected].
     * @return whether it's the reader who has just selected something.
     */
    internal fun onSelectionChange(range: BookRange?, maxEntries: Int): Boolean {
        if (range == null) {
            selectedByWalk = null
            update(history.cleared())
            return false
        }
        if (range == selectedByWalk) return false
        update(history.selected(range, maxEntries))
        return true
    }
}
