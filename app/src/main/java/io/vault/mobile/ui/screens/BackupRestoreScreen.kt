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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: VaultViewModel,
    onNavigateBack: () -> Unit
) {
    val backupCid by viewModel.backupCid.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncError by viewModel.syncError.collectAsState()

    var inputCid by remember { mutableStateOf("") }
    var masterPassword by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                "DECENTRALIZED VAULT SYNC",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                letterSpacing = 2.sp
            )

            Text(
                "Securely sync your encrypted vault or restore from an existing UID.",
                color = TextSecondary,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )

            Divider(color = Color.DarkGray, thickness = 1.dp)

            // MASTER PASSWORD SECTION (GLOBAL FOR BOTH SYNC/RESTORE)
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

            // RESTORE SECTION
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RESTORE FROM Cloud", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                OutlinedTextField(
                    value = inputCid,
                    onValueChange = { inputCid = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter Backup UID", color = Color.Gray) },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Button(
                    onClick = { viewModel.restoreFromCloud(inputCid, masterPassword) { onNavigateBack() } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = inputCid.isNotBlank() && masterPassword.isNotBlank() && !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSyncing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text("RESTORE VAULT", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // BACKUP SECTION
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CREATE NEW BACKUP", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Button(
                    onClick = { viewModel.syncToCloud(masterPassword) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = masterPassword.isNotBlank() && !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSyncing) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                    else Text("SYNC NOW", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                backupCid?.let { cid ->
                    Surface(
                        color = Color(0xFF1A1C23),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("BACKUP SUCCESSFUL!", color = NeonBlue, fontWeight = FontWeight.Bold)
                            Text("Your UID (Save this securely):", color = TextSecondary, fontSize = 12.sp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    cid,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(cid))
                                        scope.launch {
                                            snackbarHostState.showSnackbar("UID copied to clipboard")
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy UID",
                                        tint = NeonBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            syncError?.let { error ->
                Text(error, color = Color.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

