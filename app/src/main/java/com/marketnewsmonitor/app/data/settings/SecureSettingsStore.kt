package com.marketnewsmonitor.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed storage for API keys (Claude, Finnhub). Never logged,
 * never hardcoded — entered once here and read only at the point of use.
 */
class SecureSettingsStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getClaudeApiKey(): String? = prefs.getString(KEY_CLAUDE_API_KEY, null)

    fun setClaudeApiKey(value: String?) {
        prefs.edit().putString(KEY_CLAUDE_API_KEY, value).apply()
    }

    fun getFinnhubApiKey(): String? = prefs.getString(KEY_FINNHUB_API_KEY, null)

    fun setFinnhubApiKey(value: String?) {
        prefs.edit().putString(KEY_FINNHUB_API_KEY, value).apply()
    }

    fun getAlphaVantageApiKey(): String? = prefs.getString(KEY_ALPHA_VANTAGE_API_KEY, null)

    fun setAlphaVantageApiKey(value: String?) {
        prefs.edit().putString(KEY_ALPHA_VANTAGE_API_KEY, value).apply()
    }

    private companion object {
        const val KEY_CLAUDE_API_KEY = "claude_api_key"
        const val KEY_FINNHUB_API_KEY = "finnhub_api_key"
        const val KEY_ALPHA_VANTAGE_API_KEY = "alpha_vantage_api_key"
    }
}
