package io.vault.mobile.data.backup

import io.vault.mobile.data.local.PreferenceManager
import android.util.Base64
import com.google.gson.Gson
import io.vault.mobile.data.cloud.GoogleDriveManager
import io.vault.mobile.data.repository.VaultRepository
import io.vault.mobile.security.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import io.vault.mobile.data.model.MediaManifest
import io.vault.mobile.data.model.MediaManifestEntry
import android.net.Uri
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val repository: VaultRepository,
    private val driveManager: GoogleDriveManager,
    private val encryptionService: EncryptionService,
    private val masterKeyManager: MasterKeyManager,
    private val preferenceManager: PreferenceManager
) {
    private val gson = Gson()
    private val BACKUP_FILE_NAME = "vault_password_backup.bin"
    private val BACKUP_VERSION: Byte = 0x02
    private val MANIFEST_FILE_NAME = "media_manifest.enc"

    data class VaultBackup(
        val entries: List<VaultEntryDto>,
        val version: Int = 1,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class VaultEntryDto(
        val appName: String,
        val packageName: String?,
        val username: String,
        val encryptedPasswordBase64: String,
        val notes: String?
    )

    suspend fun createBackup(password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val entries = repository.getAllEntries().first()
            val dtos = entries.map {
                VaultEntryDto(
                    it.appName,
                    it.packageName,
                    it.username,
                    Base64.encodeToString(it.encryptedPassword, Base64.DEFAULT),
                    it.notes
                )
            }
            
            val backup = VaultBackup(dtos)
            val json = gson.toJson(backup)
            
            val salt = KeyDerivation.generateSalt()
            val key = KeyDerivation.deriveKey(password, salt, KeyDerivation.ITERATIONS_V2)
            val encryptedBackup = CryptoManager.encryptWithKey(json.encodeToByteArray(), key)
            
            val finalData = byteArrayOf(BACKUP_VERSION) + salt + encryptedBackup
            
            val tempFile = File(driveManager.getContext().cacheDir, BACKUP_FILE_NAME)
            tempFile.writeBytes(finalData)
            
            val driveId = driveManager.uploadFile(tempFile, "application/octet-stream")
            tempFile.delete()
            
            driveId != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun restoreAll(password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Restore Master Key (Keystone) first
            val mkRestored = masterKeyManager.restoreMasterKeyFromDrive(password)
            if (!mkRestored) return@withContext false

            // 2. Restore Passwords
            val passwordsRestored = restorePasswords(password)
            
            // 3. Restore Media
            val mediaRestored = restoreAllMedia()

            if (passwordsRestored && mediaRestored) {
                // Feature 3: Set restore metadata once successful
                preferenceManager.setLastRestoreTime(System.currentTimeMillis())
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun restorePasswords(password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val driveFiles = driveManager.listFiles()
            val backupFile = driveFiles.find { it.name == BACKUP_FILE_NAME } ?: return@withContext true // Skip if none
            
            val tempFile = File(driveManager.getContext().cacheDir, BACKUP_FILE_NAME)
            val downloadSuccess = driveManager.downloadFile(backupFile.id, tempFile)
            
            if (downloadSuccess) {
                val fullData = tempFile.readBytes()
                tempFile.delete()
                
                if (fullData.isEmpty()) return@withContext false

                val (salt, encryptedData, iterations) = if (fullData[0] == BACKUP_VERSION) {
                    // Version 2: [VERSION (1)] [SALT (16)] [DATA]
                    if (fullData.size < 18) return@withContext false
                    Triple(
                        fullData.sliceArray(1 until 17),
                        fullData.sliceArray(17 until fullData.size),
                        KeyDerivation.ITERATIONS_V2
                    )
                } else {
                    // Version 1 (Legacy): [SALT (16)] [DATA]
                    if (fullData.size < 16) return@withContext false
                    Triple(
                        fullData.sliceArray(0 until 16),
                        fullData.sliceArray(16 until fullData.size),
                        KeyDerivation.ITERATIONS_V1
                    )
                }
                
                // 3. Derive Key with correct iterations
                val key = KeyDerivation.deriveKey(password, salt, iterations)
                val decryptedJsonBytes = CryptoManager.decryptWithKey(encryptedData, key)
                val decryptedJson = decryptedJsonBytes.decodeToString()
                
                val backup = gson.fromJson(decryptedJson, VaultBackup::class.java)
                
                backup.entries.forEach { dto ->
                    val rawPassword = CryptoManager.decryptToString(Base64.decode(dto.encryptedPasswordBase64, Base64.DEFAULT))
                    repository.addEntry(
                        dto.appName,
                        dto.username,
                        rawPassword,
                        dto.notes,
                        dto.packageName
                    )
                }
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun restoreAllMedia(): Boolean = withContext(Dispatchers.IO) {
        try {
            val manifest = loadManifest() ?: return@withContext true // No manifest is fine

            manifest.entries.forEach { entry ->
                val decryptedFile = File(driveManager.getContext().filesDir, "restored_${entry.fileName}")
                
                // Feature 3: Check if file already exists locally
                if (decryptedFile.exists()) {
                    return@forEach // Skip if already restored
                }

                val tempEncFile = File(driveManager.getContext().cacheDir, entry.blobDriveId + ".enc")
                val success = driveManager.downloadFile(entry.blobDriveId, tempEncFile)
                
                if (success) {
                    try {
                        val inputStream = FileInputStream(tempEncFile)
                        val outputStream = FileOutputStream(decryptedFile)
                        encryptionService.decrypt(inputStream, outputStream)
                        inputStream.close()
                        outputStream.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        tempEncFile.delete()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun loadManifest(): MediaManifest? = withContext(Dispatchers.IO) {
        if (!encryptionService.isKeyInitialized()) return@withContext null
        
        val driveFiles = driveManager.listFiles()
        val manifestFile = driveFiles.find { it.name == MANIFEST_FILE_NAME } ?: return@withContext null
        
        val tempEncFile = File(driveManager.getContext().cacheDir, MANIFEST_FILE_NAME)
        val success = driveManager.downloadFile(manifestFile.id, tempEncFile)
        
        if (success) {
            val decryptedFile = File(driveManager.getContext().cacheDir, "manifest_decrypted.json")
            try {
                val inputStream = FileInputStream(tempEncFile)
                val outputStream = FileOutputStream(decryptedFile)
                encryptionService.decrypt(inputStream, outputStream)
                inputStream.close()
                outputStream.close()
                
                val json = decryptedFile.readText()
                gson.fromJson(json, MediaManifest::class.java)
            } finally {
                tempEncFile.delete()
                decryptedFile.delete()
            }
        } else null
    }

    suspend fun saveManifest(manifest: MediaManifest) = withContext(Dispatchers.IO) {
        if (!encryptionService.isKeyInitialized()) return@withContext
        
        val json = gson.toJson(manifest)
        val tempJsonFile = File(driveManager.getContext().cacheDir, "manifest_to_encrypt.json")
        tempJsonFile.writeText(json)
        
        val tempEncFile = File(driveManager.getContext().cacheDir, MANIFEST_FILE_NAME)
        try {
            val inputStream = FileInputStream(tempJsonFile)
            val outputStream = FileOutputStream(tempEncFile)
            encryptionService.encrypt(inputStream, outputStream, tempJsonFile.length())
            inputStream.close()
            outputStream.close()
            
            driveManager.uploadFile(tempEncFile, "application/octet-stream")
        } finally {
            tempJsonFile.delete()
            tempEncFile.delete()
        }
    }

    // Deprecated in favor of restoreAll, but kept for partial compatibility
    suspend fun restoreBackup(password: String): Boolean = restorePasswords(password)
}
