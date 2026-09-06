package com.marketnewsmonitor.webapp

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class FinnhubSymbolSearchResponse(val result: List<FinnhubSymbolSearchResult> = emptyList())

@Serializable
private data class FinnhubSymbolSearchResult(val symbol: String = "", val description: String = "")

@Serializable
private data class FinnhubNewsDto(val headline: String = "", val url: String = "", val source: String = "")

/**
 * Goes through the local proxy (server/), which attaches the Finnhub key --
 * see CLAUDE.md's webapp/server decision. Ported from
 * app/src/main/java/.../finnhub/TickerSymbolSearch.kt and FinnhubSource.kt's
 * mapping logic, minus date-window filtering (computed server-side instead,
 * see server/'s dedicated /proxy/finnhub/news route) to avoid needing a
 * date-arithmetic library on this Wasm target.
 */
object FinnhubClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<TickerSuggestion> {
        if (query.isBlank()) return emptyList()
        return try {
            val text = proxyGet("/proxy/finnhub/search?q=${query.encodeURLParameter()}")
            val response = json.decodeFromString<FinnhubSymbolSearchResponse>(text)
            response.result
                .filter { it.symbol.isNotBlank() && !it.symbol.contains(".") }
                .map { TickerSuggestion(it.symbol, it.description) }
                .distinctBy { it.symbol }
                .take(8)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun companyNews(symbol: String): List<Article> = try {
        val text = proxyGet("/proxy/finnhub/news?symbol=$symbol")
        val dtos = json.decodeFromString<List<FinnhubNewsDto>>(text)
        dtos.filter { it.url.isNotBlank() && it.headline.isNotBlank() }
            .map { dto -> Article(id = "finnhub|${dto.url}", headline = dto.headline, url = dto.url, source = "Finnhub") }
    } catch (e: Exception) {
        emptyList()
    }
}
