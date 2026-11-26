package ru.nikitagrgv.breader.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nikitagrgv.breader.data.DictionarySettings
import ru.nikitagrgv.breader.data.downloadedOrderKey
import ru.nikitagrgv.breader.data.orderKey

class DictionaryOrderTest {
    private val english = info("ru-en")
    private val german = info("ru-de")

    @Test
    fun `by default the downloaded dictionaries come first, then the online ones`() {
        assertEquals(
            listOf("downloaded:ru-en", "downloaded:ru-de", "online:YANDEX", "online:GOOGLE", "online:WIKTIONARY"),
            DictionarySettings().all(listOf(english, german)).map { it.orderKey },
        )
    }

    @Test
    fun `the order of the settings puts online dictionaries before the downloaded ones`() {
        val settings = DictionarySettings(
            order = listOf(OnlineDictionary.GOOGLE.orderKey, downloadedOrderKey("ru-en"), OnlineDictionary.WIKTIONARY.orderKey),
        )
        assertEquals(
            // The ones that the order doesn't have, like a dictionary downloaded after it was set, come after it.
            listOf("online:GOOGLE", "downloaded:ru-en", "online:WIKTIONARY", "downloaded:ru-de", "online:YANDEX"),
            settings.all(listOf(english, german)).map { it.orderKey },
        )
        assertEquals(listOf(OnlineDictionary.GOOGLE, OnlineDictionary.WIKTIONARY), settings.usable)
    }

    @Test
    fun `dictionaries that are turned off aren't in use`() {
        val settings = DictionarySettings(
            online = setOf(OnlineDictionary.YANDEX, OnlineDictionary.WIKTIONARY),
            disabled = setOf("ru-de"),
        )
        // Yandex isn't asked without a key.
        assertEquals(
            listOf("downloaded:ru-en", "online:WIKTIONARY"),
            settings.inUse(listOf(english, german)).map { it.orderKey },
        )
    }

    @Test
    fun `a removed dictionary is forgotten`() {
        val settings = DictionarySettings(disabled = setOf("ru-en", "ru-de"), order = listOf("downloaded:ru-en", "online:GOOGLE"))
            .forget("ru-en")
        assertEquals(setOf("ru-de"), settings.disabled)
        assertEquals(listOf("online:GOOGLE"), settings.order)
    }

    @Test
    fun `the brief has the meanings by part of speech, each meaning once`() {
        val results = listOf(
            DictionaryResult(
                DictionarySource.Downloaded(english),
                listOf(entry("run", "verb", "бежать", "работать"), entry("run", "noun", "бег")),
            ),
            DictionaryResult(DictionarySource.Online(OnlineDictionary.GOOGLE), listOf(entry("run", "глагол", "бежать", "бегать"))),
            // Explanations are left out when there are translations.
            DictionaryResult(DictionarySource.Online(OnlineDictionary.WIKTIONARY), listOf(entry("run", "verb", "to move swiftly"))),
        )
        assertEquals(
            listOf(
                BriefEntry("run", "verb", listOf("бежать", "работать")),
                BriefEntry("run", "noun", listOf("бег")),
                BriefEntry("run", "глагол", listOf("бегать")),
            ),
            results.brief(),
        )
        assertEquals("бежать; работать; бег; бегать", results.meaningsLine())
    }

    private fun info(id: String) = DictionaryInfo(id, id, Edition("ru", "Русский", ""), "en", entries = 1, sizeBytes = 1, created = 0)

    private fun entry(word: String, pos: String, vararg meanings: String) =
        DictionaryEntry(word, pos, forms = emptyList(), senses = meanings.map { DictionarySense(it) })
}
