package io.vault.mobile.ui.viewmodel

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.vault.mobile.security.EncryptionService
import io.vault.mobile.worker.BackupWorker
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import io.vault.mobile.data.cloud.GoogleDriveManager
import java.net.URLConnection
import io.vault.mobile.data.model.MediaManifest
import io.vault.mobile.data.model.MediaManifestEntry
import com.google.gson.Gson



@HiltViewModel
class MediaVaultViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionService: EncryptionService,
    private val driveManager: GoogleDriveManager
) : ViewModel() {

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _restoredItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val restoredItems = _restoredItems.asStateFlow()

    private val _mediaItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val mediaItems = _mediaItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp = _isBackingUp.asStateFlow()

    private val _backupProgress = MutableStateFlow(0f)
    val backupProgress = _backupProgress.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring = _isRestoring.asStateFlow()

    private val _restoreProgress = MutableStateFlow(0f)
    val restoreProgress = _restoreProgress.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds = _selectedIds.asStateFlow()

    private val _viewingMediaItem = MutableStateFlow<MediaItem?>(null)
    val viewingMediaItem = _viewingMediaItem.asStateFlow()

    private val gson = Gson()
    private val MANIFEST_FILE_NAME = "media_manifest.enc"

    init {
        attemptAutoConnect()
        loadMedia()
    }

    private fun attemptAutoConnect() {
        viewModelScope.launch {
            val success = driveManager.silentSignIn()
            if (success) {
                checkConnection()
            }
        }
    }

    fun checkConnection() {
        _isConnected.value = driveManager.isConnected()
    }

    fun onDriveConnected() {
        _isConnected.value = true
        loadMedia()
    }

    fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            val manifest = if (_isConnected.value) loadManifest() else null
            val items = fetchMedia(manifest)
            _mediaItems.value = items
            _isLoading.value = false
        }
    }

    private suspend fun fetchMedia(manifest: MediaManifest? = null): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val backedUpIds = manifest?.entries?.map { it.id }?.toSet() ?: emptySet()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED
        )

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeTypeColumn)
                val date = cursor.getLong(dateColumn)
                val contentUri = ContentUris.withAppendedId(
                    if (mimeType.startsWith("image")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                items.add(MediaItem(id, contentUri, name, size, mimeType, date, isBackedUp = backedUpIds.contains(id)))
            }
        }
        items
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value
        _selectedIds.value = if (current.contains(id)) {
            current - id
        } else {
            current + id
        }
    }

    fun setViewingItem(item: MediaItem?) {
        _viewingMediaItem.value = item
    }

    fun encryptAndBackupSelected() {
        val selectedItems = _mediaItems.value.filter { _selectedIds.value.contains(it.id) }
        val workManager = WorkManager.getInstance(context)
        val tag = "backup_session_${System.currentTimeMillis()}"

        _isBackingUp.value = true
        _backupProgress.value = 0f

        selectedItems.forEach { item ->
            val data = Data.Builder()
                .putString("uri", item.uri.toString())
                .putString("fileName", item.name)
                .putLong("id", item.id)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(data)
                .addTag(tag)
                .build()

            workManager.enqueue(workRequest)
        }
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow(tag).collect { workInfos ->
                val totalCount = selectedItems.size
                if (totalCount > 0) {
                    val finishedInfos = workInfos.filter { it.state.isFinished }
                    val successInfos = finishedInfos.filter { it.state == androidx.work.WorkInfo.State.SUCCEEDED }
                    
                    _backupProgress.value = finishedInfos.size.toFloat() / totalCount
                    
                    if (finishedInfos.size == totalCount) {
                        _isBackingUp.value = false
                        updateManifestWithNewUploads(selectedItems, successInfos)
                    }
                } else {
                    _isBackingUp.value = false
                }
            }
        }
        clearSelection()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    private suspend fun updateManifestWithNewUploads(
        selectedItems: List<MediaItem>,
        successInfos: List<androidx.work.WorkInfo>
    ) = withContext(Dispatchers.IO) {
        val currentManifest = loadManifest() ?: MediaManifest()
        val newEntries = currentManifest.entries.toMutableList()
        
        successInfos.forEach { info ->
            val driveId = info.outputData.getString("driveId") ?: return@forEach
            val itemId = info.outputData.getLong("id", -1L)
            
            val item = selectedItems.find { it.id == itemId }
            if (item != null) {
                newEntries.add(
                    MediaManifestEntry(
                        id = item.id,
                        fileName = item.name,
                        mimeType = item.mimeType,
                        size = item.size,
                        dateAdded = item.dateAdded,
                        blobDriveId = driveId
                    )
                )
            }
        }
        
        val updatedManifest = currentManifest.copy(
            lastUpdated = System.currentTimeMillis(),
            entries = newEntries
        )
        saveManifest(updatedManifest)

        // Update local items state for immediate UI feedback
        val successIds = successInfos.map { it.outputData.getLong("id", -1L) }.toSet()
        _mediaItems.value = _mediaItems.value.map { item ->
            if (successIds.contains(item.id)) item.copy(isBackedUp = true) else item
        }
    }

    private suspend fun loadManifest(): MediaManifest? = withContext(Dispatchers.IO) {
        if (!encryptionService.isKeyInitialized()) return@withContext null
        
        val driveFiles = driveManager.listFiles()
        val manifestFile = driveFiles.find { it.name == MANIFEST_FILE_NAME } ?: return@withContext null
        
        val tempEncFile = File(context.cacheDir, MANIFEST_FILE_NAME)
        val success = driveManager.downloadFile(manifestFile.id, tempEncFile)
        
        if (success) {
            val decryptedFile = File(context.cacheDir, "manifest_decrypted.json")
            try {
                val inputStream = FileInputStream(tempEncFile)
                val outputStream = FileOutputStream(decryptedFile)
                encryptionService.decrypt(inputStream, outputStream)
                inputStream.close()
                outputStream.close()
                
                val json = decryptedFile.readText()
                gson.fromJson(json, MediaManifest::class.java)
            } catch (e: javax.crypto.AEADBadTagException) {
                android.util.Log.e("MediaVault", "Decryption failed: Master Key mismatch or corrupted data.", e)
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                tempEncFile.delete()
                decryptedFile.delete()
            }
        } else {
            null
        }
    }

    private suspend fun saveManifest(manifest: MediaManifest) = withContext(Dispatchers.IO) {
        if (!encryptionService.isKeyInitialized()) return@withContext
        
        val json = gson.toJson(manifest)
        val tempJsonFile = File(context.cacheDir, "manifest_to_encrypt.json")
        tempJsonFile.writeText(json)
        
        val tempEncFile = File(context.cacheDir, MANIFEST_FILE_NAME)
        try {
            val inputStream = FileInputStream(tempJsonFile)
            val outputStream = FileOutputStream(tempEncFile)
            encryptionService.encrypt(inputStream, outputStream)
            inputStream.close()
            outputStream.close()
            
            // Check if manifest already exists on Drive to update it
            val driveFiles = driveManager.listFiles()
            val existingManifest = driveFiles.find { it.name == MANIFEST_FILE_NAME }
            
            if (existingManifest != null) {
                // Future improvement: Drive update call. For now we delete and re-upload if necessary
                // or just upload as it will create a new version/file.
                // GoogleDriveManager.uploadFile uses files().create().
            }
            driveManager.uploadFile(tempEncFile, "application/octet-stream")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tempJsonFile.delete()
            tempEncFile.delete()
        }
    }

    fun restoreFromDrive() {
        viewModelScope.launch {
            _isRestoring.value = true
            _restoreProgress.value = 0f
            _isLoading.value = true
            
            val manifest = loadManifest()
            if (manifest == null || manifest.entries.isEmpty()) {
                _isRestoring.value = false
                _isLoading.value = false
                return@launch
            }

            val totalFiles = manifest.entries.size
            val newRestoredItems = mutableListOf<MediaItem>()

            manifest.entries.forEachIndexed { index, entry ->
                val tempEncFile = File(context.cacheDir, entry.blobDriveId + ".enc")
                val success = driveManager.downloadFile(entry.blobDriveId, tempEncFile)
                
                if (success) {
                    val decryptedFile = File(context.filesDir, "restored_${entry.fileName}")
                    try {
                        val inputStream = FileInputStream(tempEncFile)
                        val outputStream = FileOutputStream(decryptedFile)
                        encryptionService.decrypt(inputStream, outputStream)
                        inputStream.close()
                        outputStream.close()

                        newRestoredItems.add(
                            MediaItem(
                                id = entry.id,
                                uri = Uri.fromFile(decryptedFile),
                                name = entry.fileName,
                                size = entry.size,
                                mimeType = entry.mimeType,
                                dateAdded = entry.dateAdded
                            )
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        tempEncFile.delete()
                    }
                }
                _restoreProgress.value = (index + 1).toFloat() / totalFiles
            }
            _restoredItems.value = newRestoredItems
            _isLoading.value = false
            _isRestoring.value = false
        }
    }

    fun saveToGallery(item: MediaItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                        put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val folder = if (item.mimeType.startsWith("image")) "Pictures/Vault" else "Movies/Vault"
                            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }

                    val collection = if (item.mimeType.startsWith("image")) {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }

                    val uri = context.contentResolver.insert(collection, contentValues)
                        ?: throw Exception("Failed to create MediaStore entry")

                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        context.contentResolver.openInputStream(item.uri)?.use { input ->
                            input.copyTo(output)
                        }
                    }

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        context.contentResolver.update(uri, contentValues, null, null)
                    }
                }
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun downloadSelectedFromRestored(onResult: (Int, Int) -> Unit) {
        val selectedItems = _restoredItems.value.filter { _selectedIds.value.contains(it.id) }
        if (selectedItems.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            selectedItems.forEach { item ->
                try {
                    withContext(Dispatchers.IO) {
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, item.name)
                            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                val folder = if (item.mimeType.startsWith("image")) "Pictures/Vault" else "Movies/Vault"
                                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                                put(MediaStore.MediaColumns.IS_PENDING, 1)
                            }
                        }

                        val collection = if (item.mimeType.startsWith("image")) {
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        } else {
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        }

                        val uri = context.contentResolver.insert(collection, contentValues)
                            ?: throw Exception("Failed to create MediaStore entry")

                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            context.contentResolver.openInputStream(item.uri)?.use { input ->
                                input.copyTo(output)
                            }
                        }

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            context.contentResolver.update(uri, contentValues, null, null)
                        }
                    }
                    successCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            onResult(successCount, selectedItems.size)
            clearSelection()
        }
    }

    fun deleteRestoredItems(onResult: (Int, Int) -> Unit) {
        val selectedIds = _selectedIds.value
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            val currentManifest = loadManifest() ?: MediaManifest()
            val entriesToDelete = currentManifest.entries.filter { selectedIds.contains(it.id) }
            
            var successCount = 0
            entriesToDelete.forEach { entry ->
                try {
                    // 1. Delete local file if it exists
                    val localFile = File(context.filesDir, "restored_${entry.fileName}")
                    if (localFile.exists()) localFile.delete()

                    // 2. Delete from Google Drive
                    val driveSuccess = driveManager.deleteFile(entry.blobDriveId)
                    if (driveSuccess) successCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 3. Update manifest and re-upload
            val updatedEntries = currentManifest.entries.filterNot { selectedIds.contains(it.id) }
            val updatedManifest = currentManifest.copy(
                lastUpdated = System.currentTimeMillis(),
                entries = updatedEntries
            )
            saveManifest(updatedManifest)

            // 4. Update UI states
            _restoredItems.value = _restoredItems.value.filterNot { selectedIds.contains(it.id) }
            _mediaItems.value = _mediaItems.value.map { item ->
                if (selectedIds.contains(item.id)) item.copy(isBackedUp = false) else item
            }

            _isLoading.value = false
            onResult(successCount, entriesToDelete.size)
            clearSelection()
        }
    }
}
