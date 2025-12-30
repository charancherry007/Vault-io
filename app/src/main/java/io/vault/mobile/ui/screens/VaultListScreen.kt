package io.vault.mobile.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vault.mobile.data.local.entity.VaultEntry
import io.vault.mobile.ui.theme.NeonBlue
import io.vault.mobile.ui.theme.NeonPurple
import io.vault.mobile.ui.theme.TextSecondary
import io.vault.mobile.ui.viewmodel.VaultViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    viewModel: VaultViewModel,
    onAddClick: () -> Unit,
    onEntryClick: (VaultEntry) -> Unit
) {
    val entries by viewModel.vaultEntries.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var entryToDelete by remember { mutableStateOf<VaultEntry?>(null) }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Confirm Deletion", color = NeonBlue, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete the credentials for ${entryToDelete?.appName}? This action cannot be undone.", color = Color.White) },
            confirmButton = {
                TextButton(
                    onClick = {
                        entryToDelete?.let { viewModel.deleteEntry(it) }
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonBlue)
                ) {
                    Text("DELETE", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1A1C23),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = NeonBlue,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Password")
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "VAULT",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeonBlue,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
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
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search encrypted records...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonBlue) },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = Color.DarkGray
                )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries) { entry ->
                    VaultEntryItem(
                        entry = entry,
                        onDelete = { entryToDelete = entry },
                        onDecrypt = { viewModel.getDecryptedPassword(entry.encryptedPassword) },
                        onClick = { onEntryClick(entry) }
                    )
                }
            }
        }
    }
}

@Composable
fun VaultEntryItem(
    entry: VaultEntry,
    onDelete: () -> Unit,
    onDecrypt: () -> String,
    onClick: () -> Unit
) {
    var revealedPassword by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1A1C23), Color(0xFF0F1014))
                )
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.appName.uppercase(),
                    color = NeonBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = entry.username,
                    color = Color.White,
                    fontSize = 16.sp
                )
                
                AnimatedVisibility(visible = revealedPassword != null) {
                    Text(
                        text = revealedPassword ?: "",
                        color = NeonPurple,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Row {
                IconButton(onClick = {
                    if (revealedPassword == null) {
                        revealedPassword = onDecrypt()
                    } else {
                        revealedPassword = null
                    }
                }) {
                    Icon(
                        imageVector = if (revealedPassword == null) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Show/Hide",
                        tint = NeonBlue
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                }
            }
        }
    }
}
