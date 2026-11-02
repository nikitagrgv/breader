package ru.nikitagrgv.breader.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParagraphCollectorTest {
    @Test
    fun `whitespace is collapsed and trimmed`() {
        val collector = ParagraphCollector()
        collector.append("\n   Hello,\n\t  ")
        collector.append("  world!  \n")
        collector.endParagraph(ParagraphKind.BODY)
        assertEquals(listOf(Paragraph("Hello, world!")), collector.paragraphs)
    }

    @Test
    fun `non-breaking spaces are kept`() {
        val collector = ParagraphCollector()
        collector.append("a — b")
        collector.endParagraph(ParagraphKind.BODY)
        assertEquals("a — b", collector.paragraphs.single().text)
    }

    @Test
    fun `indentation made of non-breaking spaces is dropped`() {
        val collector = ParagraphCollector()
        collector.append("   Some ")
        collector.startStyle(InlineStyle.ITALIC)
        collector.append("italic")
        collector.endStyle(InlineStyle.ITALIC)
        collector.append(" ")
        collector.endParagraph(ParagraphKind.BODY)
        val paragraph = collector.paragraphs.single()
        assertEquals("Some italic", paragraph.text)
        val span = paragraph.spans.single()
        assertEquals("italic", paragraph.text.substring(span.start, span.end).trim())
    }

    @Test
    fun `inline styles become ranges`() {
        val collector = ParagraphCollector()
        collector.append("Some ")
        collector.startStyle(InlineStyle.ITALIC)
        collector.append("italic")
        collector.endStyle(InlineStyle.ITALIC)
        collector.append(" text")
        collector.endParagraph(ParagraphKind.BODY)
        val paragraph = collector.paragraphs.single()
        assertEquals("Some italic text", paragraph.text)
        val span = paragraph.spans.single()
        assertEquals("italic", paragraph.text.substring(span.start, span.end).trim())
    }

    @Test
    fun `style left open continues into the next paragraph`() {
        val collector = ParagraphCollector()
        collector.startStyle(InlineStyle.BOLD)
        collector.append("one")
        collector.endParagraph(ParagraphKind.BODY)
        collector.append("two")
        collector.endStyle(InlineStyle.BOLD)
        collector.endParagraph(ParagraphKind.BODY)
        assertEquals(
            listOf(StyledRange(0, 3, InlineStyle.BOLD)),
            collector.paragraphs.map { it.spans.single() }.distinct(),
        )
    }

    @Test
    fun `empty blocks and trailing line breaks produce nothing extra`() {
        val collector = ParagraphCollector()
        collector.endParagraph(ParagraphKind.BODY)
        collector.append("   ")
        collector.endParagraph(ParagraphKind.BODY)
        collector.append("line")
        collector.lineBreak()
        collector.endParagraph(ParagraphKind.VERSE)
        assertEquals(listOf(Paragraph("line", ParagraphKind.VERSE)), collector.paragraphs)
        assertTrue(collector.paragraphs.single().spans.isEmpty())
    }

    /** Emulates FB2: paragraphs and <empty-line/> elements, given as nulls. */
    private fun collect(vararg items: String?): List<Paragraph> {
        val collector = ParagraphCollector()
        for (item in items) {
            if (item == null) {
                collector.endParagraph(ParagraphKind.BODY)
                collector.blankLine()
            } else {
                collector.append(item)
                collector.endParagraph(ParagraphKind.BODY)
            }
        }
        return collector.paragraphs
    }

    @Test
    fun `empty lines go before the next paragraph`() {
        assertEquals(
            listOf(Paragraph("One."), Paragraph("Two.", blankLinesBefore = 2)),
            collect("One.", null, null, "Two."),
        )
    }

    @Test
    fun `a number set off by empty lines becomes a heading`() {
        assertEquals(
            listOf(
                Paragraph("End."),
                Paragraph("23", ParagraphKind.HEADING),
                // The heading's own spacing is enough.
                Paragraph("Start."),
            ),
            collect("End.", null, null, null, null, "23", null, null, "Start."),
        )
        assertEquals(ParagraphKind.HEADING, collect("A.", null, "XIV.")[1].kind)
    }

    @Test
    fun `a number without empty lines before it stays text`() {
        assertEquals(listOf(Paragraph("He said:"), Paragraph("23")), collect("He said:", "23"))
        assertEquals(ParagraphKind.BODY, collect("A.", null, "23 years later.")[1].kind)
    }
}
