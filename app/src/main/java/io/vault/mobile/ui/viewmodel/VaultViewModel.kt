package io.vault.mobile.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.vault.mobile.data.local.PreferenceManager
import io.vault.mobile.data.local.entity.VaultEntry
import io.vault.mobile.data.repository.VaultRepository
import io.vault.mobile.data.backup.BackupManager
import io.vault.mobile.security.BiometricAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppInfo(
    val name: String,
    val packageName: String
)

enum class PasswordStrength {
    Poor, Weak, Strong
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val preferenceManager: PreferenceManager,
    private val backupManager: BackupManager,
    private val driveManager: io.vault.mobile.data.cloud.GoogleDriveManager,
    private val biometricAuthenticator: BiometricAuthenticator,
    private val masterKeyManager: io.vault.mobile.security.MasterKeyManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isConnectedToDrive = MutableStateFlow(false)
    val isConnectedToDrive: StateFlow<Boolean> = _isConnectedToDrive.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _remoteKeyExists = MutableStateFlow(false)
    val remoteKeyExists: StateFlow<Boolean> = _remoteKeyExists.asStateFlow()

    init {
        fetchInstalledApps()
        attemptAutoConnect()
    }

    private fun attemptAutoConnect() {
        viewModelScope.launch {
            val success = driveManager.silentSignIn()
            if (success) {
                checkDriveConnection()
            }
        }
    }

    fun onDriveConnected() {
        checkDriveConnection()
    }

    fun checkDriveConnection() {
        viewModelScope.launch {
            _isConnectedToDrive.value = driveManager.isConnected()
            if (_isConnectedToDrive.value) {
                _remoteKeyExists.value = masterKeyManager.hasRemoteMasterKey()
            } else {
                _remoteKeyExists.value = false
            }
        }
    }

    private fun fetchInstalledApps() {
        viewModelScope.launch {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
            intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            
            val apps = pm.queryIntentActivities(intent, 0).map { resolveInfo ->
                AppInfo(
                    name = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName
                )
            }.sortedBy { it.name }
            
            _installedApps.value = apps
        }
    }

    fun syncToCloud(password: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            val success = backupManager.createBackup(password)
            if (!success) {
                _syncError.value = "Failed to sync to Google Drive. Check connection."
            }
            _isSyncing.value = false
        }
    }

    fun validateAndRestoreCloudBackup(password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            
            // Feature 3: Strictly verify password against Master Key before restoring any data
            val validation = masterKeyManager.downloadAndVerifyPassword(password)
            
            when (validation) {
                is io.vault.mobile.security.MasterKeyValidationResult.Success -> {
                    // Password matches the remote encrypted key! Proceed with data restore.
                    val success = backupManager.restoreBackup(password)
                    if (success) {
                        onSuccess()
                    } else {
                        _syncError.value = "Failed to restore data. Backup might be corrupted."
                    }
                }
                is io.vault.mobile.security.MasterKeyValidationResult.InvalidPassword -> {
                    _syncError.value = "Master key does not match. Restore denied."
                }
                is io.vault.mobile.security.MasterKeyValidationResult.NoRemoteKey -> {
                    _syncError.value = "No master key found on Google Drive."
                }
                else -> {
                    _syncError.value = "Verification failed. Check your connection."
                }
            }
            _isSyncing.value = false
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedEntry = MutableStateFlow<VaultEntry?>(null)
    val selectedEntry = _selectedEntry.asStateFlow()

    val autoBackupEnabled = preferenceManager.autoBackupEnabled
    val autofillEnabled = preferenceManager.autofillEnabled
    val biometricEnabled = preferenceManager.biometricEnabled

    fun isBiometricAvailable(): Boolean {
        return biometricAuthenticator.isBiometricAvailable()
    }

    val vaultEntries: StateFlow<List<VaultEntry>> = searchQuery
        .combine(repository.getAllEntries()) { query, entries ->
            if (query.isEmpty()) entries
            else entries.filter { it.appName.contains(query, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addEntry(appName: String, username: String, password: String, notes: String?, packageName: String? = null) {
        viewModelScope.launch {
            repository.addEntry(appName, username, password, notes, packageName)
        }
    }

    fun deleteEntry(entry: VaultEntry) {
        viewModelScope.launch {
            repository.deleteEntry(entry)
        }
    }

    fun updateEntry(entry: VaultEntry, newPassword: String?, newNotes: String?, newPackageName: String? = null) {
        viewModelScope.launch {
            repository.updateEntry(entry, newPassword, newNotes, newPackageName)
        }
    }

    fun selectEntry(entry: VaultEntry) {
        _selectedEntry.value = entry
    }

    fun clearSelection() {
        _selectedEntry.value = null
    }

    fun getDecryptedPassword(encrypted: ByteArray): String {
        return repository.decryptPassword(encrypted)
    }

    fun toggleAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setAutoBackupEnabled(enabled)
        }
    }

    fun toggleAutofill(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setAutofillEnabled(enabled)
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setBiometricEnabled(enabled)
        }
    }

    fun setOnboardingCompleted() {
        viewModelScope.launch {
            preferenceManager.setOnboardingCompleted(true)
        }
    }

    fun factoryReset(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            val success = masterKeyManager.performFactoryReset()
            _isSyncing.value = false
            if (success) {
                onComplete()
            } else {
                _syncError.value = "Local reset completed, but failed to wipe some data on Google Drive."
                onComplete()
            }
        }
    }

    fun calculatePasswordStrength(password: String): PasswordStrength {
        if (password.length < 8) return PasswordStrength.Poor
        
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        
        val score = listOf(hasUpper, hasLower, hasDigit, hasSpecial).count { it }
        
        return when {
            password.length >= 12 && score >= 3 -> PasswordStrength.Strong
            password.length >= 8 && score >= 2 -> PasswordStrength.Weak
            else -> PasswordStrength.Poor
        }
    }
}
