package com.marketnewsmonitor.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.marketnewsmonitor.app.MarketNewsMonitorApp
import com.marketnewsmonitor.app.data.backup.BackupRepository
import com.marketnewsmonitor.app.data.settings.AppPreferences
import com.marketnewsmonitor.app.data.settings.SecureSettingsStore
import com.marketnewsmonitor.app.work.DigestScheduler
import com.marketnewsmonitor.app.work.PollScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Exporting : BackupStatus
    data object Importing : BackupStatus
    data class Success(val message: String) : BackupStatus
    data class Error(val message: String) : BackupStatus
}

class SettingsViewModel(
    private val appContext: Context,
    private val secureSettingsStore: SecureSettingsStore,
    private val backupRepository: BackupRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val _claudeApiKey = MutableStateFlow(secureSettingsStore.getClaudeApiKey().orEmpty())
    val claudeApiKey: StateFlow<String> = _claudeApiKey.asStateFlow()

    private val _finnhubApiKey = MutableStateFlow(secureSettingsStore.getFinnhubApiKey().orEmpty())
    val finnhubApiKey: StateFlow<String> = _finnhubApiKey.asStateFlow()

    private val _alphaVantageApiKey = MutableStateFlow(secureSettingsStore.getAlphaVantageApiKey().orEmpty())
    val alphaVantageApiKey: StateFlow<String> = _alphaVantageApiKey.asStateFlow()

    private val _pollingEnabled = MutableStateFlow(appPreferences.pollingEnabled)
    val pollingEnabled: StateFlow<Boolean> = _pollingEnabled.asStateFlow()

    private val _pollIntervalMinutes = MutableStateFlow(appPreferences.pollIntervalMinutes)
    val pollIntervalMinutes: StateFlow<Long> = _pollIntervalMinutes.asStateFlow()

    private val _userName = MutableStateFlow(appPreferences.userName)
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow(appPreferences.userEmail)
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _digestEnabled = MutableStateFlow(appPreferences.digestEnabled)
    val digestEnabled: StateFlow<Boolean> = _digestEnabled.asStateFlow()

    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    fun saveClaudeApiKey(value: String) {
        secureSettingsStore.setClaudeApiKey(value.trim().takeIf { it.isNotEmpty() })
        _claudeApiKey.value = value
    }

    fun saveFinnhubApiKey(value: String) {
        secureSettingsStore.setFinnhubApiKey(value.trim().takeIf { it.isNotEmpty() })
        _finnhubApiKey.value = value
    }

    fun saveAlphaVantageApiKey(value: String) {
        secureSettingsStore.setAlphaVantageApiKey(value.trim().takeIf { it.isNotEmpty() })
        _alphaVantageApiKey.value = value
    }

    /**
     * Enables/disables the background poll. The caller (Settings screen) is
     * responsible for requesting the POST_NOTIFICATIONS permission first on
     * API 33+ — this is called either way, since polling can run without it
     * (articles still get fetched; they just won't surface as notifications
     * until the permission is granted).
     */
    fun setPollingEnabled(enabled: Boolean) {
        appPreferences.pollingEnabled = enabled
        _pollingEnabled.value = enabled
        if (enabled) {
            PollScheduler.schedule(appContext, appPreferences.pollIntervalMinutes)
        } else {
            PollScheduler.cancel(appContext)
        }
    }

    fun setPollIntervalMinutes(minutes: Long) {
        appPreferences.pollIntervalMinutes = minutes
        _pollIntervalMinutes.value = minutes
        if (appPreferences.pollingEnabled) {
            PollScheduler.schedule(appContext, minutes)
        }
    }

    /**
     * Same shape as [setPollingEnabled]: the caller requests the
     * POST_NOTIFICATIONS permission first on API 33+, this is called either way.
     */
    fun setDigestEnabled(enabled: Boolean) {
        appPreferences.digestEnabled = enabled
        _digestEnabled.value = enabled
        if (enabled) {
            DigestScheduler.schedule(appContext)
        } else {
            DigestScheduler.cancel(appContext)
        }
    }

    fun saveUserProfile(name: String, email: String) {
        appPreferences.userName = name.trim()
        appPreferences.userEmail = email.trim()
        _userName.value = appPreferences.userName
        _userEmail.value = appPreferences.userEmail
    }

    fun exportSetup(uri: Uri) {
        _status.value = BackupStatus.Exporting
        viewModelScope.launch {
            _status.value = try {
                backupRepository.exportTo(uri)
                BackupStatus.Success("Setup exported.")
            } catch (e: Exception) {
                BackupStatus.Error("Export failed: ${e.message}")
            }
        }
    }

    fun importSetup(uri: Uri) {
        _status.value = BackupStatus.Importing
        viewModelScope.launch {
            _status.value = try {
                backupRepository.importFrom(uri)
                _claudeApiKey.value = secureSettingsStore.getClaudeApiKey().orEmpty()
                _finnhubApiKey.value = secureSettingsStore.getFinnhubApiKey().orEmpty()
                _alphaVantageApiKey.value = secureSettingsStore.getAlphaVantageApiKey().orEmpty()
                _userName.value = appPreferences.userName
                _userEmail.value = appPreferences.userEmail
                BackupStatus.Success("Setup imported. Watchlist and API keys restored.")
            } catch (e: Exception) {
                BackupStatus.Error("Import failed: ${e.message}")
            }
        }
    }

    fun dismissStatus() {
        _status.value = BackupStatus.Idle
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MarketNewsMonitorApp
                SettingsViewModel(
                    app,
                    app.container.secureSettingsStore,
                    app.container.backupRepository,
                    app.container.appPreferences,
                )
            }
        }
    }
}
