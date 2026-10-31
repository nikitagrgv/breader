package ru.nikitagrgv.breader.format

import android.net.Uri
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

internal object EpubParser {
    private const val CONTAINER = "META-INF/container.xml"

    private val MEDIA_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "css", "js",
        "ttf", "otf", "woff", "woff2", "mp3", "mp4", "m4a", "ogg", "wav",
    )
    private val BLOCKS = setOf(
        "body", "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "ul", "ol", "dl", "dt", "dd",
        "blockquote", "pre", "section", "article", "aside", "header", "footer", "nav", "main",
        "figure", "figcaption", "table", "caption", "tr", "td", "th", "hr", "address", "center",
    )
    private val BOLD = setOf("b", "strong")
    private val ITALIC = setOf("i", "em", "cite", "dfn", "var")
    private val SKIPPED = setOf("head", "script", "style")

    fun readMetadata(input: InputStream): BookMetadata {
        val files = readZip(input) { it == CONTAINER || it.endsWith(".opf", ignoreCase = true) }
        return readPackage(files).metadata
    }

    fun parse(input: InputStream): BookContent {
        val files = readZip(input) { it.substringAfterLast('.').lowercase() !in MEDIA_EXTENSIONS }
        val pkg = readPackage(files)
        val out = ParagraphCollector()
        for (path in pkg.spine) {
            val bytes = files.lookup(path) ?: continue
            try {
                readXhtml(newXmlParser(bytes.inputStream()), out)
            } catch (_: XmlPullParserException) {
                // Keep what was read: one broken chapter shouldn't make the whole book unreadable.
            }
            out.endParagraph(ParagraphKind.BODY)
        }
        return BookContent(pkg.metadata, out.paragraphs)
    }

    private class EpubPackage(val metadata: BookMetadata, val spine: List<String>)

    private fun readZip(input: InputStream, accept: (String) -> Boolean): Map<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && accept(entry.name)) files[entry.name] = zip.readBytes()
            }
        }
        return files
    }

    private fun Map<String, ByteArray>.lookup(path: String): ByteArray? =
        get(path) ?: entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value

    private fun readPackage(files: Map<String, ByteArray>): EpubPackage {
        val container = files[CONTAINER] ?: throw IOException("Not an EPUB: $CONTAINER is missing")
        val opfPath = findRootFile(container) ?: throw IOException("Not an EPUB: no package file")
        val opf = files.lookup(opfPath) ?: throw IOException("Not an EPUB: $opfPath is missing")

        val parser = newXmlParser(opf.inputStream())
        val manifest = HashMap<String, String>()
        val spineIds = ArrayList<String>()
        val authors = ArrayList<String>()
        val text = StringBuilder()
        var title: String? = null
        var isAuthor = false
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    text.setLength(0)
                    when (parser.localName) {
                        "item" -> {
                            val id = parser.attr("id")
                            val href = parser.attr("href")
                            if (id != null && href != null) manifest[id] = href
                        }
                        "itemref" -> parser.attr("idref")?.let(spineIds::add)
                        "creator" -> {
                            // Skip illustrators, translators, etc.
                            val role = parser.attr("opf:role") ?: parser.attr("role")
                            isAuthor = role == null || role == "aut"
                        }
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val value = text.trim().toString()
                    when (parser.localName) {
                        "title" -> if (title == null && value.isNotEmpty()) title = value
                        "creator" -> if (isAuthor && value.isNotEmpty()) authors += value
                    }
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }

        val baseDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val spine = spineIds.mapNotNull { manifest[it] }.map { resolvePath(baseDir, it) }
        return EpubPackage(BookMetadata(title, authors.joinToString(", ").ifEmpty { null }), spine)
    }

    private fun findRootFile(container: ByteArray): String? {
        val parser = newXmlParser(container.inputStream())
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> if (parser.localName == "rootfile") return parser.attr("full-path")
                XmlPullParser.END_DOCUMENT -> return null
            }
        }
    }

    /** Resolves a manifest href against the package directory, e.g. `OEBPS` + `../Text/ch1.xhtml`. */
    private fun resolvePath(baseDir: String, href: String): String {
        val segments = ArrayList<String>()
        for (segment in "$baseDir/${Uri.decode(href.substringBefore('#'))}".split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull()
                else -> segments += segment
            }
        }
        return segments.joinToString("/")
    }

    private fun readXhtml(parser: XmlPullParser, out: ParagraphCollector) {
        val path = ArrayList<String>()
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    val name = parser.localName.lowercase()
                    when (name) {
                        in SKIPPED -> {
                            parser.skipElement()
                            continue
                        }
                        "br" -> out.lineBreak()
                        in BLOCKS -> out.endParagraph(kindOf(path))
                        in BOLD -> out.startStyle(InlineStyle.BOLD)
                        in ITALIC -> out.startStyle(InlineStyle.ITALIC)
                    }
                    path += name
                }
                XmlPullParser.END_TAG -> {
                    val name = path.lastOrNull() ?: continue
                    when (name) {
                        in BLOCKS -> out.endParagraph(kindOf(path))
                        in BOLD -> out.endStyle(InlineStyle.BOLD)
                        in ITALIC -> out.endStyle(InlineStyle.ITALIC)
                    }
                    path.removeAt(path.lastIndex)
                }
                XmlPullParser.TEXT -> out.append(parser.text)
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun kindOf(path: List<String>): ParagraphKind {
        for (i in path.indices.reversed()) {
            when (path[i]) {
                "h1", "h2", "h3" -> return ParagraphKind.HEADING
                "h4", "h5", "h6" -> return ParagraphKind.SUBTITLE
                "blockquote" -> return ParagraphKind.QUOTE
                "body" -> return ParagraphKind.BODY
            }
        }
        return ParagraphKind.BODY
    }
}
