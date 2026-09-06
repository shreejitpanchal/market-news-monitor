package com.marketnewsmonitor.app.data.backup

import android.content.Context
import android.net.Uri
import com.marketnewsmonitor.app.data.local.entity.Ticker
import com.marketnewsmonitor.app.data.settings.AppPreferences
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
    private val appPreferences: AppPreferences,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val envelopeJson = Json { ignoreUnknownKeys = true }

    /** The whole file is password-encrypted (see [BackupCrypto]) — password-based, not device-bound, so a backup restores after a reinstall or on a new phone. */
    suspend fun exportTo(uri: Uri, password: String) = withContext(Dispatchers.IO) {
        val backup = BackupData(
            exportedAt = System.currentTimeMillis(),
            tickers = tickerRepository.getTickers().map {
                BackupTicker(it.symbol, it.companyName, it.addedAt, it.muted)
            },
            apiKey = secureSettingsStore.getClaudeApiKey(),
            finnhubApiKey = secureSettingsStore.getFinnhubApiKey(),
            userName = appPreferences.userName,
            userEmail = appPreferences.userEmail,
            alphaVantageApiKey = secureSettingsStore.getAlphaVantageApiKey(),
        )
        val plaintext = json.encodeToString(BackupData.serializer(), backup).encodeToByteArray()
        val envelope = BackupCrypto.encrypt(plaintext, password)

        val resolver = context.contentResolver
        resolver.openOutputStream(uri)?.use { out ->
            out.write(envelopeJson.encodeToString(EncryptedBackupEnvelope.serializer(), envelope).toByteArray())
        } ?: throw IOException("Could not open $uri for writing")
    }

    /** @throws java.security.GeneralSecurityException if [password] is wrong or the file was tampered with. */
    suspend fun importFrom(uri: Uri, password: String) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val text = resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IOException("Could not open $uri for reading")
        val envelope = envelopeJson.decodeFromString(EncryptedBackupEnvelope.serializer(), text)
        val plaintext = BackupCrypto.decrypt(envelope, password)
        val backup = json.decodeFromString(BackupData.serializer(), plaintext.decodeToString())

        tickerRepository.replaceAll(
            backup.tickers.map { Ticker(it.symbol, it.companyName, it.addedAt, it.muted) },
        )
        secureSettingsStore.setClaudeApiKey(backup.apiKey)
        secureSettingsStore.setFinnhubApiKey(backup.finnhubApiKey)
        appPreferences.userName = backup.userName
        appPreferences.userEmail = backup.userEmail
        secureSettingsStore.setAlphaVantageApiKey(backup.alphaVantageApiKey)
    }
}
