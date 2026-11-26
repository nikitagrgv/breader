package ru.nikitagrgv.breader.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookParserTest {
    @Test
    fun `pdf is detected by the extension and by the type`() {
        assertEquals(BookFormat.PDF, BookParser.detectFormat("Book.PDF", mimeType = null))
        assertEquals(BookFormat.PDF, BookParser.detectFormat("book", "application/pdf"))
        assertNull(BookParser.detectFormat("book", "application/octet-stream"))
    }

    @Test
    fun `pdf extension is left out of the title`() {
        assertEquals("My Book", BookParser.titleFromFileName("My Book.pdf"))
    }

    @Test
    fun `real pdf titles are kept`() {
        assertEquals("War and Peace", PdfBook.cleanTitle("  War and Peace "))
    }

    @Test
    fun `leftovers from making the pdf aren't taken for a title`() {
        assertNull(PdfBook.cleanTitle(null))
        assertNull(PdfBook.cleanTitle("   "))
        assertNull(PdfBook.cleanTitle("report_final.docx"))
        assertNull(PdfBook.cleanTitle("scan0001.PDF"))
        assertNull(PdfBook.cleanTitle("Untitled"))
        assertNull(PdfBook.cleanTitle("Untitled-3"))
        assertNull(PdfBook.cleanTitle("Microsoft Word - notes"))
    }
}
