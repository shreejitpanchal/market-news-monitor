package com.marketnewsmonitor.app.data.remote.finnhub

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface FinnhubApi {
    @GET("company-news")
    suspend fun companyNews(
        @Query("symbol") symbol: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("token") token: String,
    ): List<FinnhubNewsDto>

    companion object {
        const val BASE_URL = "https://finnhub.io/api/v1/"
    }
}

@Serializable
data class FinnhubNewsDto(
    val headline: String = "",
    val url: String = "",
    val source: String = "",
    val datetime: Long = 0L,
)
