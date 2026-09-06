package com.marketnewsmonitor.app.data.remote.finnhub

import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.remote.NewsSource
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class FinnhubSource(
    private val api: FinnhubApi,
    private val apiKeyProvider: () -> String?,
) : NewsSource {
    override val id = SOURCE_ID

    override suspend fun fetch(ticker: Ticker): List<Article> {
        val token = apiKeyProvider()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val today = LocalDate.now(ZoneOffset.UTC)
        val dtos = api.companyNews(
            symbol = ticker.symbol,
            from = today.minusDays(LOOKBACK_DAYS).format(DateTimeFormatter.ISO_LOCAL_DATE),
            to = today.format(DateTimeFormatter.ISO_LOCAL_DATE),
            token = token,
        )
        return mapFinnhubNews(ticker.symbol, dtos)
    }

    companion object {
        const val SOURCE_ID = "finnhub"
        private const val LOOKBACK_DAYS = 7L
    }
}

/** Pure mapping, kept separate from [FinnhubSource.fetch] so it's testable without a network call. */
fun mapFinnhubNews(symbol: String, dtos: List<FinnhubNewsDto>): List<Article> =
    dtos.filter { it.url.isNotBlank() && it.headline.isNotBlank() }
        .map { dto ->
            Article(
                id = "${FinnhubSource.SOURCE_ID}|${dto.url}",
                tickerSymbol = symbol,
                sourceId = FinnhubSource.SOURCE_ID,
                headline = dto.headline,
                url = dto.url,
                publishedAt = dto.datetime * 1000L,
            )
        }
