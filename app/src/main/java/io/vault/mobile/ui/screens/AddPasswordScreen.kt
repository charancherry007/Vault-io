package io.vault.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
            CyberTextField(
                value = appName, 
                onValueChange = { appName = it }, 
                label = "App / Service Name",
                enabled = !isEditMode 
            )
            CyberTextField(
                value = packageNameState, 
                onValueChange = { packageNameState = it }, 
                label = "Package Name (e.g. com.example.app)",
                placeholder = "Optional for Autofill"
            )
            CyberTextField(value = username, onValueChange = { username = it }, label = "Username / Email")

            CyberTextField(value = password, onValueChange = { password = it }, label = "Password", isPassword = true)
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

