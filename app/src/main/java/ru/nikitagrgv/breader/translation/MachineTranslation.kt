package ru.nikitagrgv.breader.translation

import ru.nikitagrgv.breader.web.GoogleTranslate
import ru.nikitagrgv.breader.web.MyMemory
import java.text.BreakIterator

/** Free translation services. Unlike the models, they translate the selection alone, without the text around it. */
enum class MachineTranslationService {
    GOOGLE,
    MYMEMORY;

    /** How much text fits in one request. */
    internal val fits: (String) -> Boolean
        get() = when (this) {
            // The text goes in the URL, which Google cuts off at about 16 KB, and non-Latin letters take 6 bytes there.
            GOOGLE -> { text -> text.length <= 1_800 }
            MYMEMORY -> { text -> text.encodeToByteArray().size <= MyMemory.MAX_BYTES }
        }
}

/** @param detectedLanguage the code of the language it was translated from, if the service detected it. */
data class MachineTranslation(val text: String, val detectedLanguage: String?)

object MachineTranslator {
    /**
     * Translates [text] by [service], in parts if it's too long for one request.
     * @param from the code of the language, null to detect it.
     */
    suspend fun translate(service: MachineTranslationService, text: String, from: String?, to: String): MachineTranslation {
        var detected: String? = null
        val translated = StringBuilder()
        for (chunk in splitForRequests(text, service.fits)) {
            val translation = when (service) {
                MachineTranslationService.GOOGLE -> GoogleTranslate.translate(chunk.text, from, to)
                MachineTranslationService.MYMEMORY -> MyMemory.translate(chunk.text, from, to)
            }
            if (detected == null) detected = translation.detectedLanguage
            translated.append(translation.text).append(chunk.separator)
        }
        return MachineTranslation(translated.toString().trim(), detected)
    }
}

/** A part of a text to translate and what separated it from the next one: a line end, a space or nothing. */
internal data class Chunk(val text: String, val separator: String)

/**
 * Splits [text] into parts that [fits] each, between sentences where possible, then between words. A sentence translated
 * on its own is translated just as well, but a part of one isn't, so the parts are as long as they can be.
 */
internal fun splitForRequests(text: String, fits: (String) -> Boolean): List<Chunk> {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (fits(trimmed)) return listOf(Chunk(trimmed, ""))
    val chunks = ArrayList<Chunk>()
    val current = StringBuilder()
    fun flush() {
        if (current.isEmpty()) return
        val whole = current.toString()
        val body = whole.trimEnd()
        chunks += Chunk(body, if ('\n' in whole.substring(body.length)) "\n" else if (body.length < whole.length) " " else "")
        current.setLength(0)
    }
    for (piece in pieces(trimmed, BreakIterator.getSentenceInstance())) {
        if (fits(current.toString() + piece.trimEnd())) {
            current.append(piece)
            continue
        }
        flush()
        if (fits(piece.trimEnd())) {
            current.append(piece)
            continue
        }
        // A sentence too long by itself goes by words, and a word too long by itself by characters.
        for (word in pieces(piece, BreakIterator.getWordInstance())) {
            if (!fits(current.toString() + word.trimEnd())) flush()
            if (fits(word.trimEnd())) {
                current.append(word)
            } else {
                for (char in word) {
                    if (!fits(current.toString() + char)) flush()
                    current.append(char)
                }
            }
        }
    }
    flush()
    return chunks.filter { it.text.isNotBlank() }
}

/** [text] cut at the boundaries found by [iterator]; the pieces put together are the text. */
private fun pieces(text: String, iterator: BreakIterator): List<String> {
    iterator.setText(text)
    val pieces = ArrayList<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        pieces += text.substring(start, end)
        start = end
        end = iterator.next()
    }
    return pieces
}
