package io.vault.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.vault.mobile.data.local.entity.VaultEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_entries ORDER BY appName ASC")
    fun getAllEntries(): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries WHERE appName LIKE '%' || :query || '%'")
    fun searchEntries(query: String): Flow<List<VaultEntry>>

    @Query("SELECT * FROM vault_entries WHERE packageName = :packageName LIMIT 1")
    suspend fun getEntryByPackageName(packageName: String): VaultEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: VaultEntry)

    @Update
    suspend fun updateEntry(entry: VaultEntry)

    @Delete
    suspend fun deleteEntry(entry: VaultEntry)

    @Query("DELETE FROM vault_entries")
    suspend fun clearVault()
}
