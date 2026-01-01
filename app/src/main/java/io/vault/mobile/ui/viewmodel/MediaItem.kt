package io.vault.mobile.ui.viewmodel

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val dateAdded: Long,
    val isBackedUp: Boolean = false
)
