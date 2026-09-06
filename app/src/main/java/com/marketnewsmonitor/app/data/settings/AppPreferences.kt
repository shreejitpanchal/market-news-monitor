package com.marketnewsmonitor.app.data.settings

import android.content.Context

/**
 * Plain (unencrypted) preferences — polling on/off and interval aren't
 * secrets, so this stays separate from [SecureSettingsStore]'s
 * EncryptedSharedPreferences rather than paying that cost for non-sensitive
 * values.
 */
class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var pollingEnabled: Boolean
        get() = prefs.getBoolean(KEY_POLLING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_POLLING_ENABLED, value).apply()

    var pollIntervalMinutes: Long
        get() = prefs.getLong(KEY_POLL_INTERVAL_MINUTES, DEFAULT_POLL_INTERVAL_MINUTES)
        set(value) = prefs.edit().putLong(KEY_POLL_INTERVAL_MINUTES, value).apply()

    /** Not a secret — used only to build the User-Agent SEC EDGAR's fair-access policy requires. */
    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    /** Daily pre-market digest, fixed 8:00 AM device-local time (see DigestScheduler). Off by default. */
    var digestEnabled: Boolean
        get() = prefs.getBoolean(KEY_DIGEST_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DIGEST_ENABLED, value).apply()

    companion object {
        // WorkManager's own floor for periodic work.
        const val DEFAULT_POLL_INTERVAL_MINUTES = 15L
        val ALLOWED_POLL_INTERVALS_MINUTES = listOf(15L, 30L, 60L)

        private const val PREFS_NAME = "app_preferences"
        private const val KEY_POLLING_ENABLED = "polling_enabled"
        private const val KEY_POLL_INTERVAL_MINUTES = "poll_interval_minutes"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_DIGEST_ENABLED = "digest_enabled"
    }
}
