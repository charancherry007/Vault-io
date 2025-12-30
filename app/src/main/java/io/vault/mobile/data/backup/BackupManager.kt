package io.vault.mobile.data.backup

import android.util.Base64
import com.google.gson.Gson
import io.vault.mobile.data.local.entity.VaultEntry
import io.vault.mobile.data.remote.PinataApi
import io.vault.mobile.data.repository.VaultRepository
import io.vault.mobile.security.CryptoManager
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.vault.mobile.security.KeyDerivation
import io.vault.mobile.blockchain.RewardManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val repository: VaultRepository,
    private val pinataApi: PinataApi,
    private val rewardManager: RewardManager
) {
    private val gson = Gson()
    
    // In a real app, this would be securely stored or input by the user
    private val PINATA_JWT = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySW5mb3JtYXRpb24iOnsiaWQiOiI3MTE1OGJkYi0yNzE1LTQxZTQtOTg1OS1kNDkwNDNhNjk0NWMiLCJlbWFpbCI6ImNoYW5kdS5jaGFyYW4wMDdAZ21haWwuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsInBpbl9wb2xpY3kiOnsicmVnaW9ucyI6W3siZGVzaXJlZFJlcGxpY2F0aW9uQ291bnQiOjEsImlkIjoiRlJBMSJ9LHsiZGVzaXJlZFJlcGxpY2F0aW9uQ291bnQiOjEsImlkIjoiTllDMSJ9XSwidmVyc2lvbiI6MX0sIm1mYV9lbmFibGVkIjpmYWxzZSwic3RhdHVzIjoiQUNUSVZFIn0sImF1dGhlbnRpY2F0aW9uVHlwZSI6InNjb3BlZEtleSIsInNjb3BlZEtleUtleSI6ImM2NWZiYzE3NmQ0MDQ4N2ZlMTBmIiwic2NvcGVkS2V5U2VjcmV0IjoiMDQ3MzI0ZjAyZWYyY2I4ZjE5NTVjYTM0NGUzMWYzYjJiM2ZhZTY5NmRhYzNmODBhZTk3ODIxZTYzYmVjNmZkNCIsImV4cCI6MTc5ODA1MjAwOX0.jFUuqi6hLghAh8QPP6AqdFrWFTZFdVyvScI5c7RHshk"

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

    suspend fun createBackup(password: String): String? = withContext(Dispatchers.IO) {
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
            
            // 1. Generate Salt
            val salt = KeyDerivation.generateSalt()
            // 2. Derive Key from Master Password
            val key = KeyDerivation.deriveKey(password, salt)
            // 3. Encrypt JSON with Derived Key
            val encryptedBackup = CryptoManager.encryptWithKey(json.encodeToByteArray(), key)
            
            // 4. Final Blob: Salt (16) + Transformed Data (IV 12 + Data)
            val finalData = salt + encryptedBackup
            
            val requestBody = finalData.toRequestBody("application/octet-stream".toMediaTypeOrNull())
            val multipart = MultipartBody.Part.createFormData("file", "vault_backup.bin", requestBody)
            
            val response = pinataApi.uploadFile(PINATA_JWT, multipart)
            if (response.isSuccessful) {
                val hash = response.body()?.IpfsHash
                if (hash != null) {
                    // Trigger reward for successful backup
                    rewardManager.claimReward(RewardManager.Action.BackupComplete, hash) { _, _ -> }
                }
                hash
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun restoreBackup(cid: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val gatewayUrl = "https://gateway.pinata.cloud/ipfs/$cid"
        
        try {
            val response = okhttp3.OkHttpClient().newCall(
                okhttp3.Request.Builder().url(gatewayUrl).build()
            ).execute()
            
            if (response.isSuccessful) {
                val fullData = response.body?.bytes() ?: return@withContext false
                
                // 1. Extract Salt (first 16 bytes)
                val salt = fullData.sliceArray(0 until 16)
                // 2. Extract Encrypted Payload
                val encryptedData = fullData.sliceArray(16 until fullData.size)
                
                // 3. Derive same Key
                val key = KeyDerivation.deriveKey(password, salt)
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
