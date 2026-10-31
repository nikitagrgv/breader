package ru.nikitagrgv.breader.format

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal object TxtParser {
    /** Huge paragraphs are slow to lay out, so they are split at word boundaries. */
    private const val MAX_PARAGRAPH_LENGTH = 4000

    private val CHAPTER = Regex("""(глава|часть|книга|chapter|part|book)\s+(\d+|[ivxlcdm]+)\b.{0,60}""", RegexOption.IGNORE_CASE)

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
     * Plain text books come either with one line per paragraph or hard-wrapped at ~70-80 chars,
     * with paragraphs separated by empty lines or by an indented first line.
     */
    internal fun splitParagraphs(text: String): List<String> {
        val lines = text.lines()
        val lengths = lines.filter { it.isNotBlank() }.map { it.trim().length }
        if (lengths.isEmpty()) return emptyList()

        val median = lengths.sorted()[lengths.size / 2]
        val hardWrapped = median >= 40 && lengths.count { it > 100 } * 20 < lengths.size
        val breakOnIndent = lines.count { it.isNotBlank() && it[0].isWhitespace() } * 10 > lengths.size
        val breakOnBlank = (lines.size - lengths.size) * 10 > lengths.size
        if (!hardWrapped || !(breakOnIndent || breakOnBlank)) {
            return lines.map { it.trim() }.filter { it.isNotEmpty() }
        }

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
            if (breakOnIndent && line[0].isWhitespace()) flush()
            if (paragraph.isNotEmpty()) paragraph.append(' ')
            paragraph.append(trimmed)
        }
        flush()
        return result
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
