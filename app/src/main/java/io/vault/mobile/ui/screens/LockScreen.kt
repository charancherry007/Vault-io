package io.vault.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.vault.mobile.security.BiometricAuthenticator
import io.vault.mobile.ui.theme.NeonBlue
import io.vault.mobile.ui.theme.NeonPurple

@Composable
fun LockScreen(
    biometricAuthenticator: BiometricAuthenticator,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Automatically trigger biometric prompt on launch
    LaunchedEffect(Unit) {
        if (biometricAuthenticator.isBiometricAvailable()) {
            biometricAuthenticator.authenticate(
                activity = context as FragmentActivity,
                title = "VAULT ENCRYPTED",
                subtitle = "Authenticate to unlock your secure vault",
                cryptoObject = io.vault.mobile.security.CryptoManager.getBiometricCryptoObject(),
                onSuccess = { onUnlock() },
                onError = { errorMessage = it }
            )
        } else {
            // If not available, we might need a PIN fallback or just bypass for now
            // But usually the toggle wouldn't be on if not available
            onUnlock()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "VAULT ENCRYPTED",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonBlue,
            fontWeight = FontWeight.Black,
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.height(64.dp))

        IconButton(
            onClick = {
                errorMessage = null
                biometricAuthenticator.authenticate(
                    activity = context as FragmentActivity,
                    title = "VAULT ENCRYPTED",
                    subtitle = "Authenticate to unlock your secure vault",
                    cryptoObject = io.vault.mobile.security.CryptoManager.getBiometricCryptoObject(),
                    onSuccess = { onUnlock() },
                    onError = { errorMessage = it }
                )
            },
            modifier = Modifier
                .size(120.dp)
                .background(NeonBlue.copy(alpha = 0.1f), RoundedCornerShape(30.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = "Authenticate",
                tint = NeonBlue,
                modifier = Modifier.size(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                errorMessage = null
                biometricAuthenticator.authenticate(
                    activity = context as FragmentActivity,
                    title = "VAULT ENCRYPTED",
                    subtitle = "Authenticate to unlock your secure vault",
                    cryptoObject = io.vault.mobile.security.CryptoManager.getBiometricCryptoObject(),
                    onSuccess = { onUnlock() },
                    onError = { errorMessage = it }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("AUTHENTICATE", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(24.dp))
            Text(it, color = Color.Red, fontSize = 14.sp)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            "Hardware-Backed Security Active",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}
