package com.marketnewsmonitor.webapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class EdgarTickerCikEntry(@SerialName("cik_str") val cikStr: Long = 0L, val ticker: String = "")

@Serializable
private data class EdgarSubmissionsResponse(val filings: EdgarFilings = EdgarFilings())

@Serializable
private data class EdgarFilings(val recent: EdgarRecentFilings = EdgarRecentFilings())

@Serializable
private data class EdgarRecentFilings(
    val accessionNumber: List<String> = emptyList(),
    val form: List<String> = emptyList(),
    val primaryDocument: List<String> = emptyList(),
)

private val RELEVANT_FORMS = setOf("8-K", "4")
private const val MAX_FILINGS = 10

/**
 * Goes through the local proxy -- see CLAUDE.md's webapp/server decision.
 * Ported from app/src/main/java/.../edgar/EdgarSource.kt, minus the 7-day
 * cutoff filter: SEC's "recent" filings array is already newest-first, so
 * taking the first N relevant-form entries is a reasonable simplification
 * that avoids needing date arithmetic on this Wasm target.
 */
object EdgarClient {
    private val json = Json { ignoreUnknownKeys = true }

    // The ticker->CIK map is the whole market (~1MB) and rarely changes --
    // cached in memory for the page's lifetime, same reasoning as the
    // Android app's per-process cache.
    private var cikByTicker: Map<String, String>? = null

    suspend fun filings(symbol: String): List<Article> {
        val cik = resolveCik(symbol) ?: return emptyList()
        return try {
            val text = proxyGet("/proxy/edgar/submissions/$cik")
            val response = json.decodeFromString<EdgarSubmissionsResponse>(text)
            val recent = response.filings.recent
            val count = minOf(recent.form.size, recent.accessionNumber.size, recent.primaryDocument.size)
            val cikNoLeadingZeros = cik.trimStart('0').ifEmpty { "0" }
            (0 until count)
                .filter { recent.form[it] in RELEVANT_FORMS && recent.primaryDocument[it].isNotBlank() }
                .take(MAX_FILINGS)
                .map { i ->
                    val accession = recent.accessionNumber[i]
                    val document = recent.primaryDocument[i]
                    Article(
                        id = "sec_edgar|$accession|$document",
                        headline = "Form ${recent.form[i]} filed",
                        url = "https://www.sec.gov/Archives/edgar/data/$cikNoLeadingZeros/${accession.replace("-", "")}/$document",
                        source = "SEC EDGAR",
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveCik(symbol: String): String? {
        val cached = cikByTicker
        val map = if (cached != null) {
            cached
        } else {
            val loaded = loadCikMap() ?: return null
            cikByTicker = loaded
            loaded
        }
        return map[symbol.uppercase()]
    }

    private suspend fun loadCikMap(): Map<String, String>? = try {
        val text = proxyGet("/proxy/edgar/tickers")
        json.decodeFromString<Map<String, EdgarTickerCikEntry>>(text)
            .values.associate { it.ticker.uppercase() to it.cikStr.toString().padStart(10, '0') }
    } catch (e: Exception) {
        null
    }
}
