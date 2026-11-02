package ru.nikitagrgv.breader.format

import android.os.ParcelFileDescriptor
import java.io.IOException
import java.io.InputStream

enum class BookFormat { TXT, FB2, EPUB, PDF }

/** A book's file, to be opened the way its format needs. Whoever opens it closes it. */
interface BookSource {
    fun openStream(): InputStream

    fun openDescriptor(): ParcelFileDescriptor
}

object BookParser {
    /** Types offered in the file picker. FB2 has no registered type, so providers report it in many ways. */
    val PICKER_MIME_TYPES = arrayOf(
        "text/plain",
        "application/pdf",
        "application/epub+zip",
        "application/x-fictionbook+xml",
        "application/x-fictionbook",
        "application/x-zip-compressed-fb2",
        "text/xml",
        "application/xml",
        "application/zip",
        "application/octet-stream",
    )

    private val EXTENSIONS = listOf(".fb2.zip", ".fb2", ".epub", ".pdf", ".txt")

    fun detectFormat(fileName: String, mimeType: String?): BookFormat? {
        val name = fileName.lowercase()
        return when {
            name.endsWith(".epub") -> BookFormat.EPUB
            name.endsWith(".fb2") || name.endsWith(".fb2.zip") -> BookFormat.FB2
            name.endsWith(".pdf") -> BookFormat.PDF
            name.endsWith(".txt") -> BookFormat.TXT
            mimeType == "application/epub+zip" -> BookFormat.EPUB
            mimeType != null && "fictionbook" in mimeType -> BookFormat.FB2
            mimeType == "application/pdf" -> BookFormat.PDF
            mimeType == "text/plain" -> BookFormat.TXT
            else -> null
        }
    }

    fun titleFromFileName(fileName: String): String {
        val lower = fileName.lowercase()
        val extension = EXTENSIONS.firstOrNull { lower.endsWith(it) }
        val title = if (extension != null) fileName.dropLast(extension.length) else fileName.substringBeforeLast('.')
        return title.ifBlank { fileName }
    }

    fun readMetadata(format: BookFormat, source: BookSource): BookMetadata = when (format) {
        BookFormat.TXT -> source.openStream().use { BookMetadata(title = null, author = null) }
        BookFormat.FB2 -> source.openStream().use(Fb2Parser::readMetadata)
        BookFormat.EPUB -> source.openStream().use(EpubParser::readMetadata)
        BookFormat.PDF -> PdfBook.open(source.openDescriptor()).use { it.metadata }
    }

    fun parse(format: BookFormat, source: BookSource): BookContent {
        val content = when (format) {
            BookFormat.TXT -> source.openStream().use(TxtParser::parse)
            BookFormat.FB2 -> source.openStream().use(Fb2Parser::parse)
            BookFormat.EPUB -> source.openStream().use(EpubParser::parse)
            BookFormat.PDF -> PdfBook.open(source.openDescriptor())
        }
        if (content.paragraphs.isEmpty()) throw IOException("The book has no readable text")
        return content
    }
}
