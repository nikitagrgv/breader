package ru.nikitagrgv.breader.translation

import ru.nikitagrgv.breader.format.Paragraph

/** A place in the book text: a paragraph and a character offset in it. */
data class TextPosition(val paragraph: Int, val offset: Int) : Comparable<TextPosition> {
    override fun compareTo(other: TextPosition): Int = compareValuesBy(this, other, TextPosition::paragraph, TextPosition::offset)
}

/** A range of the book text; [end] is exclusive. */
data class BookRange(val start: TextPosition, val end: TextPosition) {
    /** Whether they overlap or are right next to each other. */
    fun touches(other: BookRange): Boolean = start <= other.end && other.start <= end

    /** This range in the text of a book; null if there's no such place, as when the file has changed since. */
    fun inBook(paragraphs: List<Paragraph>): BookSelection? {
        if (start >= end || start.paragraph !in paragraphs.indices || end.paragraph !in paragraphs.indices) return null
        if (start.offset !in 0..paragraphs[start.paragraph].text.length) return null
        if (end.offset !in 0..paragraphs[end.paragraph].text.length) return null
        return BookSelection(paragraphs, start, end)
    }
}

/** A selected range of the book text; [end] is exclusive. */
class BookSelection(val paragraphs: List<Paragraph>, val start: TextPosition, val end: TextPosition) {
    val range: BookRange get() = BookRange(start, end)

    /** The selected text with up to [contextWords] words before and after it, which may come from other paragraphs. */
    fun excerpt(contextWords: Int): Excerpt {
        val fragment = buildString {
            for (index in start.paragraph..end.paragraph) {
                val (from, to) = partOf(index) ?: continue
                if (index > start.paragraph) append('\n')
                append(paragraphs[index].text, from, to)
            }
        }
        return Excerpt(textBefore(contextWords), fragment, textAfter(contextWords))
    }

    /** The offsets of what's selected in the paragraph [index], the second one exclusive; null if the selection isn't there. */
    fun partOf(index: Int): Pair<Int, Int>? {
        if (index !in start.paragraph..end.paragraph) return null
        return Pair(
            if (index == start.paragraph) start.offset else 0,
            if (index == end.paragraph) end.offset else paragraphs[index].text.length,
        )
    }

    private fun textBefore(words: Int): String {
        if (words <= 0) return ""
        val text = StringBuilder(paragraphs[start.paragraph].text.substring(0, start.offset))
        var count = WORD.findAll(text).count()
        var index = start.paragraph
        while (count < words && index > 0) {
            val paragraph = paragraphs[--index].text
            text.insert(0, '\n').insert(0, paragraph)
            count += WORD.findAll(paragraph).count()
        }
        val matches = WORD.findAll(text).toList()
        return if (matches.size <= words) text.trimStart().toString() else text.substring(matches[matches.size - words].range.first)
    }

    private fun textAfter(words: Int): String {
        if (words <= 0) return ""
        val text = StringBuilder(paragraphs[end.paragraph].text.substring(end.offset))
        var count = WORD.findAll(text).count()
        var index = end.paragraph
        while (count < words && index < paragraphs.lastIndex) {
            val paragraph = paragraphs[++index].text
            text.append('\n').append(paragraph)
            count += WORD.findAll(paragraph).count()
        }
        val matches = WORD.findAll(text).take(words).toList()
        return if (matches.size < words) text.trimEnd().toString() else text.substring(0, matches.last().range.last + 1)
    }

    private companion object {
        val WORD = Regex("\\S+")
    }
}

/** The selected fragment of the book with the text around it. */
data class Excerpt(val before: String, val fragment: String, val after: String)
