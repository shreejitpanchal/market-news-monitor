package com.marketnewsmonitor.app.data.backup

import android.content.Context
import android.net.Uri
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.settings.SecureSettingsStore
import com.marketnewsmonitor.app.repository.TickerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

class BackupRepository(
    private val context: Context,
    private val tickerRepository: TickerRepository,
    private val secureSettingsStore: SecureSettingsStore,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = BackupData(
            exportedAt = System.currentTimeMillis(),
            tickers = tickerRepository.getTickers().map {
                BackupTicker(it.symbol, it.companyName, it.addedAt)
            },
            apiKey = secureSettingsStore.getClaudeApiKey(),
            finnhubApiKey = secureSettingsStore.getFinnhubApiKey(),
        )
        val resolver = context.contentResolver
        resolver.openOutputStream(uri)?.use { out ->
            out.write(json.encodeToString(BackupData.serializer(), backup).toByteArray())
        } ?: throw IOException("Could not open $uri for writing")
    }

    suspend fun importFrom(uri: Uri) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IOException("Could not open $uri for reading")
        val backup = json.decodeFromString(BackupData.serializer(), text)

        tickerRepository.replaceAll(
            backup.tickers.map { Ticker(it.symbol, it.companyName, it.addedAt) },
        )
        secureSettingsStore.setClaudeApiKey(backup.apiKey)
        secureSettingsStore.setFinnhubApiKey(backup.finnhubApiKey)
    }
}
