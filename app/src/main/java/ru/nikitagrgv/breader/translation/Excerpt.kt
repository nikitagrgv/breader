package ru.nikitagrgv.breader.translation

import ru.nikitagrgv.breader.format.Paragraph

/** A place in the book text: a paragraph and a character offset in it. */
data class TextPosition(val paragraph: Int, val offset: Int)

/** A selected range of the book text; [end] is exclusive. */
class BookSelection(val paragraphs: List<Paragraph>, val start: TextPosition, val end: TextPosition) {
    /** The selected text with up to [contextWords] words before and after it, which may come from other paragraphs. */
    fun excerpt(contextWords: Int): Excerpt {
        val fragment = buildString {
            for (index in start.paragraph..end.paragraph) {
                val text = paragraphs[index].text
                if (index > start.paragraph) append('\n')
                append(
                    text,
                    if (index == start.paragraph) start.offset else 0,
                    if (index == end.paragraph) end.offset else text.length,
                )
            }
        }
        return Excerpt(textBefore(contextWords), fragment, textAfter(contextWords))
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
