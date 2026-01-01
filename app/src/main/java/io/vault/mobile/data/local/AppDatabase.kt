package io.vault.mobile.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import io.vault.mobile.data.local.dao.VaultDao

import io.vault.mobile.data.local.entity.VaultEntry


@Database(entities = [VaultEntry::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
