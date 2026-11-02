package ru.nikitagrgv.breader.format

import java.text.BreakIterator

/** A rectangle on a PDF page, in points from the top left corner of the page as it's shown. */
data class PdfRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The text of a PDF page and where its characters are.
 *
 * PDFs only know lines, so [text] is made to read as running text: lines are joined, words hyphenated at the end of
 * a line are put together and ligatures are spelled out. That's what translators and dictionaries need, and all the
 * offsets here refer to it.
 */
class PdfPageText private constructor(
    val text: String,
    /** The left, top, right and bottom of each character of [text]; all zeros for the ones that aren't drawn. */
    private val boxes: FloatArray,
) {
    /**
     * The character at a point of the page, or the closest one within [maxDistance] points; -1 if there's none.
     *
     * Going off a line vertically counts for much more than going along it. A point right of a short line stays on
     * the line rather than falling to the line below; still, it isn't enough to step between lines to get to the
     * other column.
     */
    fun charAt(x: Float, y: Float, maxDistance: Float = Float.MAX_VALUE): Int {
        var best = -1
        var bestScore = Float.MAX_VALUE
        for (char in text.indices) {
            val box = char * BOX_SIZE
            val left = boxes[box]
            val top = boxes[box + 1]
            val right = boxes[box + 2]
            val bottom = boxes[box + 3]
            if (right <= left || bottom <= top) continue
            val dx = maxOf(left - x, x - right, 0f)
            val dy = maxOf(top - y, y - bottom, 0f)
            if (dx * dx + dy * dy > maxDistance * maxDistance) continue
            val weightedDy = dy * VERTICAL_WEIGHT
            val score = dx * dx + weightedDy * weightedDy
            if (score < bestScore) {
                best = char
                bestScore = score
            }
        }
        return best
    }

    /** The offset between characters closest to a point of the page; -1 if the page has no characters to go by. */
    fun caretAt(x: Float, y: Float): Int {
        val char = charAt(x, y)
        if (char < 0) return -1
        val box = char * BOX_SIZE
        val pastMiddle = x > (boxes[box] + boxes[box + 2]) / 2
        return if (pastMiddle != text[char].isRightToLeft()) char + 1 else char
    }

    /** The start and the end (exclusive) of the word with the character at [offset], or of just the character if it's not in a word. */
    fun wordAt(offset: Int): Pair<Int, Int> {
        if (!text[offset].isLetterOrDigit()) return offset to offset + 1
        val words = BreakIterator.getWordInstance()
        words.setText(text)
        val end = words.following(offset).takeIf { it != BreakIterator.DONE } ?: text.length
        val start = words.preceding(end).takeIf { it != BreakIterator.DONE } ?: 0
        return start to end
    }

    /** The rectangles covering `text[start, end)`, one per line. */
    fun rects(start: Int, end: Int): List<PdfRect> {
        val rects = ArrayList<PdfRect>()
        var current: PdfRect? = null
        for (char in start until end.coerceAtMost(text.length)) {
            val offset = char * BOX_SIZE
            val box = PdfRect(boxes[offset], boxes[offset + 1], boxes[offset + 2], boxes[offset + 3])
            if (box.right <= box.left || box.bottom <= box.top) continue
            val line = current
            current = if (line != null && line.isSameLine(box)) {
                PdfRect(minOf(line.left, box.left), minOf(line.top, box.top), maxOf(line.right, box.right), maxOf(line.bottom, box.bottom))
            } else {
                if (line != null) rects += line
                box
            }
        }
        current?.let { rects += it }
        return rects
    }

    private fun PdfRect.isSameLine(other: PdfRect): Boolean {
        val middle = (other.top + other.bottom) / 2
        val otherHolds = (top + bottom) / 2 in other.top..other.bottom
        return middle in top..bottom || otherHolds
    }

    private fun Char.isRightToLeft(): Boolean {
        val direction = Character.getDirectionality(this)
        return direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }

    companion object {
        private const val BOX_SIZE = 4
        private const val VERTICAL_WEIGHT = 25f

        /** Hyphens marking the end of a line, as opposed to the ones that are in the word anyway. */
        private const val LINE_END_HYPHENS = "­\u0002￾"
        private const val HYPHENS = "-‐"
        private val LIGATURES = mapOf(
            'ﬀ' to "ff", 'ﬁ' to "fi", 'ﬂ' to "fl", 'ﬃ' to "ffi", 'ﬄ' to "ffl", 'ﬅ' to "st", 'ﬆ' to "st",
        )

        /**
         * @param rawText the characters of the page as PDFium lists them, with line ends and all.
         * @param rawBoxes the left, top, right and bottom of each of them.
         */
        fun of(rawText: CharSequence, rawBoxes: FloatArray): PdfPageText {
            val text = StringBuilder(rawText.length)
            var boxes = FloatArray(rawText.length * BOX_SIZE)

            fun append(char: Char, rawIndex: Int?) {
                val box = text.length * BOX_SIZE
                if (box + BOX_SIZE > boxes.size) boxes = boxes.copyOf(boxes.size * 2 + BOX_SIZE)
                if (rawIndex != null) rawBoxes.copyInto(boxes, box, rawIndex * BOX_SIZE, (rawIndex + 1) * BOX_SIZE)
                text.append(char)
            }

            var space = false
            var lineBreak = false
            var lineEndHyphen = false
            for (index in rawText.indices) {
                val char = rawText[index]
                when {
                    char == '\r' || char == '\n' -> {
                        space = true
                        lineBreak = true
                    }
                    char.isWhitespace() -> space = true
                    char in LINE_END_HYPHENS -> lineEndHyphen = true
                    char.isISOControl() || char.category == CharCategory.FORMAT || char == '￿' -> Unit
                    else -> {
                        val splitWord = lineBreak && char.isLowerCase() && text.endsWithHyphenatedWord()
                        if (splitWord) text.setLength(text.length - 1)
                        if (space && text.isNotEmpty() && !splitWord && !(lineBreak && lineEndHyphen)) append(' ', null)
                        val ligature = LIGATURES[char]
                        if (ligature != null) ligature.forEach { append(it, index) } else append(char, index)
                        space = false
                        lineBreak = false
                        lineEndHyphen = false
                    }
                }
            }
            return PdfPageText(text.toString(), boxes.copyOf(text.length * BOX_SIZE))
        }

        private fun CharSequence.endsWithHyphenatedWord(): Boolean =
            length >= 2 && last() in HYPHENS && this[length - 2].isLetter()
    }
}
