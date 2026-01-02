package io.vault.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vault.mobile.ui.theme.NeonBlue
import io.vault.mobile.ui.theme.NeonPurple
import io.vault.mobile.ui.theme.TextSecondary
import io.vault.mobile.ui.viewmodel.VaultViewModel
import io.vault.mobile.ui.components.CyberTextField
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: VaultViewModel,
    onNavigateBack: () -> Unit
) {
    val isConnectedToDrive by viewModel.isConnectedToDrive.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val remoteKeyExists by viewModel.remoteKeyExists.collectAsState()

    var masterPassword by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var resetDialogStage by remember { mutableStateOf<BackupResetStage?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        viewModel.checkDriveConnection()
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                viewModel.onDriveConnected()
                Toast.makeText(context, "Connected to Google Drive successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(context, "Connection failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_APPDATA))
        .build()
    val googleSignInClient = remember { GoogleSignIn.getClient(context, signInOptions) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CLOUD BACKUP & RESTORE", color = NeonBlue, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Icon(
                Icons.Default.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = NeonBlue
            )

            Text(
                "GOOGLE DRIVE VAULT SYNC",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                letterSpacing = 2.sp
            )

            Text(
                "Securely sync your encrypted vault or restore from your Google Drive account.",
                color = TextSecondary,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )

            Divider(color = Color.DarkGray, thickness = 1.dp)

            if (!isConnectedToDrive) {
                Surface(
                    color = Color(0xFF1A1C23),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "DRIVE DISCONNECTED", 
                            color = NeonBlue, 
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("CONNECT TO DRIVE", fontWeight = FontWeight.Bold, color = Color.Black)
                        }
                    }
                }
            } else {
                // MASTER PASSWORD SECTION
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MASTER PASSWORD", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    CyberTextField(
                        value = masterPassword,
                        onValueChange = { masterPassword = it },
                        label = "Vault Master Password",
                        isPassword = true,
                        placeholder = "Used to encrypt/decrypt cloud data"
                    )
                }

                Divider(color = Color.DarkGray.copy(alpha = 0.5f), thickness = 0.5.dp)

                // SYNC & RESTORE BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { viewModel.syncToCloud(masterPassword) },
                        modifier = Modifier.weight(1f),
                        enabled = masterPassword.isNotBlank() && !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSyncing) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp))
                        else Text("SYNC NOW", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { 
                            if (remoteKeyExists) {
                                showPasswordDialog = true 
                            } else {
                                viewModel.validateAndRestoreCloudBackup(masterPassword) { 
                                    Toast.makeText(context, "Vault and media restored successfully.", Toast.LENGTH_LONG).show()
                                    onNavigateBack() 
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = masterPassword.isNotBlank() && !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSyncing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("RESTORE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (remoteKeyExists) {
                    Text(
                        "☁️ Master Key backup found on Drive. Verification required for restore.",
                        color = NeonBlue.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            if (showPasswordDialog) {
                AlertDialog(
                    onDismissRequest = { showPasswordDialog = false },
                    title = { Text("Verify Master Password", color = NeonBlue) },
                    text = {
                        Column {
                            Text("Please enter your Master Password to verify identity before restoring data.", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            CyberTextField(
                                value = masterPassword,
                                onValueChange = { masterPassword = it },
                                label = "Master Password",
                                isPassword = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showPasswordDialog = false
                                viewModel.validateAndRestoreCloudBackup(masterPassword) { 
                                    Toast.makeText(context, "Vault and media restored successfully.", Toast.LENGTH_LONG).show()
                                    onNavigateBack() 
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                        ) {
                            Text("VERIFY & RESTORE", color = Color.Black)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPasswordDialog = false }) {
                            Text("CANCEL", color = Color.Gray)
                        }
                    },
                    containerColor = Color(0xFF1A1C23),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            syncError?.let { errorMsg: String ->
                Text(text = errorMsg, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
            }

            if (isConnectedToDrive) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { resetDialogStage = BackupResetStage.Warning },
                    enabled = !isSyncing
                ) {
                    Text(
                        "Forgot Master Key? Reset Vault",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    // RESET FLOW DIALOGS (Same logic as MasterKeyScreen)
    resetDialogStage?.let { stage ->
        AlertDialog(
            onDismissRequest = { resetDialogStage = null },
            title = { 
                Text(
                    when (stage) {
                        BackupResetStage.Warning -> "Reset Vault?"
                        BackupResetStage.CloudCheck -> "Wipe Cloud Backups?"
                        BackupResetStage.FinalConfirmation -> "PERMANENT DATA WIPE"
                    },
                    color = if (stage == BackupResetStage.FinalConfirmation) Color.Red else NeonBlue,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    when (stage) {
                        BackupResetStage.Warning -> "If you forgot your password, the only way to regain access is to perform a full reset. This will delete all your local passwords and media."
                        BackupResetStage.CloudCheck -> "This reset will also attempt to permenantly delete all encrypted backups from your Google Drive account."
                        BackupResetStage.FinalConfirmation -> "This action will permanently delete all backups and cannot be undone. Are you absolutely sure you want to proceed?"
                    },
                    fontSize = 14.sp,
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (stage) {
                            BackupResetStage.Warning -> resetDialogStage = BackupResetStage.CloudCheck
                            BackupResetStage.CloudCheck -> resetDialogStage = BackupResetStage.FinalConfirmation
                            BackupResetStage.FinalConfirmation -> {
                                resetDialogStage = null
                                viewModel.factoryReset {
                                    onNavigateBack() // Go back to splash/main to trigger MK setup
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (stage == BackupResetStage.FinalConfirmation) Color.Red else NeonBlue
                    )
                ) {
                    Text(
                        when (stage) {
                            BackupResetStage.Warning -> "CONTINUE"
                            BackupResetStage.CloudCheck -> "I UNDERSTAND"
                            BackupResetStage.FinalConfirmation -> "DELETE EVERYTHING"
                        },
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { resetDialogStage = null }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1A1C23),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

private enum class BackupResetStage {
    Warning, CloudCheck, FinalConfirmation
}

