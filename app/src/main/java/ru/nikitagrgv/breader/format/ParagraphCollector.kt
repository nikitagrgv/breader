package ru.nikitagrgv.breader.format

/** Accumulates XML text into paragraphs, collapsing whitespace the way HTML does. */
internal class ParagraphCollector {
    val paragraphs = ArrayList<Paragraph>()

    private val text = StringBuilder()
    private val ranges = ArrayList<StyledRange>()
    private val openStyles = ArrayList<OpenStyle>()
    private var pendingSpace = false
    private var pendingBlankLines = 0

    fun append(chars: String) {
        for (c in chars) {
            // Only XML whitespace collapses; non-breaking spaces are kept as they are.
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                pendingSpace = true
            } else {
                if (pendingSpace && text.isNotEmpty() && text.last() != '\n') text.append(' ')
                pendingSpace = false
                text.append(c)
            }
        }
    }

    fun lineBreak() {
        if (text.isNotEmpty() && text.last() != '\n') text.append('\n')
        pendingSpace = false
    }

    /** An empty line between paragraphs; it goes before the next paragraph. */
    fun blankLine() {
        pendingBlankLines++
    }

    fun startStyle(style: InlineStyle) {
        openStyles += OpenStyle(style, text.length)
    }

    fun endStyle(style: InlineStyle) {
        val index = openStyles.indexOfLast { it.style == style }
        if (index < 0) return
        val open = openStyles.removeAt(index)
        addRange(open.style, open.start, text.length)
    }

    fun endParagraph(kind: ParagraphKind) {
        // Books often indent paragraphs with non-breaking spaces and end them with <br/>;
        // both are dropped, since the reader lays out paragraphs itself.
        val start = text.indexOfFirst { !it.isWhitespace() }
        if (start >= 0) {
            val end = text.indexOfLast { !it.isWhitespace() } + 1
            for (open in openStyles) addRange(open.style, open.start, text.length)
            val spans = ranges.mapNotNull {
                val spanStart = maxOf(it.start, start) - start
                val spanEnd = minOf(it.end, end) - start
                if (spanEnd > spanStart) it.copy(start = spanStart, end = spanEnd) else null
            }
            val paragraphText = text.substring(start, end)
            // Some books have no chapter titles, only a number set off by empty lines.
            val isChapterNumber = kind == ParagraphKind.BODY && pendingBlankLines > 0 && CHAPTER_NUMBER.matches(paragraphText)
            // A heading is set off by its own spacing.
            val afterHeading = paragraphs.lastOrNull()?.kind == ParagraphKind.HEADING
            paragraphs += Paragraph(
                text = paragraphText,
                kind = if (isChapterNumber) ParagraphKind.HEADING else kind,
                spans = spans,
                blankLinesBefore = if (isChapterNumber || afterHeading) 0 else pendingBlankLines,
            )
            pendingBlankLines = 0
        }
        // Styles that are still open continue into the next paragraph.
        for (i in openStyles.indices) openStyles[i] = openStyles[i].copy(start = 0)
        text.setLength(0)
        ranges.clear()
        pendingSpace = false
    }

    private fun addRange(style: InlineStyle, start: Int, end: Int) {
        if (end > start) ranges += StyledRange(start, end, style)
    }

    private data class OpenStyle(val style: InlineStyle, val start: Int)

    private companion object {
        /** "23", "23." or a Roman numeral like "XIV". */
        val CHAPTER_NUMBER = Regex("[0-9]{1,4}[.]?|[IVXLCDM]{1,8}[.]?")
    }
}
