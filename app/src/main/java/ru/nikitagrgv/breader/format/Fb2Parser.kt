package ru.nikitagrgv.breader.format

import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

internal object Fb2Parser {
    fun readMetadata(input: InputStream): BookMetadata =
        Fb2Reader(newXmlParser(unzipIfNeeded(input)), metadataOnly = true).read().metadata

    fun parse(input: InputStream): BookContent =
        Fb2Reader(newXmlParser(unzipIfNeeded(input)), metadataOnly = false).read()

    /** FB2 books are often distributed as `.fb2.zip`. */
    private fun unzipIfNeeded(input: InputStream): InputStream {
        val buffered = BufferedInputStream(input)
        buffered.mark(2)
        val isZip = buffered.read() == 'P'.code && buffered.read() == 'K'.code
        buffered.reset()
        if (!isZip) return buffered

        val zip = ZipInputStream(buffered)
        while (true) {
            val entry = zip.nextEntry ?: throw IOException("No .fb2 file in the archive")
            if (!entry.isDirectory && entry.name.endsWith(".fb2", ignoreCase = true)) return zip
        }
    }
}

private class Fb2Reader(private val parser: XmlPullParser, private val metadataOnly: Boolean) {
    private val path = ArrayList<String>()
    private val out = ParagraphCollector()

    // Metadata from <description><title-info>.
    private val text = StringBuilder()
    private val authors = ArrayList<String>()
    private val authorNames = ArrayList<String>()
    private var nickname: String? = null
    private var title: String? = null

    private var inBody = false

    fun read(): BookContent {
        loop@ while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    val name = parser.localName
                    when {
                        // Base64 images; not supported yet.
                        name == "binary" -> parser.skipElement()
                        name == "body" && metadataOnly -> break@loop
                        else -> onStart(name)
                    }
                }
                XmlPullParser.END_TAG -> onEnd()
                XmlPullParser.TEXT -> if (inBody) out.append(parser.text) else text.append(parser.text)
                XmlPullParser.END_DOCUMENT -> break@loop
            }
        }
        out.endParagraph(ParagraphKind.BODY)
        return BookContent(BookMetadata(title, authors.joinToString(", ").ifEmpty { null }), out.paragraphs)
    }

    private fun onStart(name: String) {
        if (inBody) {
            when (name) {
                in BLOCKS -> out.endParagraph(kindOf(path))
                "strong" -> out.startStyle(InlineStyle.BOLD)
                "emphasis" -> out.startStyle(InlineStyle.ITALIC)
            }
            if (name == "empty-line") out.blankLine()
        } else {
            text.setLength(0)
            if (name == "body") inBody = true
        }
        path += name
    }

    private fun onEnd() {
        val name = path.lastOrNull() ?: return
        if (inBody) {
            when (name) {
                "body" -> {
                    out.endParagraph(kindOf(path))
                    inBody = false
                }
                in BLOCKS -> out.endParagraph(kindOf(path))
                "strong" -> out.endStyle(InlineStyle.BOLD)
                "emphasis" -> out.endStyle(InlineStyle.ITALIC)
            }
        } else if ("title-info" in path) {
            // Only <title-info> describes the book itself; <document-info> is about the FB2 file.
            onTitleInfoEnd(name, text.trim().toString())
        }
        path.removeAt(path.lastIndex)
    }

    private fun onTitleInfoEnd(name: String, value: String) {
        when (name) {
            "book-title" -> if (value.isNotEmpty()) title = value
            "first-name", "middle-name", "last-name" -> if (value.isNotEmpty()) authorNames += value
            "nickname" -> if (value.isNotEmpty()) nickname = value
            "author" -> {
                (authorNames.joinToString(" ").ifEmpty { nickname })?.let(authors::add)
                authorNames.clear()
                nickname = null
            }
        }
    }

    /** The innermost element that defines how the text looks. */
    private fun kindOf(path: List<String>): ParagraphKind {
        for (i in path.indices.reversed()) {
            when (path[i]) {
                "title" -> return ParagraphKind.HEADING
                "subtitle" -> return ParagraphKind.SUBTITLE
                "v", "poem" -> return ParagraphKind.VERSE
                "epigraph", "cite", "text-author" -> return ParagraphKind.QUOTE
                "section", "body" -> return ParagraphKind.BODY
            }
        }
        return ParagraphKind.BODY
    }

    companion object {
        private val BLOCKS = setOf(
            "p", "v", "subtitle", "text-author", "title", "section", "poem", "stanza", "epigraph",
            "cite", "empty-line", "date", "table", "tr", "td", "th",
        )
    }
}
