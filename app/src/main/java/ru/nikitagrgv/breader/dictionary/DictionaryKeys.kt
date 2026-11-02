package ru.nikitagrgv.breader.dictionary

import java.text.Normalizer

/**
 * What words are compared by: case, the kind of apostrophe, spacing and stress marks don't matter.
 * Dictionaries mark the stress in some languages (Russian "соба́ка"), which books don't do.
 */
fun dictionaryKey(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
        .replace(STRESS_MARKS, "")
        .lowercase()
        .replace(APOSTROPHES, "'")
        .replace(WHITESPACE, " ")

/**
 * Keys to look up the selected [text] by, in groups from the best: a group is only tried when nothing
 * is found by the ones before it. The first group is the text itself; the second one is the base forms
 * guessed by the spelling rules of the dictionary's [languageCode], for languages whose rules are known.
 * Dictionaries list the inflected forms of their words, but not always all of them.
 */
fun lookupKeys(text: String, languageCode: String): List<List<String>> {
    // Quotes and punctuation around a word are often selected along with it.
    val bare = dictionaryKey(text.trim { !it.isLetterOrDigit() })
    if (bare.isEmpty()) return emptyList()
    val exact = linkedSetOf(dictionaryKey(text), bare)
    val english = languageCode == "en"
    if (english) {
        // "John's", "dogs'".
        bare.removeSuffix("'s").removeSuffix("'").takeIf { it.isNotEmpty() }?.let(exact::add)
    }
    val groups = mutableListOf(exact.toList())
    if (english && ' ' !in bare) {
        // Of the word without the possessive: "dogs" of "dogs'".
        val base = englishBaseForms(exact.last()).filter { it !in exact }
        if (base.isNotEmpty()) groups += base
    }
    return groups
}

/** Possible base forms of an inflected English [word], e.g. "run" for "running". Most are wrong, which is fine for lookups. */
internal fun englishBaseForms(word: String): List<String> {
    val forms = LinkedHashSet<String>()
    for ((ending, replacements) in ENGLISH_ENDINGS) {
        if (!word.endsWith(ending)) continue
        val stem = word.dropLast(ending.length)
        if (stem.isEmpty()) continue
        for (replacement in replacements) {
            if (stem.length + replacement.length < 2) continue
            forms += stem + replacement
            // "running", "stopped", "bigger": the last consonant is doubled before the ending.
            if (replacement.isEmpty() && stem.length >= 3 && stem.last() == stem[stem.lastIndex - 1] && stem.last() !in VOWELS) {
                forms += stem.dropLast(1)
            }
        }
    }
    forms -= word
    return forms.toList()
}

/** The more specific endings first: their guesses are more likely right. */
private val ENGLISH_ENDINGS = listOf(
    "ies" to listOf("y", "ie"), // cities, ties
    "ied" to listOf("y", "ie"), // carried, tied
    "ying" to listOf("ie"), // lying
    "ves" to listOf("f", "fe"), // wolves, knives
    "iest" to listOf("y"), // happiest
    "ier" to listOf("y"), // happier
    "ily" to listOf("y"), // happily
    "es" to listOf("", "e"), // boxes, horses
    "s" to listOf(""),
    "ed" to listOf("", "e"), // walked, moved
    "ing" to listOf("", "e"), // going, making
    "est" to listOf("", "e"), // fastest, latest
    "er" to listOf("", "e"), // faster, later
    "ly" to listOf(""), // quickly
)

private const val VOWELS = "aeiouy"
private val STRESS_MARKS = Regex("[\\u0300-\\u036F]")
private val APOSTROPHES = Regex("[’‘ʼ`´]")
private val WHITESPACE = Regex("\\s+")
