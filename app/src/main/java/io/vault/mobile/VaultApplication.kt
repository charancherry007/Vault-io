package io.vault.mobile

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize SQLCipher native libraries
        System.loadLibrary("sqlcipher")
    }
}
