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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MasterKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveManager: GoogleDriveManager,
    private val database: AppDatabase,
    private val preferenceManager: PreferenceManager,
    private val encryptionService: EncryptionService
) {
    private val MASTER_KEY_FILE = "master.key.enc"
    private val BACKUP_VERSION: Byte = 0x02
    
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

            // 2. Derive key from password to encrypt the backup (using new NIST 2024 iteration count)
            val salt = KeyDerivation.generateSalt()
            val encryptionKey = KeyDerivation.deriveKey(password, salt, KeyDerivation.ITERATIONS_V2)
            
            // 3. Encrypt the master key bytes
            val encryptedBackup = CryptoManager.encryptWithKey(masterKeyBytes, encryptionKey)
            val finalBackupData = byteArrayOf(BACKUP_VERSION) + salt + encryptedBackup

            // 4. Save to local temp file and upload to Drive (Update if exists)
            val tempFile = File(context.cacheDir, MASTER_KEY_FILE)
            tempFile.writeBytes(finalBackupData)
            val existingFile = driveManager.findFileByName(MASTER_KEY_FILE)
            val driveId = driveManager.uploadFile(tempFile, "application/octet-stream", existingFile?.id)
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

                if (fullData.isEmpty()) return@withContext MasterKeyValidationResult.CorruptedKey

                val (salt, encryptedData, iterations) = if (fullData[0] == BACKUP_VERSION) {
                    // Version 2: [VERSION (1)] [SALT (16)] [DATA]
                    if (fullData.size < 18) return@withContext MasterKeyValidationResult.CorruptedKey // 1+16+min data
                    Triple(
                        fullData.sliceArray(1 until 17),
                        fullData.sliceArray(17 until fullData.size),
                        KeyDerivation.ITERATIONS_V2
                    )
                } else {
                    // Version 1 (Legacy): [SALT (16)] [DATA]
                    if (fullData.size < 16) return@withContext MasterKeyValidationResult.CorruptedKey
                    Triple(
                        fullData.sliceArray(0 until 16),
                        fullData.sliceArray(16 until fullData.size),
                        KeyDerivation.ITERATIONS_V1
                    )
                }

                val encryptionKey = KeyDerivation.deriveKey(password, salt, iterations)
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

            // 4. Wipe Cache
            context.cacheDir.deleteRecursively()

            // 5. Reset Preferences
            preferenceManager.clearAll()

            // 6. Remove Master Key from Keystore
            CryptoManager.deleteMasterKey()

            driveWiped
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Changes the vault password and optionally rotates the underlying master key.
     * Rotation re-encrypts all local data with a new random master key.
     */
    suspend fun changePasswordAndRotate(
        oldPassword: String,
        newPassword: String,
        rotateMasterKey: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = downloadAndVerifyPassword(oldPassword)
            if (result !is MasterKeyValidationResult.Success) return@withContext false
            val currentMasterKeyData = result.masterKeyBytes

            val finalMasterKeyData = if (rotateMasterKey) {
                // Generate a fresh master key
                val newKey = ByteArray(32)
                SecureRandom().nextBytes(newKey)
                
                // 1. Migrate Database entries
                migrateDatabase(currentMasterKeyData, newKey)
                
                // 2. Migrate Media files
                migrateMedia(currentMasterKeyData, newKey)
                
                newKey
            } else {
                currentMasterKeyData
            }

            // 2. Update backup on Google Drive with NEW password and V2 iterations
            val success = updateRemoteBackup(newPassword, finalMasterKeyData)
            if (success) {
                // 3. Update local Keystore
                CryptoManager.importMasterKey(finalMasterKeyData)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun updateRemoteBackup(password: String, masterKeyBytes: ByteArray): Boolean {
        val salt = KeyDerivation.generateSalt()
        val encryptionKey = KeyDerivation.deriveKey(password, salt, KeyDerivation.ITERATIONS_V2)
        val encryptedBackup = CryptoManager.encryptWithKey(masterKeyBytes, encryptionKey)
        val finalBackupData = byteArrayOf(BACKUP_VERSION) + salt + encryptedBackup

        val tempFile = File(context.cacheDir, MASTER_KEY_FILE)
        tempFile.writeBytes(finalBackupData)
        val existingFile = driveManager.findFileByName(MASTER_KEY_FILE)
        val driveId = driveManager.uploadFile(tempFile, "application/octet-stream", existingFile?.id)
        tempFile.delete()
        return driveId != null
    }

    private suspend fun migrateMedia(oldKey: ByteArray, newKey: ByteArray) {
        val oldSecretKey = SecretKeySpec(oldKey, "AES")
        val newSecretKey = SecretKeySpec(newKey, "AES")

        // 1. Migrate Local Restored Files (if any)
        val filesDir = context.filesDir
        filesDir.listFiles()?.filter { it.name.startsWith("restored_") }?.forEach { file ->
            try {
                val tempFile = File(context.cacheDir, "migrate_${file.name}")
                
                // Decrypt with old key
                val inputStream = FileInputStream(file)
                val outputStream = FileOutputStream(tempFile)
                encryptionService.decrypt(inputStream, outputStream, oldSecretKey)
                inputStream.close()
                outputStream.close()
                
                // Encrypt with new key (preserving original size if possible, or just using file length if same)
                // Note: EncryptionService with padding handles size headers
                val inputStream2 = FileInputStream(tempFile)
                val outputStream2 = FileOutputStream(file)
                // We use tempFile.length() as an approximation or we'd need to extract original size from header
                // Since decrypt returns original size to outputStream, tempFile EXACTLY matches original data.
                encryptionService.encrypt(inputStream2, outputStream2, tempFile.length(), newSecretKey)
                inputStream2.close()
                outputStream2.close()
                
                tempFile.delete()
            } catch (e: Exception) {
                android.util.Log.e("MasterKeyManager", "Failed to migrate media file ${file.name}: ${e.message}")
            }
        }
    }

    private suspend fun migrateDatabase(oldKey: ByteArray, newKey: ByteArray) {
        val dao = database.vaultDao()
        val entries = dao.getAllEntriesOneShot()
        entries.forEach { entry ->
            try {
                val decryptedPassword = CryptoManager.decryptWithKey(entry.encryptedPassword, oldKey).decodeToString()
                val reEncryptedPassword = CryptoManager.encryptWithKey(decryptedPassword.encodeToByteArray(), newKey)
                dao.updateEntry(entry.copy(encryptedPassword = reEncryptedPassword))
            } catch (e: Exception) {
                android.util.Log.e("MasterKeyManager", "Failed to migrate entry ${entry.id}: ${e.message}")
            }
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
