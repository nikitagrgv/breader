package ru.nikitagrgv.breader.format

import ru.nikitagrgv.breader.dictionary.withoutStressMarks

/**
 * A word of a book and how many times it's there.
 * @param key what the word is told apart by: its case and stress marks don't matter, so "The" is "the".
 * @param word as the book writes it: in lowercase, unless the book always capitalizes it, like "London" or "I".
 */
class WordCount(val key: String, val word: String, val count: Int)

/** The different words of a book, the most frequent first; the ones as frequent, in the alphabetical order. */
class WordFrequency(val words: List<WordCount>) {
    /** The number of words in the text: each word as many times as it's there. */
    val total: Int = words.sumOf { it.count }

    /** The share (0..1) of the text made up by the words with the [keys]. */
    fun share(keys: Set<String>): Double =
        if (total == 0) 0.0 else words.sumOf { if (it.key in keys) it.count else 0 }.toDouble() / total

    /** The words without the ones with the [hidden] keys. */
    fun without(hidden: Set<String>): WordList {
        val (hiddenWords, shown) = words.partition { it.key in hidden }
        return WordList(shown, total, hiddenWords.sumOf { it.count })
    }

    companion object {
        fun of(paragraphs: List<Paragraph>): WordFrequency {
            val counter = WordCounter()
            for (paragraph in paragraphs) counter.add(paragraph.text)
            return counter.result()
        }
    }
}

/**
 * Some of the words of a [WordFrequency], with how much of the text each one covers together with all the words above
 * it: knowing them, one knows that much of the text. The hidden words count as known, so they're in every word's
 * coverage.
 * @param total the number of words in the text, the hidden ones included.
 * @param hiddenCount how many times the hidden words are in the text.
 */
class WordList(val words: List<WordCount>, val total: Int, val hiddenCount: Int) {
    /** How many words of the text are covered by the hidden ones and the ones up to each of [words]. */
    private val covered = IntArray(words.size).also { covered ->
        var sum = hiddenCount
        for (index in words.indices) {
            sum += words[index].count
            covered[index] = sum
        }
    }

    /** The share (0..1) of the text made up by the hidden words. */
    val hiddenShare: Double get() = fraction(hiddenCount)

    /** The share (0..1) of the text made up by the word at [index]. */
    fun share(index: Int): Double = fraction(words[index].count)

    /** The share (0..1) of the text covered by the hidden words and the ones up to [index]; -1 for the hidden ones alone. */
    fun coverage(index: Int): Double = fraction(if (index < 0) hiddenCount else covered[index])

    private fun fraction(count: Int): Double = if (total == 0) 0.0 else count.toDouble() / total
}

/**
 * Counts the words of a text that comes in parts, like the paragraphs of a book. A word is what [countWords] counts as
 * one, but numbers aren't words here: they're not something to learn.
 */
class WordCounter {
    private class Tally(var word: String) {
        var count = 0
    }

    private val tallies = HashMap<String, Tally>()
    private val token = StringBuilder()
    private var hasLetter = false
    private var hasMarks = false

    /** Adds the words of [text]; a word doesn't go on from one text into the next. */
    fun add(text: CharSequence) {
        for (index in 0 until text.length) {
            val char = text[index]
            when {
                char.isLetterOrDigit() -> {
                    // A letter itself, unlike the other apostrophes; as in the dictionaries, it's the usual one.
                    token.append(if (char == MODIFIER_APOSTROPHE) '\'' else char)
                    if (char.isLetter()) hasLetter = true
                }
                token.isNotEmpty() && (char.category == CharCategory.NON_SPACING_MARK || char.category == CharCategory.COMBINING_SPACING_MARK) -> {
                    token.append(char)
                    hasMarks = true
                }
                token.isNotEmpty() && char in WORD_JOINERS && index + 1 < text.length && text[index + 1].isLetterOrDigit() -> when (char) {
                    SOFT_HYPHEN -> Unit
                    RIGHT_QUOTE -> token.append('\'')
                    HYPHEN -> token.append('-')
                    else -> token.append(char)
                }
                else -> endWord()
            }
        }
        endWord()
    }

    fun result(): WordFrequency = WordFrequency(
        tallies.map { (key, tally) -> WordCount(key, tally.word, tally.count) }
            .sortedWith(compareByDescending<WordCount> { it.count }.thenBy { it.key }),
    )

    private fun endWord() {
        if (token.isEmpty()) return
        if (hasLetter) {
            // A letter with an accent may be written as two characters; composed, it's one, as the stress marks aren't.
            val word = token.toString().let { if (hasMarks) withoutStressMarks(it) else it }
            val key = word.lowercase()
            val tally = tallies.getOrPut(key) { Tally(word) }
            tally.count++
            // Capitalized at the start of a sentence, the word is still written in lowercase elsewhere.
            if (word == key) tally.word = key
        }
        token.setLength(0)
        hasLetter = false
        hasMarks = false
    }

    private companion object {
        const val RIGHT_QUOTE = '’'
        const val HYPHEN = '‐'
        const val MODIFIER_APOSTROPHE = 'ʼ'
    }
}
