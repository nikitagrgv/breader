package ru.nikitagrgv.breader.format

import java.io.IOException
import java.io.InputStream

enum class BookFormat { TXT, FB2, EPUB }

object BookParser {
    /** Types offered in the file picker. FB2 has no registered type, so providers report it in many ways. */
    val PICKER_MIME_TYPES = arrayOf(
        "text/plain",
        "application/epub+zip",
        "application/x-fictionbook+xml",
        "application/x-fictionbook",
        "application/x-zip-compressed-fb2",
        "text/xml",
        "application/xml",
        "application/zip",
        "application/octet-stream",
    )

    private val EXTENSIONS = listOf(".fb2.zip", ".fb2", ".epub", ".txt")

    fun detectFormat(fileName: String, mimeType: String?): BookFormat? {
        val name = fileName.lowercase()
        return when {
            name.endsWith(".epub") -> BookFormat.EPUB
            name.endsWith(".fb2") || name.endsWith(".fb2.zip") -> BookFormat.FB2
            name.endsWith(".txt") -> BookFormat.TXT
            mimeType == "application/epub+zip" -> BookFormat.EPUB
            mimeType != null && "fictionbook" in mimeType -> BookFormat.FB2
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

    fun readMetadata(format: BookFormat, input: InputStream): BookMetadata = when (format) {
        BookFormat.TXT -> BookMetadata(title = null, author = null)
        BookFormat.FB2 -> Fb2Parser.readMetadata(input)
        BookFormat.EPUB -> EpubParser.readMetadata(input)
    }

    fun parse(format: BookFormat, input: InputStream): BookContent {
        val content = when (format) {
            BookFormat.TXT -> TxtParser.parse(input)
            BookFormat.FB2 -> Fb2Parser.parse(input)
            BookFormat.EPUB -> EpubParser.parse(input)
        }
        if (content.paragraphs.isEmpty()) throw IOException("The book has no readable text")
        return content
    }
}
