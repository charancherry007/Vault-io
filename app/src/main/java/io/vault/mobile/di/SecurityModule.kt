package io.vault.mobile.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.vault.mobile.security.BiometricAuthenticator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideBiometricAuthenticator(@ApplicationContext context: Context): BiometricAuthenticator {
        return BiometricAuthenticator(context)
    }
}
