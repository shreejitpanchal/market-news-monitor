package com.marketnewsmonitor.app.data.backup

import kotlinx.serialization.Serializable

/**
 * A full snapshot of app setup for export/import.
 *
 * Includes the Claude and Finnhub API keys in plain text by explicit user
 * choice — this file is a secret once written and must be stored securely by
 * whoever holds it. See the Settings screen's export warning.
 */
@Serializable
data class BackupData(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val tickers: List<BackupTicker>,
    val apiKey: String?,
    // Added in version 2 — defaulted so version-1 export files still decode.
    val finnhubApiKey: String? = null,
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class BackupTicker(
    val symbol: String,
    val companyName: String?,
    val addedAt: Long,
    // Added alongside Ticker.muted (Phase 3) — defaulted so older exports decode.
    val muted: Boolean = false,
)
