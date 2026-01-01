package io.vault.mobile.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.vault.mobile.security.MasterKeyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MasterKeyViewModel @Inject constructor(
    private val masterKeyManager: MasterKeyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<MasterKeyUiState>(MasterKeyUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        checkStatus()
    }

    fun checkStatus() {
        viewModelScope.launch {
            _uiState.value = MasterKeyUiState.Loading
            val hasLocal = masterKeyManager.hasLocalMasterKey()
            val hasRemote = masterKeyManager.hasRemoteMasterKey()

            when {
                hasLocal -> {
                    // This actually shouldn't be reachable if the gate works correctly, 
                    // but we handle it just in case.
                    _uiState.value = MasterKeyUiState.AlreadyInitialized
                }
                hasRemote -> {
                    _uiState.value = MasterKeyUiState.NeedsRestore
                }
                else -> {
                    _uiState.value = MasterKeyUiState.NeedsCreation
                }
            }
        }
    }

    fun createMasterKey(password: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            
            // Feature 3: Safety check - don't overwrite existing remote key during "Create" flow
            if (masterKeyManager.hasRemoteMasterKey()) {
                _error.value = "A master key already exists on Drive. Please restore it instead."
                _isProcessing.value = false
                return@launch
            }

            val success = masterKeyManager.createAndSyncMasterKey(password)
            _isProcessing.value = false
            if (success) {
                onComplete()
            } else {
                _error.value = "Failed to create and sync master key. Please check your connection."
            }
        }
    }

    fun restoreMasterKey(password: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            val success = masterKeyManager.restoreMasterKeyFromDrive(password)
            _isProcessing.value = false
            if (success) {
                onComplete()
            } else {
                _error.value = "Invalid password or restoration failed."
            }
        }
    }

    fun factoryReset(onComplete: () -> Unit) {
        viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            val success = masterKeyManager.performFactoryReset()
            _isProcessing.value = false
            if (success) {
                onComplete()
            } else {
                _error.value = "Local reset completed, but failed to wipe some data on Google Drive."
                // Even if partial failure on Drive, we still proceed as local data is gone
                onComplete() 
            }
        }
    }
}

sealed class MasterKeyUiState {
    object Loading : MasterKeyUiState()
    object NeedsCreation : MasterKeyUiState()
    object NeedsRestore : MasterKeyUiState()
    object AlreadyInitialized : MasterKeyUiState()
}
