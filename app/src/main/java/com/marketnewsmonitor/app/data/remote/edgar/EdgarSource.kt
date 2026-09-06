package com.marketnewsmonitor.app.data.remote.edgar

import com.marketnewsmonitor.app.data.local.entity.Article
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.remote.NewsSource
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class EdgarSource(
    private val api: EdgarApi,
    private val userAgentProvider: () -> String,
) : NewsSource {
    override val id = SOURCE_ID

    @Volatile private var cikByTicker: Map<String, String>? = null

    override suspend fun fetch(ticker: Ticker): List<Article> {
        val cik = resolveCik(ticker.symbol) ?: return emptyList()
        val submissions = api.getSubmissions(cik, userAgentProvider())
        return mapEdgarFilings(
            symbol = ticker.symbol,
            cik = cik,
            recent = submissions.filings.recent,
            cutoffDate = LocalDate.now(ZoneOffset.UTC).minusDays(LOOKBACK_DAYS),
        )
    }

    /** Ticker -> CIK map is ~1MB and rarely changes — fetched once per process, not per poll. */
    private suspend fun resolveCik(symbol: String): String? {
        val cached = cikByTicker
        val map = if (cached != null) {
            cached
        } else {
            val loaded = loadCikMap() ?: return null // fetch failed; don't cache, retry next call
            cikByTicker = loaded
            loaded
        }
        return map[symbol.uppercase()]
    }

    private suspend fun loadCikMap(): Map<String, String>? =
        try {
            api.getTickerCikMap(userAgentProvider()).values.associate {
                it.ticker.uppercase() to it.cikStr.toString().padStart(10, '0')
            }
        } catch (e: Exception) {
            null
        }

    companion object {
        const val SOURCE_ID = "sec_edgar"
        private const val LOOKBACK_DAYS = 7L
        val RELEVANT_FORMS = setOf("8-K", "4")

        const val DEFAULT_USER_AGENT = "MarketNewsMonitor (personal use; no contact provided)"
    }
}

/**
 * Not a hardcoded contact — built from the user's own Settings profile so no
 * personal info lives in source. Falls back to a generic identifier if the
 * user hasn't filled that in yet, so EDGAR calls degrade rather than fail.
 */
fun buildEdgarUserAgent(name: String, email: String): String {
    val trimmedName = name.trim()
    val trimmedEmail = email.trim()
    if (trimmedName.isEmpty() && trimmedEmail.isEmpty()) return EdgarSource.DEFAULT_USER_AGENT
    val contact = listOf(trimmedName, trimmedEmail).filter { it.isNotEmpty() }.joinToString(" ")
    return "MarketNewsMonitor ($contact)"
}

/** Pure, kept separate from [EdgarSource.fetch] so it's testable without a network call. */
fun mapEdgarFilings(symbol: String, cik: String, recent: EdgarRecentFilings, cutoffDate: LocalDate): List<Article> {
    val cikNoLeadingZeros = cik.trimStart('0').ifEmpty { "0" }
    val count = minOf(recent.form.size, recent.filingDate.size, recent.accessionNumber.size, recent.primaryDocument.size)

    return (0 until count).mapNotNull { i ->
        val form = recent.form[i]
        if (form !in EdgarSource.RELEVANT_FORMS) return@mapNotNull null

        val filingDate = try {
            LocalDate.parse(recent.filingDate[i], DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            return@mapNotNull null
        }
        if (filingDate.isBefore(cutoffDate)) return@mapNotNull null

        val accession = recent.accessionNumber[i]
        val document = recent.primaryDocument[i]
        if (document.isBlank()) return@mapNotNull null

        Article(
            id = "${EdgarSource.SOURCE_ID}|$accession|$document",
            tickerSymbol = symbol,
            sourceId = EdgarSource.SOURCE_ID,
            headline = "Form $form filed",
            url = "https://www.sec.gov/Archives/edgar/data/$cikNoLeadingZeros/${accession.replace("-", "")}/$document",
            publishedAt = filingDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
    }
}
