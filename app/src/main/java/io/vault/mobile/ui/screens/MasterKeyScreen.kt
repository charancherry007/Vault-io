package io.vault.mobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.vault.mobile.ui.theme.*
import io.vault.mobile.ui.viewmodel.MasterKeyUiState
import io.vault.mobile.ui.viewmodel.MasterKeyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterKeyScreen(
    viewModel: MasterKeyViewModel = hiltViewModel(),
    onFinished: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val error by viewModel.error.collectAsState()

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var resetDialogStage by remember { mutableStateOf<MasterKeyResetStage?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is MasterKeyUiState.Loading -> {
                CircularProgressIndicator(color = NeonBlue)
            }
            is MasterKeyUiState.AlreadyInitialized -> {
                LaunchedEffect(Unit) { onFinished() }
            }
            is MasterKeyUiState.NeedsCreation, is MasterKeyUiState.NeedsRestore -> {
                val isRestore = uiState is MasterKeyUiState.NeedsRestore
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isRestore) Icons.Default.CloudDownload else Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = NeonBlue
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (isRestore) "RESTORE MASTER KEY" else "CREATE MASTER KEY",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isRestore) 
                            "Enter the password you used when creating your master key to restore your vault." 
                            else "This password will be used to back up your master key to Google Drive.",
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    
                    if (!isRestore) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF331111)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "This password cannot be recovered. Losing it means losing access to all your data.",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Master Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = NeonBlue
                        )
                    )

                    if (!isRestore) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm password") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = NeonBlue,
                                unfocusedBorderColor = Color.Gray,
                                focusedLabelColor = NeonBlue
                            )
                        )
                    }

                    if (error != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(error!!, color = Color.Red, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (isRestore) {
                                viewModel.restoreMasterKey(password, onFinished)
                            } else {
                                if (password.isNotEmpty() && password == confirmPassword) {
                                    viewModel.createMasterKey(password, onFinished)
                                }
                            }
                        },
                        enabled = !isProcessing && password.isNotEmpty() && (isRestore || password == confirmPassword),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                if (isRestore) "RESTORE KEY" else "CREATE & SYNC KEY",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    if (isRestore) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { resetDialogStage = MasterKeyResetStage.Warning },
                            enabled = !isProcessing
                        ) {
                            Text(
                                "Forgot Password? Reset Vault",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // RESET FLOW DIALOGS
        resetDialogStage?.let { stage ->
            AlertDialog(
                onDismissRequest = { resetDialogStage = null },
                title = { 
                    Text(
                        when (stage) {
                            MasterKeyResetStage.Warning -> "Reset Vault?"
                            MasterKeyResetStage.CloudCheck -> "Wipe Cloud Backups?"
                            MasterKeyResetStage.FinalConfirmation -> "PERMANENT DATA WIPE"
                        },
                        color = if (stage == MasterKeyResetStage.FinalConfirmation) Color.Red else NeonBlue,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        when (stage) {
                            MasterKeyResetStage.Warning -> "If you forgot your password, the only way to regain access is to perform a full reset. This will delete all your local passwords and media."
                            MasterKeyResetStage.CloudCheck -> "This reset will also attempt to permenantly delete all encrypted backups from your Google Drive account."
                            MasterKeyResetStage.FinalConfirmation -> "This action will permanently delete all backups and cannot be undone. Are you absolutely sure you want to proceed?"
                        },
                        fontSize = 14.sp,
                        color = Color.White
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when (stage) {
                                MasterKeyResetStage.Warning -> resetDialogStage = MasterKeyResetStage.CloudCheck
                                MasterKeyResetStage.CloudCheck -> resetDialogStage = MasterKeyResetStage.FinalConfirmation
                                MasterKeyResetStage.FinalConfirmation -> {
                                    resetDialogStage = null
                                    viewModel.factoryReset {
                                        viewModel.checkStatus() // Refresh to NeedsCreation
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (stage == MasterKeyResetStage.FinalConfirmation) Color.Red else NeonBlue
                        )
                    ) {
                        Text(
                            when (stage) {
                                MasterKeyResetStage.Warning -> "CONTINUE"
                                MasterKeyResetStage.CloudCheck -> "I UNDERSTAND"
                                MasterKeyResetStage.FinalConfirmation -> "DELETE EVERYTHING"
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
}

enum class MasterKeyResetStage {
    Warning, CloudCheck, FinalConfirmation
}
