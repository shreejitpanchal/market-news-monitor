package com.marketnewsmonitor.app.data.remote.claude

import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker

/** Extracted so [com.marketnewsmonitor.app.work.DigestWorker] is testable without a real network call. */
interface DigestGenerator {
    /** Null when there's nothing to say (no key, empty input, or a failed/empty response) — the caller skips notifying entirely. */
    suspend fun generateDigest(tickerArticles: Map<Ticker, List<Article>>): String?
}

/**
 * Once-a-day, whole-watchlist summary — the one place this app uses Sonnet
 * instead of Haiku, since output quality here matters more than per-call
 * cost (docs/ARCHITECTURE.md). Unlike [ClaudeArticleClassifier], the
 * response is plain text meant to go straight into a notification body, not
 * JSON — there's no structured data the app needs back.
 */
class ClaudeDigestGenerator(
    private val api: ClaudeApi,
    private val apiKeyProvider: () -> String?,
) : DigestGenerator {

    override suspend fun generateDigest(tickerArticles: Map<Ticker, List<Article>>): String? {
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return null
        val notable = tickerArticles.filterValues { it.isNotEmpty() }
        if (notable.isEmpty()) return null

        val response = try {
            api.createMessage(
                apiKey = key,
                request = ClaudeMessageRequest(
                    model = ClaudeApi.MODEL_SONNET,
                    maxTokens = MAX_TOKENS,
                    messages = listOf(ClaudeMessage(role = "user", content = buildDigestPrompt(notable))),
                ),
            )
        } catch (e: Exception) {
            return null
        }

        return response.content.firstOrNull { it.type == "text" }?.text?.trim()?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val MAX_TOKENS = 1024
    }
}

/** Pure, kept separate from [ClaudeDigestGenerator.generateDigest] so it's testable without a network call. */
fun buildDigestPrompt(tickerArticles: Map<Ticker, List<Article>>): String {
    val perTicker = tickerArticles.entries.joinToString("\n\n") { (ticker, articles) ->
        val label = ticker.companyName?.let { "${ticker.symbol} ($it)" } ?: ticker.symbol
        val lines = articles.joinToString("\n") { article ->
            val why = article.whyItMatters?.let { " — $it" }.orEmpty()
            "- ${article.headline}$why"
        }
        "$label:\n$lines"
    }
    return """
        You are writing a concise pre-market digest for a trader's stock/options
        watchlist. Below is every ticker with notable (hot/warm) news from the
        last 24 hours, and why it matters where known. Write a short digest —
        a sentence or two per ticker. Plain text only, no markdown, no
        headers, suitable to read directly in a phone notification.

        $perTicker
    """.trimIndent()
}
