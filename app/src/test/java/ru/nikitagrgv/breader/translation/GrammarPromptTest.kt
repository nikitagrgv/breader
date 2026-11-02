package ru.nikitagrgv.breader.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarPromptTest {
    @Test
    fun `fragment is marked in the excerpt`() {
        val prompt = GrammarPrompt.user(Excerpt("the breath ", "she had forced", " into him"), "", "Russian")
        assertTrue(prompt, "the breath <mark>she had forced</mark> into him" in prompt)
        assertTrue(prompt, prompt.endsWith("Explain the grammar of the marked fragment \"she had forced\" in Russian."))
    }

    @Test
    fun `JSON reply is read`() {
        // Shortened from a real reply.
        val reply = """
            {
              "translation": "Если бы ты промолчал, я бы отправил его.",
              "points": [
                {"words": "If you had kept your mouth shut", "rule": "Третий тип условных предложений (Third Conditional)", "explanation": "Нереальное условие в прошлом."},
                {"words": "would have sent", "rule": "would have + past participle", "explanation": ""}
              ]
            }
        """.trimIndent()
        assertEquals(
            GrammarExplanation(
                translation = "Если бы ты промолчал, я бы отправил его.",
                points = listOf(
                    GrammarPoint("If you had kept your mouth shut", "Третий тип условных предложений (Third Conditional)", "Нереальное условие в прошлом."),
                    GrammarPoint("would have sent", "would have + past participle", ""),
                ),
            ),
            GrammarPrompt.parse(reply),
        )
    }

    @Test
    fun `renamed fields are accepted`() {
        val reply = """{"points": [{"title": "Past Perfect (had forced)", "explanation": "Раньше другого действия."}, {"construction": "Phrasal verb", "use": "go off"}]}"""
        assertEquals(
            listOf(GrammarPoint("", "Past Perfect (had forced)", "Раньше другого действия."), GrammarPoint("", "Phrasal verb", "go off")),
            GrammarPrompt.parse(reply)?.points,
        )
    }

    @Test
    fun `reply in another format is kept as text`() {
        val reply = "**Past Perfect** — had + V3."
        assertEquals(GrammarExplanation(text = reply), GrammarPrompt.parse(reply))
        assertEquals(GrammarExplanation(text = """{"answer": "x"}"""), GrammarPrompt.parse("""{"answer": "x"}"""))
        assertNull(GrammarPrompt.parse("  "))
    }
}
