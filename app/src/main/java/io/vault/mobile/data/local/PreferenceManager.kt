package io.vault.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val AUTO_BACKUP_KEY = booleanPreferencesKey("auto_backup")
    private val AUTOFILL_ENABLED_KEY = booleanPreferencesKey("autofill_enabled")
    private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
    private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")

    val autoBackupEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_BACKUP_KEY] ?: false
    }

    val autofillEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTOFILL_ENABLED_KEY] ?: false
    }

    val biometricEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BIOMETRIC_ENABLED_KEY] ?: false
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_BACKUP_KEY] = enabled
        }
    }

    suspend fun setAutofillEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTOFILL_ENABLED_KEY] = enabled
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }
}
