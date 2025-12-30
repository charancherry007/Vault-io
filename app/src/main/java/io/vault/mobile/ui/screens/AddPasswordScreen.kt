package io.vault.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.vault.mobile.ui.theme.NeonBlue
import io.vault.mobile.ui.theme.TextSecondary
import io.vault.mobile.ui.viewmodel.VaultViewModel
import io.vault.mobile.ui.components.CyberTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPasswordScreen(
    viewModel: VaultViewModel,
    onNavigateBack: () -> Unit
) {
    val selectedEntry by viewModel.selectedEntry.collectAsState()
    val isEditMode = selectedEntry != null

    var appName by remember { mutableStateOf(selectedEntry?.appName ?: "") }
    var packageNameState by remember { mutableStateOf(selectedEntry?.packageName ?: "") }
    var username by remember { mutableStateOf(selectedEntry?.username ?: "") }
    var password by remember { 
        mutableStateOf(
            if (isEditMode) viewModel.getDecryptedPassword(selectedEntry!!.encryptedPassword) 
            else ""
        ) 
    }
    var notes by remember { mutableStateOf(selectedEntry?.notes ?: "") }

    val installedApps by viewModel.installedApps.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val filteredApps = remember(appName, installedApps) {
        if (appName.isEmpty()) installedApps
        else installedApps.filter { it.name.contains(appName, ignoreCase = true) }
    }
    
    val strength = remember(password) { viewModel.calculatePasswordStrength(password) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (isEditMode) "EDIT RECORD" else "ENCRYPT NEW RECORD", 
                        color = NeonBlue, 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearSelection()
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded && filteredApps.isNotEmpty() && !isEditMode,
                onExpandedChange = { if (!isEditMode) expanded = it }
            ) {
                CyberTextField(
                    value = appName,
                    onValueChange = {
                        appName = it
                        expanded = true
                    },
                    label = "App / Service Name",
                    enabled = !isEditMode,
                    modifier = Modifier.menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expanded && !isEditMode,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color.Black)
                ) {
                    if (appName.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Save, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Use \"$appName\" as custom entry", color = NeonBlue)
                                }
                            },
                            onClick = {
                                expanded = false
                            }
                        )
                        Divider(color = Color.DarkGray, thickness = 0.5.dp)
                    }

                    if (filteredApps.isNotEmpty()) {
                        Text(
                            "INSTALLED APPS",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        filteredApps.forEach { app ->
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(app.name, color = NeonBlue, fontWeight = FontWeight.Bold)
                                        Text(app.packageName, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                onClick = {
                                    appName = app.name
                                    packageNameState = app.packageName
                                    expanded = false
                                },
                                colors = MenuDefaults.itemColors(
                                    textColor = NeonBlue,
                                    trailingIconColor = NeonBlue
                                )
                            )
                        }
                    } else if (appName.isEmpty()) {
                         Text(
                            "RECENT APPS",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        installedApps.take(5).forEach { app ->
                             DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(app.name, color = NeonBlue, fontWeight = FontWeight.Bold)
                                        Text(app.packageName, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                onClick = {
                                    appName = app.name
                                    packageNameState = app.packageName
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            CyberTextField(
                value = packageNameState, 
                onValueChange = { packageNameState = it }, 
                label = "Package Name (e.g. com.example.app)",
                placeholder = "Optional for Autofill"
            )
            CyberTextField(value = username, onValueChange = { username = it }, label = "Username / Email")

            CyberTextField(value = password, onValueChange = { password = it }, label = "Password", isPassword = true)
            
            // Password Strength Indicator
            if (password.isNotEmpty()) {
                val strengthColor = when (strength) {
                    io.vault.mobile.ui.viewmodel.PasswordStrength.Poor -> Color.Red
                    io.vault.mobile.ui.viewmodel.PasswordStrength.Weak -> Color.Yellow
                    io.vault.mobile.ui.viewmodel.PasswordStrength.Strong -> NeonBlue
                }
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(3) { index ->
                            val active = index <= when(strength) {
                                io.vault.mobile.ui.viewmodel.PasswordStrength.Poor -> 0
                                io.vault.mobile.ui.viewmodel.PasswordStrength.Weak -> 1
                                io.vault.mobile.ui.viewmodel.PasswordStrength.Strong -> 2
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(4.dp)
                                    .background(
                                        if (active) strengthColor else Color.DarkGray,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }
                    Text(
                        text = "Strength: ${strength.name}",
                        color = strengthColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            CyberTextField(value = notes, onValueChange = { notes = it }, label = "Notes (Optional)", singleLine = false)

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (appName.isNotBlank() && password.isNotBlank()) {
                        if (isEditMode) {
                            viewModel.updateEntry(selectedEntry!!, password, notes.ifBlank { null }, packageNameState.ifBlank { null })
                        } else {
                            viewModel.addEntry(appName, username, password, notes.ifBlank { null }, packageNameState.ifBlank { null })
                        }
                        viewModel.clearSelection()
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (isEditMode) "UPDATE VAULT" else "SECURE IN VAULT", 
                    color = Color.Black, 
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

