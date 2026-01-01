package io.vault.mobile.data.cloud

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import com.google.api.services.drive.model.File as DriveFile

@Singleton
class GoogleDriveManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val scopes = listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA)

    fun getContext() = context

    fun isConnected(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    suspend fun silentSignIn(): Boolean = withContext(Dispatchers.IO) {
        try {
            val signInOptions = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE), com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_APPDATA))
                .build()
            val client = GoogleSignIn.getClient(context, signInOptions)
            val task = client.silentSignIn()
            // We can't easily wait for task completion in a suspend function without a wrapper
            // but for simplicity we can check if it's already successful or just rely on getLastSignedInAccount
            // Actually, let's just use getLastSignedInAccount since silentSignIn is mostly for refreshing tokens.
            isConnected()
        } catch (e: Exception) {
            false
        }
    }

    private fun getDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val credential = GoogleAccountCredential.usingOAuth2(context, scopes)
        credential.selectedAccount = account.account
        
        return Drive.Builder(
            com.google.api.client.extensions.android.http.AndroidHttp.newCompatibleTransport(),
            jsonFactory,
            credential
        )
        .setApplicationName("Vault-io")
        .build()
    }

    suspend fun uploadFile(file: File, mimeType: String): String? = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext null
        
        val fileMetadata = DriveFile().apply {
            name = file.name
            parents = listOf("appDataFolder") // Or a specific folder
        }
        
        val mediaContent = FileContent(mimeType, file)
        
        try {
            val driveFile = service.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
            driveFile.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun listFiles(): List<DriveFile> = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext emptyList()
        
        try {
            val result = service.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name, size, createdTime)")
                .execute()
            result.files ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun downloadFile(fileId: String, targetFile: File): Boolean = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext false
        try {
            val outputStream = FileOutputStream(targetFile)
            service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteFile(fileId: String): Boolean = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext false
        try {
            service.files().delete(fileId).execute()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun nukeAllAppData(): Boolean = withContext(Dispatchers.IO) {
        val service = getDriveService() ?: return@withContext false
        try {
            val files = listFiles()
            var allDeleted = true
            for (file in files) {
                if (!deleteFile(file.id)) {
                    allDeleted = false
                }
            }
            allDeleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
