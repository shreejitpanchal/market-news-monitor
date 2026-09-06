package com.marketnewsmonitor.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.marketnewsmonitor.app.MarketNewsMonitorApp
import com.marketnewsmonitor.app.data.backup.BackupRepository
import com.marketnewsmonitor.app.data.settings.SecureSettingsStore
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
    private val secureSettingsStore: SecureSettingsStore,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _claudeApiKey = MutableStateFlow(secureSettingsStore.getClaudeApiKey().orEmpty())
    val claudeApiKey: StateFlow<String> = _claudeApiKey.asStateFlow()

    private val _finnhubApiKey = MutableStateFlow(secureSettingsStore.getFinnhubApiKey().orEmpty())
    val finnhubApiKey: StateFlow<String> = _finnhubApiKey.asStateFlow()

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
                SettingsViewModel(app.container.secureSettingsStore, app.container.backupRepository)
            }
        }
    }
}
