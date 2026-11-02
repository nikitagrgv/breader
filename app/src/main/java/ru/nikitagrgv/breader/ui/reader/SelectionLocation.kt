package ru.nikitagrgv.breader.ui.reader

import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import ru.nikitagrgv.breader.format.Paragraph
import ru.nikitagrgv.breader.translation.BookRange
import ru.nikitagrgv.breader.translation.BookSelection
import ru.nikitagrgv.breader.translation.TextPosition

private const val PARAGRAPH_TAG = "paragraph"

/** The selection in the open book, whichever way the book is shown. Reading it is observable. */
internal sealed interface ReaderSelection {
    val hasSelection: Boolean

    /** The selected text; the parts in different paragraphs are on different lines. */
    val text: String

    val paragraphs: List<Paragraph>

    /** Where the selection is in the book; null if there's no selection or it isn't in the book text. */
    fun locate(): BookSelection?

    fun clear()

    /** Gets ready for selecting [range], which is about to be asked for. */
    suspend fun prepare(range: BookRange) {}

    /**
     * Selects [target], which may have to be on the screen for that.
     * @param partly whether to select as much of it as can be, if not all of it can.
     * @return whether all of it is selected.
     */
    fun select(target: BookSelection, partly: Boolean): Boolean
}

/** The selection in text laid out by the reader. */
internal class TextSelection(val state: SelectionState, override val paragraphs: List<Paragraph>) : ReaderSelection {
    override val hasSelection: Boolean get() = state.selectedTexts.any { it.isNotEmpty() }
    override val text: String get() = state.selectedTexts.joinToString("\n") { it.text }

    override fun locate(): BookSelection? = state.locate(paragraphs)

    override fun clear() = state.clear()

    /** Only the paragraphs that are composed can be selected, which is the ones on the screen. */
    override fun select(target: BookSelection, partly: Boolean): Boolean {
        val range = rangeAmongTexts(state.getSelectableTexts(), target.range, partly) ?: return false
        state.select(range)
        return locate()?.range == target.range
    }
}

/**
 * Where [range] of the book is in [texts] taken as one long text, which is how [SelectionState.select] wants it.
 * @param texts the paragraphs at hand, from [SelectionState.getSelectableTexts].
 * @param partly whether it's enough for the start of it to be there, with the rest cut off.
 * @return null if it isn't there.
 */
internal fun rangeAmongTexts(texts: List<AnnotatedString>, range: BookRange, partly: Boolean): TextRange? {
    var start = -1
    var end = -1
    var length = 0
    for (text in texts) {
        val paragraph = text.paragraphIndex()
        if (paragraph == range.start.paragraph) start = length + range.start.offset
        if (paragraph == range.end.paragraph) end = length + range.end.offset
        length += text.length
    }
    if (start < 0 || (end < 0 && !partly)) return null
    return TextRange(start, if (end < 0) length else end)
}

/**
 * Tags the whole text with the paragraph [index]. The tag stays on any selected part of the text,
 * which is how [locateSelection] finds the part's paragraph.
 */
internal fun AnnotatedString.Builder.tagParagraph(index: Int) {
    addStringAnnotation(PARAGRAPH_TAG, index.toString(), 0, length)
}

private fun AnnotatedString.paragraphIndex(): Int? =
    getStringAnnotations(PARAGRAPH_TAG, 0, length).firstOrNull()?.item?.toIntOrNull()

/** Where the selection is in the book; null if there's no selection or it isn't in the book text. */
internal fun SelectionState.locate(paragraphs: List<Paragraph>): BookSelection? =
    locateSelection(selectedTexts, anchorOffsets(), paragraphs)

/**
 * @param parts the selected parts of the paragraphs in order, as [SelectionState.selectedTexts] gives them.
 * @param anchors offsets of the selection's start and end in their paragraphs, if known.
 */
internal fun locateSelection(
    parts: List<AnnotatedString>,
    anchors: Pair<Int, Int>?,
    paragraphs: List<Paragraph>,
): BookSelection? {
    val selected = parts.filter { it.isNotEmpty() }
    val first = selected.firstOrNull() ?: return null
    val last = selected.last()
    val firstIndex = first.paragraphIndex()?.takeIf { it in paragraphs.indices } ?: return null
    val lastIndex = last.paragraphIndex()?.takeIf { it in firstIndex..paragraphs.lastIndex } ?: return null
    val firstText = paragraphs[firstIndex].text

    if (selected.size > 1) {
        // A selection of several paragraphs runs to the end of the first one and from the start of the last one.
        val start = (firstText.length - first.length).coerceAtLeast(0)
        val end = last.length.coerceAtMost(paragraphs[lastIndex].text.length)
        return BookSelection(paragraphs, TextPosition(firstIndex, start), TextPosition(lastIndex, end))
    }

    // The selected text may occur in the paragraph more than once, so the anchors are tried first.
    val text = first.text
    val candidates = buildList {
        if (anchors != null) {
            val (start, end) = anchors
            // Both anchors are in this paragraph, which is the usual case.
            add(minOf(start, end))
            // One anchor is at the edge of a neighboring paragraph, with nothing selected there.
            addAll(listOf(start, end, start - text.length, end - text.length))
        }
        add(firstText.indexOf(text))
    }
    val offset = candidates.firstOrNull { it >= 0 && firstText.startsWith(text, it) } ?: return null
    return BookSelection(paragraphs, TextPosition(firstIndex, offset), TextPosition(firstIndex, offset + text.length))
}

/**
 * Offsets of the selection's start and end anchors in their texts. [SelectionState] doesn't expose them,
 * but its public Saver saves them. The layout of the saved list is an implementation detail though,
 * so the result is only a hint that [locateSelection] verifies.
 */
private fun SelectionState.anchorOffsets(): Pair<Int, Int>? {
    val saved = runCatching { with(SelectionState.Saver) { AnythingSaverScope.save(this@anchorOffsets) } }
        .getOrNull() as? List<*> ?: return null
    val start = saved.getOrNull(1) as? Int ?: return null
    val end = saved.getOrNull(4) as? Int ?: return null
    return start to end
}

private object AnythingSaverScope : SaverScope {
    override fun canBeSaved(value: Any) = true
}
