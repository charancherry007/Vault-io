package io.vault.mobile.data.repository

import io.vault.mobile.data.local.dao.VaultDao
import io.vault.mobile.data.local.entity.VaultEntry
import io.vault.mobile.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepository @Inject constructor(
    private val vaultDao: VaultDao
) {

    fun getAllEntries(): Flow<List<VaultEntry>> = vaultDao.getAllEntries()

    fun searchEntries(query: String): Flow<List<VaultEntry>> = vaultDao.searchEntries(query)

    suspend fun addEntry(appName: String, username: String, password: String, notes: String?, packageName: String? = null) {
        val encryptedPassword = CryptoManager.encryptString(password)
        val entry = VaultEntry(
            appName = appName,
            packageName = packageName,
            username = username,
            encryptedPassword = encryptedPassword,
            notes = notes
        )
        vaultDao.insertEntry(entry)
    }

    suspend fun deleteEntry(entry: VaultEntry) {
        vaultDao.deleteEntry(entry)
    }

    suspend fun updateEntry(entry: VaultEntry, newPassword: String?, newNotes: String?, newPackageName: String? = null) {
        val updatedEntry = entry.copy(
            encryptedPassword = newPassword?.let { CryptoManager.encryptString(it) } ?: entry.encryptedPassword,
            notes = newNotes ?: entry.notes,
            packageName = newPackageName ?: entry.packageName
        )
        vaultDao.updateEntry(updatedEntry)
    }

    suspend fun getEntryByPackageName(packageName: String): VaultEntry? {
        return vaultDao.getEntryByPackageName(packageName)
    }

    fun decryptPassword(encryptedPassword: ByteArray): String {
        return CryptoManager.decryptToString(encryptedPassword)
    }
}
