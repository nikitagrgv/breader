package ru.nikitagrgv.breader.translation

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private val codes = ConcurrentHashMap<String, String>()

/** Stands for "not known" in [codes], which can't have nulls. */
private const val UNKNOWN = ""

/**
 * The ISO 639-1 code of a language named in the settings, e.g. "en" for "English"; null if it isn't known. The models
 * are told the name as is, while web services want a code. The name may be in English, in the device language or in
 * the language itself; a code is taken as it is.
 */
fun languageCode(name: String): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    return codes.getOrPut(trimmed.lowercase()) { findCode(trimmed) ?: UNKNOWN }.ifEmpty { null }
}

/** The English name of the language with [code], as English Wiktionary names it. */
fun languageName(code: String): String = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifEmpty { code }

/** The code of the device language. */
fun deviceLanguageCode(): String = Locale.getDefault().modernLanguage()

private fun findCode(name: String): String? {
    val locales = Locale.getAvailableLocales().filter { it.language.isNotEmpty() }
    val lower = name.lowercase()
    if (lower.length in 2..3 && lower.all { it in 'a'..'z' }) {
        locales.firstOrNull { it.language == lower || it.modernLanguage() == lower }?.let { return it.modernLanguage() }
    }
    val named = locales.firstOrNull { locale ->
        locale.getDisplayLanguage(Locale.ENGLISH).equals(name, ignoreCase = true) ||
            locale.getDisplayLanguage().equals(name, ignoreCase = true) ||
            locale.getDisplayLanguage(locale).equals(name, ignoreCase = true)
    }
    return named?.modernLanguage()
}

/** Java still calls some languages by their old codes, like "iw" for Hebrew, which web services may not know. */
private fun Locale.modernLanguage(): String = toLanguageTag().substringBefore('-')
