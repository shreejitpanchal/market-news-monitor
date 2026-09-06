package com.marketnewsmonitor.app

import android.content.Context
import com.marketnewsmonitor.app.data.backup.BackupRepository
import com.marketnewsmonitor.app.data.local.AppDatabase
import com.marketnewsmonitor.app.data.notifications.NotificationHelper
import com.marketnewsmonitor.app.data.remote.NewsSourceRegistry
import com.marketnewsmonitor.app.data.remote.claude.ClaudeApi
import com.marketnewsmonitor.app.data.remote.claude.ClaudeArticleClassifier
import com.marketnewsmonitor.app.data.remote.edgar.EdgarApi
import com.marketnewsmonitor.app.data.remote.edgar.EdgarSource
import com.marketnewsmonitor.app.data.remote.edgar.buildEdgarUserAgent
import com.marketnewsmonitor.app.data.remote.finnhub.FinnhubApi
import com.marketnewsmonitor.app.data.remote.finnhub.FinnhubSource
import com.marketnewsmonitor.app.data.remote.rss.GoogleNewsRssSource
import com.marketnewsmonitor.app.data.settings.AppPreferences
import com.marketnewsmonitor.app.data.settings.SecureSettingsStore
import com.marketnewsmonitor.app.repository.NewsRepository
import com.marketnewsmonitor.app.repository.TickerRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Manual dependency container, built once in [MarketNewsMonitorApp] and
 * threaded down through ViewModel factories — no DI framework, no global
 * singletons/statics.
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.build(context)

    val tickerRepository = TickerRepository(database.tickerDao())
    val secureSettingsStore = SecureSettingsStore(context)
    val appPreferences = AppPreferences(context)
    val backupRepository = BackupRepository(context, tickerRepository, secureSettingsStore, appPreferences)
    val notificationHelper = NotificationHelper(context)

    private val okHttpClient = OkHttpClient()
    private val jsonConverterFactory = Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType())

    private val finnhubApi: FinnhubApi = Retrofit.Builder()
        .baseUrl(FinnhubApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(jsonConverterFactory)
        .build()
        .create(FinnhubApi::class.java)

    private val claudeApi: ClaudeApi = Retrofit.Builder()
        .baseUrl(ClaudeApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(jsonConverterFactory)
        .build()
        .create(ClaudeApi::class.java)

    private val edgarApi: EdgarApi = Retrofit.Builder()
        .baseUrl(EdgarApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(jsonConverterFactory)
        .build()
        .create(EdgarApi::class.java)

    private val newsSourceRegistry = NewsSourceRegistry(
        listOf(
            FinnhubSource(finnhubApi) { secureSettingsStore.getFinnhubApiKey() },
            GoogleNewsRssSource(okHttpClient),
            EdgarSource(edgarApi) { buildEdgarUserAgent(appPreferences.userName, appPreferences.userEmail) },
        ),
    )

    private val articleClassifier = ClaudeArticleClassifier(claudeApi) { secureSettingsStore.getClaudeApiKey() }

    val newsRepository = NewsRepository(database.articleDao(), newsSourceRegistry, articleClassifier)
}
