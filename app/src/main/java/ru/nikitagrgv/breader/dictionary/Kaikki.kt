package ru.nikitagrgv.breader.dictionary

import ru.nikitagrgv.breader.translation.abortOnCancel
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A Wiktionary edition: the language words are explained in. [name] is in English. */
data class Edition(val code: String, val name: String, val url: String)

/**
 * A language of the words in an edition, as kaikki.org lists it.
 * @param name in the edition's language, e.g. "Английский" in the Russian one.
 * @param path the URL-encoded directory of its page.
 */
data class CatalogLanguage(val name: String, val path: String, val senses: Int)

/** Dictionaries extracted from Wiktionary by https://kaikki.org. */
object Kaikki {
    val editions = listOf(
        Edition("en", "English", "https://kaikki.org/dictionary/"),
        edition("ru", "Russian"),
        edition("de", "German"),
        edition("fr", "French"),
        edition("es", "Spanish"),
        edition("it", "Italian"),
        edition("pt", "Portuguese"),
        edition("pl", "Polish"),
        edition("nl", "Dutch"),
        edition("cs", "Czech"),
        edition("el", "Greek"),
        edition("tr", "Turkish"),
        edition("zh", "Chinese"),
        edition("ja", "Japanese"),
        edition("ko", "Korean"),
        edition("th", "Thai"),
        edition("vi", "Vietnamese"),
        edition("id", "Indonesian"),
        edition("ms", "Malay"),
        edition("ku", "Kurdish"),
    )

    suspend fun languages(edition: Edition): List<CatalogLanguage> = parseLanguages(fetchText("${edition.url}index.html"))

    /**
     * The URL of the file with [language]'s words only; null if there's none. Such files are deprecated
     * (https://github.com/tatuylonen/wiktextract/issues/1178), but much smaller than [wholeEditionUrl].
     */
    suspend fun languageFileUrl(edition: Edition, language: CatalogLanguage): String? {
        val page = "${edition.url}${language.path}/"
        val html = try {
            fetchText("${page}index.html")
        } catch (_: FileNotFoundException) {
            return null
        }
        return parseDownloadLink(html)?.let { if (it.startsWith("http")) it else page + it }
    }

    /** All the words of an edition, in all languages. */
    fun wholeEditionUrl(edition: Edition): String = "${edition.url}raw-wiktextract-data.jsonl.gz"

    /** The languages of an edition's index page, in its order: the ones with more senses first. */
    internal fun parseLanguages(html: String): List<CatalogLanguage> =
        LANGUAGE_LINK.findAll(html)
            .map { CatalogLanguage(decodeEntities(it.groupValues[2]).trim(), it.groupValues[1], it.groupValues[3].toInt()) }
            .filter { it.path != ALL_LANGUAGES }
            .distinctBy { it.path }
            .toList()

    internal fun parseDownloadLink(html: String): String? = JSONL_LINK.find(html)?.groupValues?.get(1)

    private fun edition(code: String, name: String) = Edition(code, name, "https://kaikki.org/${code}wiktionary/")

    private fun decodeEntities(text: String): String = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&#x27;", "'").replace("&amp;", "&")

    private suspend fun fetchText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return connection.abortOnCancel {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            when (val code = responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> throw FileNotFoundException(url)
                !in 200..299 -> throw IOException("HTTP $code for $url")
            }
            inputStream.use { it.readBytes().decodeToString() }
        }
    }

    private const val ALL_LANGUAGES = "All%20languages%20combined"
    private const val TIMEOUT_MS = 30_000
    private val LANGUAGE_LINK = Regex("""<a href="([^"/]+)/index\.html">([^<]+?) \((\d+) senses\)</a>""")
    private val JSONL_LINK = Regex("""href="([^"]+\.jsonl)"""")
}
