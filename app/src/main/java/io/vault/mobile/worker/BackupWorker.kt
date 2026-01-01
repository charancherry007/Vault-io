package io.vault.mobile.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.vault.mobile.data.cloud.GoogleDriveManager
import io.vault.mobile.security.EncryptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val encryptionService: EncryptionService,
    private val driveManager: GoogleDriveManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriString = inputData.getString("uri") ?: return@withContext Result.failure()
        val itemId = inputData.getLong("id", -1L)
        val uri = Uri.parse(uriString)
        val fileName = inputData.getString("fileName") ?: "encrypted_file"
        
        try {
            val tempFile = File(applicationContext.cacheDir, "$fileName.enc")
            val inputStream = applicationContext.contentResolver.openInputStream(uri) ?: return@withContext Result.failure()
            val outputStream = FileOutputStream(tempFile)
            
            encryptionService.encrypt(inputStream, outputStream)
            
            inputStream.close()
            outputStream.close()
            
            val driveId = driveManager.uploadFile(tempFile, "application/octet-stream")
            
            tempFile.delete()
            
            if (driveId != null) {
                val output = androidx.work.Data.Builder()
                    .putString("driveId", driveId)
                    .putLong("id", itemId)
                    .build()
                Result.success(output)
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
