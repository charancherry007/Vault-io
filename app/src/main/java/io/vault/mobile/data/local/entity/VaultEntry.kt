package io.vault.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_entries")
data class VaultEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appName: String,
    val packageName: String? = null, // For Autofill matching
    val username: String,
    val encryptedPassword: ByteArray, // Encrypted with AES
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
