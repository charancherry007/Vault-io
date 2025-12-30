package io.vault.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vault.mobile.ui.theme.NeonBlue
import io.vault.mobile.ui.theme.TextSecondary
import io.vault.mobile.ui.viewmodel.VaultViewModel
import io.vault.mobile.ui.components.SettingItem
import io.vault.mobile.ui.components.SettingToggleItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: VaultViewModel,
    onNavigateToBackup: () -> Unit
) {
    val autoBackup by viewModel.autoBackupEnabled.collectAsState(initial = false)
    val autofillEnabled by viewModel.autofillEnabled.collectAsState(initial = false)
    val biometricEnabled by viewModel.biometricEnabled.collectAsState(initial = false)
    val isBiometricAvailable = remember { viewModel.isBiometricAvailable() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "SYSTEM SETTINGS",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeonBlue,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingItem(
                title = "Zero-Knowledge Encryption",
                subtitle = "AES-256-GCM (Active)",
                icon = Icons.Default.Shield
            )
            if (isBiometricAvailable) {
                SettingToggleItem(
                    title = "Biometric Lock",
                    subtitle = "Require fingerprint to unlock",
                    icon = Icons.Default.Security,
                    checked = biometricEnabled,
                    onCheckedChange = { viewModel.toggleBiometric(it) }
                )
            }
            SettingToggleItem(
                title = "Auto Backup",
                subtitle = "Sync vault automatically",
                icon = Icons.Default.CloudUpload,
                checked = autoBackup,
                onCheckedChange = { viewModel.toggleAutoBackup(it) }
            )
            SettingToggleItem(
                title = "Autofill Service",
                subtitle = "Auto-fill credentials in other apps",
                icon = Icons.Default.DynamicForm,
                checked = autofillEnabled,
                onCheckedChange = { viewModel.toggleAutofill(it) }
            )
            SettingItem(
                title = "Cloud Sync",
                subtitle = "Backup or restore your vault",
                icon = Icons.Default.CloudSync,
                onClick = onNavigateToBackup
            )
            SettingItem(
                title = "Decentralized Storage",
                subtitle = "Gateway connected",
                icon = Icons.Default.Storage
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                "App Version: 0.6.0",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

