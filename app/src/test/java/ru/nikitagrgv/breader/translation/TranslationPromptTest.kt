package ru.nikitagrgv.breader.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPromptTest {
    @Test
    fun `fragment is marked in the excerpt and named in the request`() {
        val prompt = TranslationPrompt.user(Excerpt("He saw the ", "saw", "."), "English", "Russian")
        assertTrue(prompt, "He saw the <mark>saw</mark>." in prompt)
        assertTrue(prompt, prompt.endsWith("Translate the marked fragment \"saw\" from English into Russian."))
    }

    @Test
    fun `source language is left out when it isn't set`() {
        val prompt = TranslationPrompt.user(Excerpt("", "word", ""), " ", "Russian")
        assertTrue(prompt, prompt.endsWith("Translate the marked fragment \"word\" into Russian."))
    }

    @Test
    fun `JSON reply is read`() {
        val reply = """{"translation": "считает нужным", "alternatives": ["сочтёт уместным", "Считает нужным."], "note": "Идиома"}"""
        assertEquals(
            Translation("считает нужным", listOf("сочтёт уместным"), "Идиома"),
            TranslationPrompt.parse(reply),
        )
    }

    @Test
    fun `JSON in a code block with text around is read`() {
        val reply = "Here it is:\n```json\n{\"translation\": \"решит\", \"alternatives\": \"примет решение\", \"note\": null}\n```"
        assertEquals(Translation("решит", listOf("примет решение"), ""), TranslationPrompt.parse(reply))
    }

    @Test
    fun `plain text reply is taken as the translation`() {
        assertEquals(Translation("решит"), TranslationPrompt.parse("  решит\n"))
    }

    @Test
    fun `reply without a translation is rejected`() {
        assertNull(TranslationPrompt.parse(""))
        assertNull(TranslationPrompt.parse("""{"translation": "", "note": "?"}"""))
        assertNull(TranslationPrompt.parse("""{"translation": "broken"""))
    }
}
