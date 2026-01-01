package io.vault.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*

import io.vault.mobile.data.local.PreferenceManager
import io.vault.mobile.ui.navigation.VaultNavigation
import io.vault.mobile.ui.screens.OnboardingScreen
import io.vault.mobile.ui.screens.SplashScreen
import io.vault.mobile.ui.screens.MasterKeyScreen
import io.vault.mobile.ui.screens.GoogleLoginScreen
import io.vault.mobile.data.cloud.GoogleDriveManager
import io.vault.mobile.ui.theme.VaultTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.fragment.app.FragmentActivity
import io.vault.mobile.security.BiometricAuthenticator
import io.vault.mobile.ui.screens.LockScreen
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState

@AndroidEntryPoint
class MainActivity : FragmentActivity() {


    @Inject lateinit var preferenceManager: PreferenceManager
    @Inject lateinit var biometricAuthenticator: BiometricAuthenticator
    @Inject lateinit var masterKeyManager: io.vault.mobile.security.MasterKeyManager
    @Inject lateinit var driveManager: GoogleDriveManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prevent screenshots and screen recording for privacy
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            VaultTheme {
                val isOnboardingCompleted by preferenceManager.onboardingCompleted.collectAsState(initial = false)
                val isBiometricEnabled by preferenceManager.biometricEnabled.collectAsState(initial = false)
                var hasMasterKey by remember { mutableStateOf(masterKeyManager.hasLocalMasterKey()) }
                var currentStage by remember { mutableStateOf(AppStage.Splash) }

                // Side effect to re-check master key status when stage might need to change
                LaunchedEffect(isOnboardingCompleted, currentStage) {
                    hasMasterKey = masterKeyManager.hasLocalMasterKey()
                }

                val determineNextStage = {
                    when {
                        !isOnboardingCompleted -> AppStage.Onboarding
                        !driveManager.isConnected() -> AppStage.GoogleLogin
                        !hasMasterKey -> AppStage.MasterKey
                        isBiometricEnabled && biometricAuthenticator.isBiometricAvailable() -> AppStage.Lock
                        else -> AppStage.Main
                    }
                }

                when (currentStage) {
                    AppStage.Splash -> SplashScreen {
                        currentStage = determineNextStage()
                    }
                    AppStage.GoogleLogin -> GoogleLoginScreen(
                        onConnected = {
                            hasMasterKey = masterKeyManager.hasLocalMasterKey()
                            currentStage = determineNextStage()
                        }
                    )
                    AppStage.MasterKey -> MasterKeyScreen(
                        onFinished = { 
                            hasMasterKey = true
                            currentStage = determineNextStage()
                        }
                    )
                    AppStage.Lock -> LockScreen(
                        biometricAuthenticator = biometricAuthenticator,
                        onUnlock = { currentStage = determineNextStage() }
                    )
                    AppStage.Onboarding -> OnboardingScreen(
                        viewModel = androidx.hilt.navigation.compose.hiltViewModel(),
                        onFinished = { currentStage = determineNextStage() }
                    )
                    AppStage.Main -> {
                        // Continuous guard: if key is deleted (reset), jump out
                        if (!hasMasterKey) {
                            LaunchedEffect(Unit) { currentStage = determineNextStage() }
                        }
                        VaultNavigation()
                    }
                }
            }
        }
    }
}

enum class AppStage {
    Splash, Lock, Onboarding, GoogleLogin, MasterKey, Main
}
