package ru.nikitagrgv.breader.format

/** Accumulates XML text into paragraphs, collapsing whitespace the way HTML does. */
internal class ParagraphCollector {
    val paragraphs = ArrayList<Paragraph>()

    private val text = StringBuilder()
    private val ranges = ArrayList<StyledRange>()
    private val openStyles = ArrayList<OpenStyle>()
    private var pendingSpace = false

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
        // A trailing <br/> shouldn't produce an empty line.
        while (text.isNotEmpty() && text.last() == '\n') text.setLength(text.length - 1)
        if (text.isNotEmpty()) {
            val end = text.length
            for (open in openStyles) addRange(open.style, open.start, end)
            val spans = ranges.mapNotNull { if (it.start >= end) null else it.copy(end = minOf(it.end, end)) }
            paragraphs += Paragraph(text.toString(), kind, spans)
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
}
