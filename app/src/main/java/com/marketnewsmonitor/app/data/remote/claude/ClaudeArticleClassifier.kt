package com.marketnewsmonitor.app.data.remote.claude

import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.local.entity.Urgency
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class ArticleClassification(val urgency: String, val whyItMatters: String)

/** Extracted so [com.marketnewsmonitor.app.repository.NewsRepository] is testable without a real network call. */
interface ArticleClassifier {
    suspend fun classify(ticker: Ticker, articles: List<Article>): Map<String, ArticleClassification>
}

/**
 * Classifies a batch of a ticker's unclassified articles in one Haiku call —
 * cheaper than one call per article, and the natural unit for a future
 * clustering pass (deferred — see CLAUDE.md) that needs to see them together
 * anyway.
 */
class ClaudeArticleClassifier(
    private val api: ClaudeApi,
    private val apiKeyProvider: () -> String?,
) : ArticleClassifier {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun classify(ticker: Ticker, articles: List<Article>): Map<String, ArticleClassification> {
        val key = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return emptyMap()
        if (articles.isEmpty()) return emptyMap()

        val response = try {
            api.createMessage(
                apiKey = key,
                request = ClaudeMessageRequest(
                    model = ClaudeApi.MODEL_HAIKU,
                    maxTokens = MAX_TOKENS,
                    messages = listOf(ClaudeMessage(role = "user", content = buildClassificationPrompt(ticker, articles))),
                ),
            )
        } catch (e: Exception) {
            return emptyMap()
        }

        val text = response.content.firstOrNull { it.type == "text" }?.text ?: return emptyMap()
        return parseClassificationResponse(text, json)
            .associate { it.id to ArticleClassification(it.urgency, it.why) }
    }

    companion object {
        private const val MAX_TOKENS = 1024
        const val MAX_ARTICLES_PER_CALL = 20
    }
}

@Serializable
data class ClassificationDto(val id: String, val urgency: String, val why: String)

/** Pure, kept separate from [ClaudeArticleClassifier.classify] so it's testable without a network call. */
fun buildClassificationPrompt(ticker: Ticker, articles: List<Article>): String {
    val tickerLabel = ticker.companyName?.let { "${ticker.symbol} ($it)" } ?: ticker.symbol
    val articlesJson = articles.joinToString(",\n") { "  {\"id\": ${jsonQuote(it.id)}, \"headline\": ${jsonQuote(it.headline)}}" }
    return """
        You are a financial news triage assistant for a trader watching $tickerLabel.
        For each article below, classify how urgent it is and write ONE short sentence
        explaining why it might move the stock (or why it likely doesn't).

        Urgency levels:
        - "${Urgency.HOT}": material news likely to move the stock soon (earnings surprise, M&A, regulatory action, major guidance change)
        - "${Urgency.WARM}": relevant but not urgent (analyst notes, minor product news, sector commentary)
        - "${Urgency.CALM}": routine or low-relevance (recaps, opinion pieces, unrelated mentions)

        Articles:
        [
        $articlesJson
        ]

        Respond with ONLY a JSON array, no other text, no markdown fences:
        [{"id": "...", "urgency": "hot|warm|calm", "why": "..."}]
    """.trimIndent()
}

/** Pure, kept separate from [ClaudeArticleClassifier.classify] so it's testable without a network call. */
fun parseClassificationResponse(text: String, json: Json = Json { ignoreUnknownKeys = true }): List<ClassificationDto> {
    val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return try {
        json.decodeFromString<List<ClassificationDto>>(cleaned)
    } catch (e: Exception) {
        emptyList()
    }
}

private fun jsonQuote(value: String): String = Json.encodeToString(String.serializer(), value)
