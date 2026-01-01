package io.vault.mobile.di

import android.content.Context
import androidx.room.Room
import io.vault.mobile.data.local.AppDatabase
import io.vault.mobile.data.local.dao.VaultDao

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        // In a real app, the passphrase would be derived from the master password and stored in memory/Keystore.
        // For project initialization, we use a placeholder or handle it in a more complex Way.
        // Here we'll use a constant for now, but plan to inject the key dynamically.
        val factory = SupportOpenHelperFactory("vault_temporary_key".toByteArray())
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "vault.db"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideVaultDao(database: AppDatabase): VaultDao {
        return database.vaultDao()
    }


}
