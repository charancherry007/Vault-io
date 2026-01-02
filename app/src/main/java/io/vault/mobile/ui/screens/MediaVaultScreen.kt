package io.vault.mobile.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import io.vault.mobile.ui.theme.*
import io.vault.mobile.ui.viewmodel.MediaVaultViewModel
import io.vault.mobile.ui.viewmodel.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaVaultScreen(
    viewModel: MediaVaultViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val mediaItems by viewModel.mediaItems.collectAsState()
    val restoredItems by viewModel.restoredItems.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val backupProgress by viewModel.backupProgress.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val restoreProgress by viewModel.restoreProgress.collectAsState()
    val viewingItem by viewModel.viewingMediaItem.collectAsState()
    
    var activeTab by remember { mutableStateOf(0) } // 0 for Device, 1 for Restored
    var hasPermission by remember { mutableStateOf(false) }

    // Track the current list for the viewer to handle swiping
    val currentGalleryList = if (activeTab == 0) mediaItems else restoredItems
    val initialViewerIndex = remember(viewingItem, currentGalleryList) {
        currentGalleryList.indexOfFirst { it.id == viewingItem?.id }.coerceAtLeast(0)
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
            val errorMessage = when (e.statusCode) {
                12501 -> "Sign-in cancelled"
                10 -> "Developer error (Check SHA-1 and package name)"
                7 -> "Network error"
                else -> "Connection failed: ${e.statusCode}"
            }
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_FILE), Scope(DriveScopes.DRIVE_APPDATA))
        .build()
    val googleSignInClient = remember { GoogleSignIn.getClient(context, signInOptions) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        hasPermission = allGranted
        if (allGranted) viewModel.loadMedia()
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissions)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { 
                        if (selectedIds.isNotEmpty()) {
                            Text("${selectedIds.size} SELECTED", color = NeonBlue, fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                "MEDIA VAULT", 
                                color = NeonBlue, 
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ) 
                        }
                    },
                    navigationIcon = {
                        if (selectedIds.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = Color.White)
                            }
                        }
                    },
                    actions = {
                        if (selectedIds.isNotEmpty()) {
                            IconButton(onClick = { 
                                if (activeTab == 0) {
                                    Toast.makeText(context, "Deleting ${selectedIds.size} local references...", Toast.LENGTH_SHORT).show()
                                    // Local delete logic can be expanded here if needed
                                } else {
                                    viewModel.deleteRestoredItems { success, total ->
                                        Toast.makeText(context, "Deleted $success of $total from Cloud", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        } else if (isConnected) {
                            TextButton(onClick = { 
                                viewModel.restoreFromDrive() 
                                Toast.makeText(context, "Scanning for backups...", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("RESTORE FROM DRIVE", color = NeonBlue, fontSize = 12.sp)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                if (isConnected) {
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = Color.Transparent,
                        contentColor = NeonBlue,
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                color = NeonBlue
                            )
                        }
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("DEVICE") }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("RESTORED") }
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (isConnected && activeTab == 0) {
                if (selectedIds.isNotEmpty() || isBackingUp) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = CyberGray,
                        tonalElevation = 8.dp
                    ) {
                        if (isBackingUp) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("BACKING UP...", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${(backupProgress * 100).toInt()}%", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = backupProgress,
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = NeonBlue,
                                    trackColor = CyberGray.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            Button(
                                onClick = { 
                                    viewModel.encryptAndBackupSelected() 
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("BACKUP SELECTED (${selectedIds.size})", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (isConnected && activeTab == 1) {
                if (selectedIds.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = CyberGray,
                        tonalElevation = 8.dp
                    ) {
                        Button(
                            onClick = { 
                                viewModel.downloadSelectedFromRestored { success, total ->
                                    Toast.makeText(context, "Downloaded $success of $total items", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DOWNLOAD SELECTED (${selectedIds.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        containerColor = CyberBlack
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isRestoring) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("RESTORING FROM DRIVE...", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("${(restoreProgress * 100).toInt()}%", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = restoreProgress,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = NeonBlue,
                            trackColor = CyberGray.copy(alpha = 0.5f)
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (!isConnected) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(80.dp), tint = NeonBlue)
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Connect to Google Drive", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Securely backup and restore photos/videos.", color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("CONNECT NOW", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NeonBlue)
                        }
                    } else {
                        val currentList = if (activeTab == 0) mediaItems else restoredItems
                        if (currentList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = if (activeTab == 0) Icons.Default.PhotoLibrary else Icons.Default.CloudOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = Color.Gray.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        if (activeTab == 0) "No local media items found" else "No restored media items",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(110.dp),
                                contentPadding = PaddingValues(4.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(currentList) { item ->
                                    MediaGridItem(
                                        item = item,
                                        isSelected = selectedIds.contains(item.id),
                                        onClick = { 
                                            if (selectedIds.isNotEmpty()) viewModel.toggleSelection(item.id)
                                            else viewModel.setViewingItem(item)
                                        },
                                        onLongClick = {
                                            if (selectedIds.isEmpty()) viewModel.toggleSelection(item.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Media Viewer
            viewingItem?.let { item ->
                MediaViewer(
                    items = currentGalleryList,
                    initialIndex = initialViewerIndex,
                    isRestored = activeTab == 1,
                    onSave = { selectedItem ->
                        viewModel.saveToGallery(selectedItem) { success ->
                            Toast.makeText(context, if (success) "Saved to Gallery" else "Failed to save", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onClose = { viewModel.setViewingItem(null) }
                )
            }

        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridItem(
    item: MediaItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(CyberGray)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) NeonBlue else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Icon(
            imageVector = if (item.isBackedUp) Icons.Default.CloudDone else Icons.Default.CloudOff,
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(18.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp)).padding(2.dp),
            tint = if (item.isBackedUp) NeonBlue else Color.White.copy(alpha = 0.7f)
        )

        if (isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(NeonBlue.copy(alpha = 0.2f)))
            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), tint = NeonBlue)
        }

        if (item.mimeType.startsWith("video")) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.align(Alignment.Center).size(24.dp), tint = Color.White.copy(alpha = 0.8f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewer(
    items: List<MediaItem>,
    initialIndex: Int,
    isRestored: Boolean = false,
    onSave: ((MediaItem) -> Unit)? = null,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { items.size })
    val currentItem = items.getOrNull(pagerState.currentPage)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier.fillMaxSize().clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onClose() },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
        
        // Header
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRestored && onSave != null && currentItem != null) {
                IconButton(
                    onClick = { onSave(currentItem) },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = "Save to device", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // Footer
        currentItem?.let { item ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .padding(24.dp)
            ) {
                Text(item.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${(item.size / 1024)} KB • ${item.mimeType}", color = Color.Gray, fontSize = 14.sp)
                Text("${pagerState.currentPage + 1} / ${items.size}", color = NeonBlue, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
