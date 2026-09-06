package com.marketnewsmonitor.app.ui.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.marketnewsmonitor.app.MarketNewsMonitorApp
import com.marketnewsmonitor.app.data.backup.BackupRepository
import com.marketnewsmonitor.app.data.settings.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.GeneralSecurityException

sealed interface OnboardingImportStatus {
    data object Idle : OnboardingImportStatus
    data object Importing : OnboardingImportStatus
    data class Error(val message: String) : OnboardingImportStatus
}

class ProfileOnboardingViewModel(
    private val appPreferences: AppPreferences,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _importStatus = MutableStateFlow<OnboardingImportStatus>(OnboardingImportStatus.Idle)
    val importStatus: StateFlow<OnboardingImportStatus> = _importStatus.asStateFlow()

    fun saveProfile(name: String, email: String) {
        appPreferences.userName = name.trim()
        appPreferences.userEmail = email.trim()
    }

    /**
     * Restoring a backup fills in name/email from the export, satisfying the
     * same mandatory gate this screen otherwise enforces via typed input.
     */
    fun importSetup(uri: Uri, password: String, onComplete: () -> Unit) {
        _importStatus.value = OnboardingImportStatus.Importing
        viewModelScope.launch {
            try {
                backupRepository.importFrom(uri, password)
                if (appPreferences.hasCompletedProfile) {
                    _importStatus.value = OnboardingImportStatus.Idle
                    onComplete()
                } else {
                    _importStatus.value =
                        OnboardingImportStatus.Error("That backup didn't include a name and email — enter them below.")
                }
            } catch (e: GeneralSecurityException) {
                _importStatus.value = OnboardingImportStatus.Error("Import failed: incorrect password or corrupted file.")
            } catch (e: Exception) {
                _importStatus.value = OnboardingImportStatus.Error("Import failed: ${e.message}")
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MarketNewsMonitorApp
                ProfileOnboardingViewModel(app.container.appPreferences, app.container.backupRepository)
            }
        }
    }
}
