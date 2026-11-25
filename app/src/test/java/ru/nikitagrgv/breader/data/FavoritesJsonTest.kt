package ru.nikitagrgv.breader.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.nikitagrgv.breader.translation.BookRange
import ru.nikitagrgv.breader.translation.Excerpt
import ru.nikitagrgv.breader.translation.MachineTranslationService
import ru.nikitagrgv.breader.translation.TextPosition
import ru.nikitagrgv.breader.translation.Translation

class FavoritesJsonTest {
    private fun Favorite.roundTrip(): Favorite = JSONObject(toJson().toString()).toFavorite()

    private val place = FavoritePlace(
        bookId = "id-1",
        fingerprint = "abc123",
        bookTitle = "Misery",
        range = BookRange(TextPosition(12, 5), TextPosition(13, 2)),
    )

    private val translations = listOf(
        "model/a" to Translation("умереть", alternatives = listOf("сыграть в ящик"), note = "an idiom"),
        "model/b" to Translation("умереть"),
    )

    @Test
    fun `a favorite is kept with all it has, the context and the place`() {
        val favorite = Favorite(
            key = "kick the bucket",
            word = "kick the bucket",
            added = 1_000,
            meanings = "умереть (идиома)",
            translations = translations,
            machine = MachineText("пнуть ведро", MachineTranslationService.MYMEMORY),
            context = Excerpt("He was about to ", "kick the bucket", ", they said."),
            place = place,
        )
        assertEquals(favorite, favorite.roundTrip())
        assertEquals(setOf(FavoriteKind.DICTIONARY, FavoriteKind.AI, FavoriteKind.MACHINE), favorite.kinds)
    }

    @Test
    fun `a favorite with nothing but the text is kept`() {
        val favorite = Favorite("word", "word", 3_000, place = place.copy(fingerprint = null))
        assertEquals(favorite, favorite.roundTrip())
        assertEquals(emptySet<FavoriteKind>(), favorite.kinds)
        assertEquals("", favorite.brief)
    }

    @Test
    fun `favorites stored before there were translations have their meanings as the brief`() {
        val favorite = JSONObject("""{"key":"push","word":"push","brief":"толкать","added":5}""").toFavorite()
        assertEquals(Favorite("push", "push", 5, meanings = "толкать"), favorite)
        assertEquals(setOf(FavoriteKind.DICTIONARY), favorite.kinds)
    }

    @Test
    fun `a broken place is left out, not the favorite`() {
        val json = """{"key":"a","word":"a","added":1,"place":{"bookId":"x","title":"T","range":[3,4,1,0]}}"""
        val favorite = JSONObject(json).toFavorite()
        assertEquals("a", favorite.word)
        assertNull(favorite.place)
    }

    @Test
    fun `what's added to a favorite joins what it has, and where it was kept from first stays`() {
        val kept = Favorite("run", "run", 1, meanings = "бежать", context = Excerpt("", "run", " away"), place = place)
        val added = Favorite("run", "Run", 2, translations = translations, context = Excerpt("They ", "run", ""), place = null)
        val merged = kept.merge(added)
        assertEquals(setOf(FavoriteKind.DICTIONARY, FavoriteKind.AI), merged.kinds)
        assertEquals("run", merged.word)
        assertEquals(1L, merged.added)
        assertEquals(kept.context, merged.context)
        assertEquals(place, merged.place)
    }

    @Test
    fun `a place is taken from what's added if there was none`() {
        val typed = Favorite("run", "run", 1, meanings = "бежать")
        val merged = typed.merge(Favorite("run", "run", 2, translations = translations, place = place))
        assertEquals(place, merged.place)
    }

    @Test
    fun `the brief has the translation fitting the context first`() {
        val favorite = Favorite("run", "run", 1, meanings = "бежать; управлять", translations = translations)
        assertEquals("умереть · бежать; управлять", favorite.brief)
        assertEquals(setOf(FavoriteKind.AI), favorite.without(FavoriteKind.DICTIONARY).kinds)
    }
}
