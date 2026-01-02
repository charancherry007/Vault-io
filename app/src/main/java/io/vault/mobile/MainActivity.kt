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
import io.vault.mobile.BuildConfig
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.unit.dp
import io.vault.mobile.security.BiometricAuthenticator
import io.vault.mobile.ui.screens.LockScreen
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*

@AndroidEntryPoint
class MainActivity : FragmentActivity() {


    @Inject lateinit var preferenceManager: PreferenceManager
    @Inject lateinit var biometricAuthenticator: BiometricAuthenticator
    @Inject lateinit var masterKeyManager: io.vault.mobile.security.MasterKeyManager
    @Inject lateinit var driveManager: GoogleDriveManager
    @Inject lateinit var integrityManager: io.vault.mobile.security.IntegrityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prevent screenshots and screen recording for privacy
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        
        val isDeviceCompromised = io.vault.mobile.security.RootDetector.isDeviceCompromised()
        val isDebug = BuildConfig.DEBUG

        setContent {
            VaultTheme {
                if (isDeviceCompromised && !isDebug) {
                    // Security Guard: Block app on compromised devices in release
                    CompromisedDeviceScreen()
                } else {
                    MainAppContent()
                }
            }
        }
    }

    @Composable
    private fun MainAppContent() {
                val isOnboardingCompleted by preferenceManager.onboardingCompleted.collectAsState(initial = false)
                val isBiometricEnabled by preferenceManager.biometricEnabled.collectAsState(initial = false)
                var isSessionUnlocked by remember { mutableStateOf(false) }
                var hasMasterKey by remember { mutableStateOf(masterKeyManager.hasLocalMasterKey()) }
                var currentStage by remember { mutableStateOf(AppStage.Splash) }

                // Side effect to re-check master key status when stage might need to change
                LaunchedEffect(isOnboardingCompleted, currentStage) {
                    hasMasterKey = masterKeyManager.hasLocalMasterKey()
                }

                // Device Integrity Check
                LaunchedEffect(Unit) {
                    // Note: Replace with actual Cloud Project Number from Play Console
                    val cloudProjectNumber = 560128341629L // Placeholder
                    val integrityResult = integrityManager.requestIntegrityToken(cloudProjectNumber)
                    integrityResult.onSuccess { token ->
                        android.util.Log.d("IntegrityCheck", "Token received: ${token.take(20)}...")
                    }.onFailure { e ->
                        android.util.Log.e("IntegrityCheck", "Integrity check failed: ${e.message}")
                    }
                }

                val determineNextStage = {
                    when {
                        !isOnboardingCompleted -> AppStage.Onboarding
                        !driveManager.isConnected() -> AppStage.GoogleLogin
                        !hasMasterKey -> AppStage.MasterKey
                        isBiometricEnabled && biometricAuthenticator.isBiometricAvailable() && !isSessionUnlocked -> AppStage.Lock
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
                        onUnlock = { 
                            isSessionUnlocked = true
                            currentStage = determineNextStage() 
                        }
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

    @Composable
    private fun CompromisedDeviceScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "SECURITY ALERT",
                    color = Color.Red,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "This device appears to be rooted or compromised. For your security, Vault-io cannot run on modified environments.",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { finish() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("EXIT APP", color = Color.White)
                }
            }
        }
    }
}

enum class AppStage {
    Splash, Lock, Onboarding, GoogleLogin, MasterKey, Main
}
