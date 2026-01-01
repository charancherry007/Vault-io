package io.vault.mobile.data.model

data class MediaManifest(
    val version: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis(),
    val entries: List<MediaManifestEntry> = emptyList()
)

data class MediaManifestEntry(
    val id: Long,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val dateAdded: Long,
    val blobDriveId: String
)
