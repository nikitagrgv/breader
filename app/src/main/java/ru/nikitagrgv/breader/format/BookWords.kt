package ru.nikitagrgv.breader.format

/**
 * The number of words in [text]: runs of letters and digits. An apostrophe or a hyphen between letters doesn't split a
 * word, so "don't" and "well-known" are one word each, while a dash standing between words is none. Neither do stress
 * marks, which some books put in Russian words.
 */
fun countWords(text: CharSequence): Int {
    var count = 0
    var inWord = false
    for (index in 0 until text.length) {
        val char = text[index]
        when {
            char.isLetterOrDigit() -> {
                if (!inWord) count++
                inWord = true
            }
            inWord && (char.category == CharCategory.NON_SPACING_MARK || char.category == CharCategory.COMBINING_SPACING_MARK) -> Unit
            // Only if a letter follows: "dogs' bowls" is two words.
            inWord && char in WORD_JOINERS && index + 1 < text.length && text[index + 1].isLetterOrDigit() -> Unit
            else -> inWord = false
        }
    }
    return count
}

/** Apostrophes, hyphens and the soft hyphen, which some books put inside long words. */
internal const val WORD_JOINERS = "'’‐-­"

internal const val SOFT_HYPHEN = '\u00AD'

/**
 * How the words of a book are spread over its paragraphs, for telling how much of it is read. Unlike paragraphs, which
 * can be a line or a page long, words are read at about the same pace.
 */
class BookWords private constructor(
    /** The number of words before each paragraph, and the total after the last one. */
    private val before: IntArray,
) {
    val total: Int get() = before.last()

    val paragraphs: Int get() = before.size - 1

    /** The words read once [share] (0..1) of the paragraph [index] is. */
    fun at(index: Int, share: Float): Float {
        if (paragraphs == 0) return 0f
        val paragraph = index.coerceIn(0, paragraphs - 1)
        val words = before[paragraph + 1] - before[paragraph]
        return before[paragraph] + words * share.coerceIn(0f, 1f)
    }

    /** The paragraph where [fraction] (0..1) of the words is, with the share of the paragraph read by then. */
    fun find(fraction: Float): Pair<Int, Float> {
        if (paragraphs == 0) return 0 to 0f
        val target = total * fraction.coerceIn(0f, 1f)
        // The last paragraph that starts at or before the target.
        var low = 0
        var high = paragraphs - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (before[middle] <= target) low = middle else high = middle - 1
        }
        val words = before[low + 1] - before[low]
        return low to if (words == 0) 0f else ((target - before[low]) / words).coerceIn(0f, 1f)
    }

    companion object {
        /** @param counts the number of words in each paragraph. */
        fun of(counts: IntArray): BookWords {
            val before = IntArray(counts.size + 1)
            for (index in counts.indices) before[index + 1] = before[index] + counts[index]
            return BookWords(before)
        }

        fun of(paragraphs: List<Paragraph>): BookWords = of(IntArray(paragraphs.size) { countWords(paragraphs[it].text) })
    }
}
