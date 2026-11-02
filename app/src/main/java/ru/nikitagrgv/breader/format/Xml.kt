package ru.nikitagrgv.breader.format

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

private const val FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"

internal fun newXmlParser(input: InputStream): XmlPullParser =
    Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        // Enabled by Xml.newPullParser(); must be off to define the entities below. DOCTYPEs are still skipped.
        setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        // Real-world books aren't always well-formed XML.
        runCatching { setFeature(FEATURE_RELAXED, true) }
        // The encoding is taken from the BOM or the XML declaration.
        setInput(input, null)
        // XHTML files often use HTML entities without declaring them.
        for ((name, value) in HTML_ENTITIES) defineEntityReplacementText(name, value)
    }

/** Element name without the namespace prefix. */
internal val XmlPullParser.localName: String
    get() = name.substringAfter(':')

internal fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

/** Skips the current element with all its children; the parser must be at its START_TAG. */
internal fun XmlPullParser.skipElement() {
    var depth = 1
    while (depth > 0) {
        when (next()) {
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> depth--
            XmlPullParser.END_DOCUMENT -> return
        }
    }
}

private val HTML_ENTITIES = mapOf(
    "nbsp" to " ", "shy" to "­", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
    "zwnj" to "‌", "zwj" to "‍", "lrm" to "‎", "rlm" to "‏",
    "ndash" to "–", "mdash" to "—", "minus" to "−", "hellip" to "…", "bull" to "•", "middot" to "·",
    "laquo" to "«", "raquo" to "»", "lsaquo" to "‹", "rsaquo" to "›",
    "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚", "ldquo" to "“", "rdquo" to "”", "bdquo" to "„",
    "prime" to "′", "Prime" to "″", "dagger" to "†", "Dagger" to "‡", "sect" to "§", "para" to "¶",
    "copy" to "©", "reg" to "®", "trade" to "™", "deg" to "°", "plusmn" to "±", "times" to "×",
    "divide" to "÷", "permil" to "‰", "frac12" to "½", "frac14" to "¼", "frac34" to "¾",
    "euro" to "€", "pound" to "£", "cent" to "¢", "yen" to "¥", "iexcl" to "¡", "iquest" to "¿",
    "larr" to "←", "rarr" to "→",
)
