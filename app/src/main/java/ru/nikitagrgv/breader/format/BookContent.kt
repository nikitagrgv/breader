package ru.nikitagrgv.breader.format

data class BookMetadata(val title: String?, val author: String?)

/** Parsed text of a book. Not a data class on purpose: comparing thousands of paragraphs is pointless. */
open class BookContent(
    val metadata: BookMetadata,
    val paragraphs: List<Paragraph>,
)

data class Paragraph(
    val text: String,
    val kind: ParagraphKind = ParagraphKind.BODY,
    val spans: List<StyledRange> = emptyList(),
    /** Empty lines the book puts before the paragraph, e.g. to set off a chapter. */
    val blankLinesBefore: Int = 0,
)

enum class ParagraphKind { BODY, HEADING, SUBTITLE, VERSE, QUOTE }

enum class InlineStyle { BOLD, ITALIC }

/** [style] applied to `text[start, end)` of a paragraph. */
data class StyledRange(val start: Int, val end: Int, val style: InlineStyle)
