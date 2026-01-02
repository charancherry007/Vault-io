package io.vault.mobile.data.backup

import android.util.Base64
import com.google.gson.Gson
import io.vault.mobile.data.cloud.GoogleDriveManager
import io.vault.mobile.data.repository.VaultRepository
import io.vault.mobile.security.CryptoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.vault.mobile.security.KeyDerivation
import java.io.File

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val repository: VaultRepository,
    private val driveManager: GoogleDriveManager
) {
    private val gson = Gson()
    private val BACKUP_FILE_NAME = "vault_password_backup.bin"
    private val BACKUP_VERSION: Byte = 0x02

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

    suspend fun restoreBackup(password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val driveFiles = driveManager.listFiles()
            val backupFile = driveFiles.find { it.name == BACKUP_FILE_NAME } ?: return@withContext false
            
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
                // 4. Decrypt
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
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
