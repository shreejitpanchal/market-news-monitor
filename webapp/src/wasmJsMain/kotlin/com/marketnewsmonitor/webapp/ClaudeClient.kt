package com.marketnewsmonitor.webapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class ClaudeMessageRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<ClaudeMessage>,
)

@Serializable
private data class ClaudeMessage(val role: String, val content: String)

@Serializable
private data class ClaudeMessageResponse(val content: List<ClaudeContentBlock> = emptyList())

@Serializable
private data class ClaudeContentBlock(val type: String = "", val text: String = "")

@Serializable
private data class ClassificationDto(val id: String, val urgency: String, val why: String)

private const val MODEL_HAIKU = "claude-haiku-4-5-20251001"
private const val MAX_ARTICLES_PER_CALL = 20

/**
 * Goes through the local proxy, which attaches the Claude key -- see
 * CLAUDE.md's webapp/server decision. Ported from app/src/main/java/.../
 * claude/ClaudeArticleClassifier.kt, minus dedup clustering (dropped for
 * this simpler web client -- see Models.kt).
 */
object ClaudeClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun classify(tickerSymbol: String, companyName: String?, articles: List<Article>): Map<String, Article> {
        if (articles.isEmpty()) return emptyMap()
        val batch = articles.take(MAX_ARTICLES_PER_CALL)
        val requestBody = json.encodeToString(
            ClaudeMessageRequest(
                model = MODEL_HAIKU,
                maxTokens = 1024,
                messages = listOf(ClaudeMessage("user", buildPrompt(tickerSymbol, companyName, batch))),
            ),
        )
        return try {
            val responseText = proxyPost("/proxy/claude/messages", requestBody)
            val response = json.decodeFromString<ClaudeMessageResponse>(responseText)
            val text = response.content.firstOrNull { it.type == "text" }?.text ?: return emptyMap()
            val byId = batch.associateBy { it.id }
            parseClassifications(text).mapNotNull { dto ->
                byId[dto.id]?.let { article ->
                    dto.id to article.copy(
                        urgency = Urgency.entries.firstOrNull { it.name.equals(dto.urgency, ignoreCase = true) },
                        whyItMatters = dto.why,
                    )
                }
            }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun buildPrompt(symbol: String, companyName: String?, articles: List<Article>): String {
        val label = companyName?.let { "$symbol ($it)" } ?: symbol
        val list = articles.joinToString("\n") { "- id: \"${it.id}\", headline: \"${it.headline.replace("\"", "\\\"")}\"" }
        return """
            You are helping a trader triage news for $label. For each article
            below, classify its urgency as one of "hot", "warm", or "calm", and
            write one short sentence on why it matters (or doesn't) for the stock.

            Articles:
            $list

            Respond with ONLY a JSON array, no other text, in this exact shape:
            [{"id": "...", "urgency": "hot|warm|calm", "why": "..."}]
        """.trimIndent()
    }

    private fun parseClassifications(text: String): List<ClassificationDto> {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return try {
            json.decodeFromString<List<ClassificationDto>>(cleaned)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
