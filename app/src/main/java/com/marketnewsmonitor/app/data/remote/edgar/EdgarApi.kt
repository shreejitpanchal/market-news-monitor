package com.marketnewsmonitor.app.data.remote.edgar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

/**
 * Two static-URL endpoints on different subdomains — declared as full
 * absolute URLs in the annotations, so [BASE_URL] is only a formality
 * Retrofit requires; it's never actually resolved against.
 */
interface EdgarApi {
    @GET("https://www.sec.gov/files/company_tickers.json")
    suspend fun getTickerCikMap(@Header("User-Agent") userAgent: String): Map<String, EdgarTickerCikEntry>

    @GET("https://data.sec.gov/submissions/CIK{cik}.json")
    suspend fun getSubmissions(@Path("cik") cik: String, @Header("User-Agent") userAgent: String): EdgarSubmissionsResponse

    companion object {
        const val BASE_URL = "https://www.sec.gov/"
    }
}

@Serializable
data class EdgarTickerCikEntry(
    @SerialName("cik_str") val cikStr: Long,
    val ticker: String,
    val title: String = "",
)

@Serializable
data class EdgarSubmissionsResponse(val filings: EdgarFilings = EdgarFilings())

@Serializable
data class EdgarFilings(val recent: EdgarRecentFilings = EdgarRecentFilings())

/** SEC models this as a struct of parallel arrays, not an array of objects — indices line up across all four. */
@Serializable
data class EdgarRecentFilings(
    val accessionNumber: List<String> = emptyList(),
    val filingDate: List<String> = emptyList(),
    val form: List<String> = emptyList(),
    val primaryDocument: List<String> = emptyList(),
)
