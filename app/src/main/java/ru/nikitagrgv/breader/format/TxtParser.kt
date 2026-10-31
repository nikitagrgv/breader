package ru.nikitagrgv.breader.format

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object TxtParser {
    /** Huge paragraphs are slow to lay out, so they are split at word boundaries. */
    private const val MAX_PARAGRAPH_LENGTH = 4000

    /** "Chapter 12 ...", "Часть II" or a bare chapter number. */
    private val CHAPTER = Regex("""(глава|часть|книга|chapter|part|book)\s+(\d+|[ivxlcdm]+)\b.{0,60}|\d{1,3}\.?""", RegexOption.IGNORE_CASE)
    private val PAGE_NUMBER = Regex("""page\s+\d+""", RegexOption.IGNORE_CASE)
    private const val SENTENCE_END = ".!?…'\"”’»"
    private const val DIALOGUE_START = "'\"‘“«—–-"

    fun parse(input: InputStream): BookContent {
        val paragraphs = splitParagraphs(decode(input.readBytes()))
            .flatMap(::splitLong)
            .map { Paragraph(it, if (CHAPTER.matches(it)) ParagraphKind.HEADING else ParagraphKind.BODY) }
        return BookContent(BookMetadata(title = null, author = null), paragraphs)
    }

    internal fun decode(bytes: ByteArray): String {
        fun startsWith(vararg bom: Int) = bytes.size >= bom.size && bom.indices.all { bytes[it] == bom[it].toByte() }
        return when {
            startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            } catch (_: CharacterCodingException) {
                // Not UTF-8: most likely a legacy Russian text.
                String(bytes, Charset.forName("windows-1251"))
            }
        }
    }

    /**
     * Plain text books come either with one line per paragraph or hard-wrapped at ~70-80 chars.
     * Hard-wrapped paragraphs are separated by empty lines, by an indented first line, or,
     * in texts converted from PDF, by nothing at all except a shorter last line.
     */
    internal fun splitParagraphs(text: String): List<String> {
        var lines = text.lines().map { it.trimEnd() }
        if (isDoubleSpaced(lines)) lines = collapseDoubleSpacing(lines)
        lines = removePageArtifacts(lines)

        val nonBlank = lines.filter { it.isNotEmpty() }
        if (nonBlank.isEmpty()) return emptyList()
        val lengths = nonBlank.map { it.trim().length }.sorted()
        val hardWrapped = lengths[lengths.size / 2] >= 40 && lengths.count { it > 100 } * 20 < lengths.size
        if (!hardWrapped) return nonBlank.map { it.trim() }

        // An indent shared by all lines means nothing; only a deeper one starts a paragraph.
        val indents = nonBlank.map(::indentOf)
        val baseIndent = indents.groupingBy { it }.eachCount().maxBy { it.value }.key
        val breakOnIndent = indents.count { it > baseIndent } * 10 > nonBlank.size
        val breakOnBlank = (lines.size - nonBlank.size) * 10 > nonBlank.size
        val breakOnShort = !breakOnIndent && !breakOnBlank
        // Lines of a paragraph are filled up to the text width; its last line is usually shorter.
        val shortLine = lengths[lengths.size * 9 / 10] * 4 / 5

        val result = ArrayList<String>()
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotEmpty()) result += paragraph.toString()
            paragraph.setLength(0)
        }
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                flush()
                continue
            }
            if (breakOnIndent && indentOf(line) > baseIndent) flush()
            // A sentence ending at a full-width line, followed by dialogue or a heading like "4".
            if (breakOnShort && paragraph.endsSentence() &&
                (trimmed[0] in DIALOGUE_START || (trimmed.length < shortLine && !trimmed.endsSentence()))
            ) {
                flush()
            }
            if (paragraph.isNotEmpty()) paragraph.append(' ')
            paragraph.append(trimmed)
            if (breakOnShort && trimmed.length < shortLine) flush()
        }
        flush()
        return result
    }

    private fun indentOf(line: String) = line.indexOfFirst { !it.isWhitespace() }

    private fun CharSequence.endsSentence() = isNotEmpty() && last() in SENTENCE_END

    /** Double-spaced text (often converted from PDF) has an empty line after each line, so they mean nothing. */
    private fun isDoubleSpaced(lines: List<String>): Boolean {
        var nonBlank = 0
        var followed = 0
        for (i in lines.indices) {
            if (lines[i].isBlank()) continue
            nonBlank++
            if (i + 1 < lines.size && lines[i + 1].isNotBlank()) followed++
        }
        return nonBlank >= 10 && followed * 10 < nonBlank
    }

    /** Drops single empty lines; longer runs become one paragraph break. */
    private fun collapseDoubleSpacing(lines: List<String>): List<String> {
        val result = ArrayList<String>(lines.size / 2 + 1)
        var blanks = 0
        for (line in lines) {
            if (line.isBlank()) {
                blanks++
                continue
            }
            if (blanks >= 2 && result.isNotEmpty()) result += ""
            blanks = 0
            result += line
        }
        return result
    }

    /** PDF converters leave page numbers and a running title (repeated on most pages) between lines. */
    private fun removePageArtifacts(lines: List<String>): List<String> {
        val pages = lines.count { PAGE_NUMBER.matches(it.trim()) }
        if (pages < 10) return lines
        val counts = lines.groupingBy { it.trim() }.eachCount()
        return lines.filterNot { line ->
            val trimmed = line.trim()
            PAGE_NUMBER.matches(trimmed) ||
                (trimmed.isNotEmpty() && trimmed.length <= 60 && counts.getValue(trimmed) >= pages / 2)
        }
    }

    private fun splitLong(paragraph: String): List<String> {
        if (paragraph.length <= MAX_PARAGRAPH_LENGTH) return listOf(paragraph)
        val parts = ArrayList<String>()
        var start = 0
        while (start < paragraph.length) {
            var end = minOf(start + MAX_PARAGRAPH_LENGTH, paragraph.length)
            if (end < paragraph.length) {
                val space = paragraph.lastIndexOf(' ', end)
                if (space > start) end = space
            }
            paragraph.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(parts::add)
            start = end
        }
        return parts
    }
}
