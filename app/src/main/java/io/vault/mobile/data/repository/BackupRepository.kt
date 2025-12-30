package io.vault.mobile.data.repository

import android.content.Context
import io.vault.mobile.data.remote.PinataApi
import io.vault.mobile.security.CryptoManager
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    private val pinataApi: PinataApi,
    @ApplicationContext private val context: Context
) {

    suspend fun backupVault(dbFile: File, apiKey: String): String? {
        // Read DB file
        val bytes = dbFile.readBytes()
        
        // Encrypt the entire DB blob
        val encryptedBytes = CryptoManager.encrypt(bytes)
        
        // Save encrypted blob to a temporary file
        val tempFile = File(context.cacheDir, "vault_backup.enc")
        tempFile.writeBytes(encryptedBytes)
        
        val requestFile = tempFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        val body = MultipartBody.Part.createFormData("file", tempFile.name, requestFile)
        
        val response = pinataApi.uploadFile("Bearer $apiKey", body)
        
        return if (response.isSuccessful) {
            response.body()?.IpfsHash
        } else {
            null
        }
    }

    suspend fun restoreVault(ipfsHash: String, targetFile: File) {
        // Real implementation would download via IPFS gateway or Pinata API
        // and decrypt using CryptoManager.decrypt(downloadedBytes)
    }
}
