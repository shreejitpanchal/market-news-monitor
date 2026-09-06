package com.marketnewsmonitor.app.data.remote.claude

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ClaudeApi {
    @POST("v1/messages")
    suspend fun createMessage(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = ANTHROPIC_VERSION,
        @Body request: ClaudeMessageRequest,
    ): ClaudeMessageResponse

    companion object {
        const val BASE_URL = "https://api.anthropic.com/"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MODEL_HAIKU = "claude-haiku-4-5-20251001"
    }
}

@Serializable
data class ClaudeMessageRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<ClaudeMessage>,
)

@Serializable
data class ClaudeMessage(val role: String, val content: String)

@Serializable
data class ClaudeMessageResponse(val content: List<ClaudeContentBlock> = emptyList())

@Serializable
data class ClaudeContentBlock(val type: String = "", val text: String = "")
