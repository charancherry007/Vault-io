package io.vault.mobile.security

import android.content.Context
import com.google.api.services.drive.DriveScopes
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import io.vault.mobile.data.cloud.GoogleDriveManager
import io.vault.mobile.data.local.AppDatabase
import io.vault.mobile.data.local.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasterKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveManager: GoogleDriveManager,
    private val database: AppDatabase,
    private val preferenceManager: PreferenceManager
) {
    private val MASTER_KEY_FILE = "master.key.enc"
    
    /**
     * Checks if the master key exists locally in Android Keystore.
     */
    fun hasLocalMasterKey(): Boolean {
        return CryptoManager.hasMasterKey()
    }

    /**
     * Checks if a master key backup exists on Google Drive.
     */
    suspend fun hasRemoteMasterKey(): Boolean = withContext(Dispatchers.IO) {
        if (!driveManager.isConnected()) return@withContext false
        val files = driveManager.listFiles()
        files.any { it.name == MASTER_KEY_FILE }
    }

    /**
     * Creates a new master key, stores it in Keystore, and uploads an encrypted backup to Drive.
     */
    suspend fun createAndSyncMasterKey(password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Generate 32-byte random key
            val random = SecureRandom()
            val masterKeyBytes = ByteArray(32)
            random.nextBytes(masterKeyBytes)

            // 2. Derive key from password to encrypt the backup
            val salt = KeyDerivation.generateSalt()
            val encryptionKey = KeyDerivation.deriveKey(password, salt)
            
            // 3. Encrypt the master key bytes
            val encryptedBackup = CryptoManager.encryptWithKey(masterKeyBytes, encryptionKey)
            val finalBackupData = salt + encryptedBackup

            // 4. Save to local temp file and upload to Drive
            val tempFile = File(context.cacheDir, MASTER_KEY_FILE)
            tempFile.writeBytes(finalBackupData)
            val driveId = driveManager.uploadFile(tempFile, "application/octet-stream")
            tempFile.delete()

            if (driveId != null) {
                // 5. Import into Keystore only if Drive upload succeeded
                CryptoManager.importMasterKey(masterKeyBytes)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Downloads the master key from Drive, decrypts it with password, and imports to Keystore.
     */
    suspend fun restoreMasterKeyFromDrive(password: String): Boolean = withContext(Dispatchers.IO) {
        val result = downloadAndVerifyPassword(password)
        if (result is MasterKeyValidationResult.Success) {
            CryptoManager.importMasterKey(result.masterKeyBytes)
            true
        } else {
            false
        }
    }

    /**
     * Verifies if the provided password can decrypt the remote master key backup.
     */
    suspend fun downloadAndVerifyPassword(password: String): MasterKeyValidationResult = withContext(Dispatchers.IO) {
        try {
            if (!driveManager.isConnected()) return@withContext MasterKeyValidationResult.NoConnection
            
            val files = driveManager.listFiles()
            val backupFile = files.find { it.name == MASTER_KEY_FILE } 
                ?: return@withContext MasterKeyValidationResult.NoRemoteKey

            val tempFile = File(context.cacheDir, MASTER_KEY_FILE)
            val downloadSuccess = driveManager.downloadFile(backupFile.id, tempFile)

            if (downloadSuccess) {
                val fullData = tempFile.readBytes()
                tempFile.delete()

                if (fullData.size < 16) return@withContext MasterKeyValidationResult.CorruptedKey

                val salt = fullData.sliceArray(0 until 16)
                val encryptedData = fullData.sliceArray(16 until fullData.size)

                val encryptionKey = KeyDerivation.deriveKey(password, salt)
                return@withContext try {
                    val masterKeyBytes = CryptoManager.decryptWithKey(encryptedData, encryptionKey)
                    MasterKeyValidationResult.Success(masterKeyBytes)
                } catch (e: Exception) {
                    MasterKeyValidationResult.InvalidPassword
                }
            } else {
                MasterKeyValidationResult.DownloadFailed
            }
        } catch (e: Exception) {
            e.printStackTrace()
            MasterKeyValidationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Feature 4: Performs a destructive wipe of all local and remote app data.
     */
    suspend fun performFactoryReset(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Nuke Google Drive data
            val driveWiped = if (driveManager.isConnected()) {
                driveManager.nukeAllAppData()
            } else {
                true // If not connected, we skip but proceed with local wipe
            }

            // 2. Wipe Local Database
            database.clearAllTables()

            // 3. Wipe Local Encrypted Files (Media)
            val encryptedDir = File(context.filesDir, "encrypted_media")
            if (encryptedDir.exists()) {
                encryptedDir.deleteRecursively()
            }

            // 4. Reset Preferences
            preferenceManager.setOnboardingCompleted(false)
            preferenceManager.setBiometricEnabled(false)
            preferenceManager.setAutoBackupEnabled(false)
            preferenceManager.setLastRestoreTime(0)

            // 5. Remove Master Key from Keystore
            CryptoManager.deleteMasterKey()

            driveWiped
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

sealed class MasterKeyValidationResult {
    data class Success(val masterKeyBytes: ByteArray) : MasterKeyValidationResult()
    object InvalidPassword : MasterKeyValidationResult()
    object NoRemoteKey : MasterKeyValidationResult()
    object NoConnection : MasterKeyValidationResult()
    object DownloadFailed : MasterKeyValidationResult()
    object CorruptedKey : MasterKeyValidationResult()
    data class Error(val message: String) : MasterKeyValidationResult()
}
