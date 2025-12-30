package io.vault.mobile.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.vault.mobile.data.local.PreferenceManager
import io.vault.mobile.data.local.entity.VaultEntry
import io.vault.mobile.data.repository.VaultRepository
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
    private val backupManager: io.vault.mobile.data.backup.BackupManager,
    private val biometricAuthenticator: io.vault.mobile.security.BiometricAuthenticator,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

    init {
        fetchInstalledApps()
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

    private val _backupCid = MutableStateFlow<String?>(null)
    val backupCid = _backupCid.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError = _syncError.asStateFlow()

    fun syncToCloud(password: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            val cid = backupManager.createBackup(password)
            if (cid != null) {
                _backupCid.value = cid
            } else {
                _syncError.value = "Failed to sync to IPFS. Check connection."
            }
            _isSyncing.value = false
        }
    }

    fun restoreFromCloud(cid: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            val success = backupManager.restoreBackup(cid, password)
            if (success) {
                onSuccess()
            } else {
                _syncError.value = "Failed to restore. Invalid CID/Password or network error."
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
