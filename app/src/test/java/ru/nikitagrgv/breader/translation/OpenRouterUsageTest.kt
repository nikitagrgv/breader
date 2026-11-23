package ru.nikitagrgv.breader.translation

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** On the replies OpenRouter actually gave, trimmed. */
class OpenRouterUsageTest {
    @Test
    fun `chat reply`() {
        val reply = JSONObject(
            """
            {"id":"gen-1795456698-zz18FGtHO8CyjBygEMyD","object":"chat.completion","created":1795456698,
            "model":"mistralai/mistral-nemo","provider":"DeepInfra",
            "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"Hi"}}],
            "usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12,"cost":3.5e-7,"is_byok":false}}
            """,
        )
        assertEquals(
            RequestUsage(
                time = 5,
                kind = RequestKind.GRAMMAR,
                fragment = "fragment",
                model = "mistralai/mistral-nemo",
                promptTokens = 10,
                completionTokens = 2,
                cost = 3.5e-7,
            ),
            reply.toUsage(5, RequestKind.GRAMMAR, "fragment", askedModel = "openrouter/auto"),
        )
    }

    @Test
    fun `chat reply without usage`() {
        val usage = JSONObject("""{"choices":[]}""").toUsage(5, RequestKind.TRANSLATION, "fragment", askedModel = "a/b")
        assertEquals("a/b", usage.model)
        assertEquals(0, usage.promptTokens)
        assertNull(usage.cost)
    }

    @Test
    fun `key with a limit`() {
        val data = JSONObject(
            """
            {"label":"sk-or-v1-b1a...d42","is_management_key":false,"limit":1,"limit_reset":null,"limit_remaining":0.75,
            "usage":0.25,"usage_daily":0.0000035,"usage_weekly":0.1,"usage_monthly":0.2,"is_free_tier":false,
            "expires_at":"2026-11-23T18:56:00.001Z","free_model_daily_requests":{"used":18,"limit":1000,"remaining":982},
            "rate_limit":{"requests":-1,"interval":"10s"}}
            """,
        )
        assertEquals(
            KeyInfo(
                limit = 1.0,
                limitRemaining = 0.75,
                limitReset = null,
                usage = 0.25,
                usageDaily = 0.0000035,
                usageWeekly = 0.1,
                usageMonthly = 0.2,
                freeRequests = FreeRequests(18, 1000),
                expiresAt = Instant.parse("2026-11-23T18:56:00.001Z"),
            ),
            data.toKeyInfo(),
        )
    }

    @Test
    fun `key without a limit`() {
        val data = JSONObject(
            """{"limit":null,"limit_reset":"monthly","limit_remaining":null,"usage":2,"usage_daily":0,"usage_weekly":0,"usage_monthly":1,"expires_at":null}""",
        )
        val key = data.toKeyInfo()
        assertNull(key.limit)
        assertNull(key.limitRemaining)
        assertEquals("monthly", key.limitReset)
        assertNull(key.freeRequests)
        assertNull(key.expiresAt)
        assertEquals(2.0, key.usage, 0.0)
    }

    @Test
    fun credits() {
        val credits = JSONObject("""{"total_credits":34,"total_usage":33.54651598}""").toCredits()
        assertEquals(0.45348402, credits.remaining, 1e-9)
    }
}
