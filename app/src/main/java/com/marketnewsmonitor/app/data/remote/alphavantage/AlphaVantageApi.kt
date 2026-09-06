package com.marketnewsmonitor.app.data.remote.alphavantage

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface AlphaVantageApi {
    @GET("query?function=TIME_SERIES_DAILY&outputsize=compact")
    suspend fun dailyTimeSeries(
        @Query("symbol") symbol: String,
        @Query("apikey") apiKey: String,
    ): AlphaVantageDailyResponse

    companion object {
        const val BASE_URL = "https://www.alphavantage.co/"
    }
}

@Serializable
data class AlphaVantageDailyResponse(
    @SerialName("Time Series (Daily)") val timeSeries: Map<String, AlphaVantageDailyBar>? = null,
)

@Serializable
data class AlphaVantageDailyBar(
    @SerialName("4. close") val close: String = "",
    @SerialName("5. volume") val volume: String = "",
)
