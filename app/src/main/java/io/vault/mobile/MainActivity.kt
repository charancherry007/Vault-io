package io.vault.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import io.vault.mobile.blockchain.IWalletManager
import io.vault.mobile.blockchain.RewardManager
import io.vault.mobile.data.local.PreferenceManager
import io.vault.mobile.ui.navigation.VaultNavigation
import io.vault.mobile.ui.screens.OnboardingScreen
import io.vault.mobile.ui.screens.SplashScreen
import io.vault.mobile.ui.theme.VaultTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.fragment.app.FragmentActivity
import io.vault.mobile.security.BiometricAuthenticator
import io.vault.mobile.ui.screens.LockScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var walletManager: IWalletManager
    @Inject lateinit var rewardManager: RewardManager
    @Inject lateinit var preferenceManager: PreferenceManager
    @Inject lateinit var biometricAuthenticator: BiometricAuthenticator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prevent screenshots and screen recording for privacy
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            VaultTheme {
                var currentStage by remember { mutableStateOf(AppStage.Splash) }

                when (currentStage) {
                    AppStage.Splash -> SplashScreen {
                        // Logic to decide next stage
                        val isOnboardingCompleted = runBlocking { preferenceManager.onboardingCompleted.first() }
                        val isBiometricEnabled = runBlocking { preferenceManager.biometricEnabled.first() }
                        
                        if (!isOnboardingCompleted) {
                            currentStage = AppStage.Onboarding
                        } else if (isBiometricEnabled && biometricAuthenticator.isBiometricAvailable()) {
                            currentStage = AppStage.Lock
                        } else {
                            currentStage = AppStage.Main
                        }
                    }
                    AppStage.Lock -> LockScreen(
                        biometricAuthenticator = biometricAuthenticator,
                        onUnlock = { currentStage = AppStage.Main }
                    )
                    AppStage.Onboarding -> OnboardingScreen(
                        viewModel = androidx.hilt.navigation.compose.hiltViewModel(),
                        onFinished = { currentStage = AppStage.Main }
                    )
                    AppStage.Main -> VaultNavigation(
                        walletManager = walletManager,
                        rewardManager = rewardManager
                    )
                }
            }
        }
    }
}

enum class AppStage {
    Splash, Lock, Onboarding, Main
}
