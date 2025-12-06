package com.engfred.yvd.ui.home

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.engfred.yvd.ui.components.ConfirmationDialog
import com.engfred.yvd.ui.components.DownloadProgressCard
import com.engfred.yvd.ui.components.FormatSelectionSheet
import com.engfred.yvd.ui.components.ThemeSelectionDialog
import com.engfred.yvd.ui.components.VideoCard
import com.engfred.yvd.util.openYoutube

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Notifications needed for background downloads", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { errorMessage ->
            snackbarHostState.showSnackbar(message = errorMessage, duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }

    // --- Dialogs Controlled by ViewModel State ---

    if (state.isCancelDialogVisible) {
        ConfirmationDialog(
            title = "Cancel Download?",
            text = "Are you sure you want to stop the current download?",
            confirmText = "Yes, Cancel",
            onConfirm = { viewModel.cancelDownload() },
            onDismiss = { viewModel.hideCancelDialog() }
        )
    }

    if (state.isThemeDialogVisible) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { theme -> viewModel.updateTheme(theme) },
            onDismiss = { viewModel.hideThemeDialog() }
        )
    }

    // Root Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Content Layer (TopBar + Scrollable Content)
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TopAppBar(
                title = { Text("YV Downloader") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { viewModel.showThemeDialog() }) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsBrightness,
                            contentDescription = "Change Theme",
                            modifier = Modifier.size(28.dp) // Adjusted size for better standard fit
                        )
                    }
                }
            )

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Takes up remaining space below TopBar
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // URL Input Field
                OutlinedTextField(
                    value = state.urlInput,
                    onValueChange = { viewModel.onUrlInputChanged(it) },
                    label = { Text("Paste YouTube Link") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            if (clipboardManager.hasText()) {
                                clipboardManager.getText()?.let { clipData ->
                                    val pastedText = clipData.text.toString()
                                    keyboardController?.hide()
                                    viewModel.loadVideoInfo(pastedText)
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Get Info Button
                if (state.videoMetadata == null && !state.isDownloading && !state.downloadComplete) {
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.loadVideoInfo(state.urlInput)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Text("Get Video Info", color = Color.White)
                        }
                    }
                }

                // Instructions / Empty State
                if (state.videoMetadata == null && !state.isDownloading && !state.downloadComplete && !state.isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp)
                            .background(Color.Transparent)
                            .padding(start = 24.dp, end = 24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .padding(24.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "How to Download",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "1. Tap the YouTube button to find a video\n2. Copy the video link\n3. Paste it above to start",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Video Metadata Card
                state.videoMetadata?.let { metadata ->
                    Spacer(modifier = Modifier.height(16.dp))
                    VideoCard(
                        metadata = metadata,
                        isDownloading = state.isDownloading,
                        onDownloadClick = { viewModel.showFormatDialog() }
                    )
                }

                // Download Progress
                if (state.isDownloading || state.downloadComplete) {
                    Spacer(modifier = Modifier.height(24.dp))
                    DownloadProgressCard(
                        statusText = state.downloadStatusText,
                        progress = state.downloadProgress,
                        isDownloading = state.isDownloading,
                        isComplete = state.downloadComplete,
                        isAudio = state.isAudio,
                        onCancel = { viewModel.showCancelDialog() },
                        onPlay = { viewModel.openMediaFile() },
                        onShare = { viewModel.shareMediaFile() }
                    )
                }

                // Spacer to clear the FAB so content doesn't get hidden behind it
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Floating Action Button Layer
        ExtendedFloatingActionButton(
            onClick = { openYoutube(context) },
            containerColor = Color(0xFFFF0000),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.SmartDisplay,
                modifier = Modifier.size(34.dp),
                contentDescription = "Open YouTube"
            )
        }

        // Snackbar Layer
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Format Dialog (Sheet)
    if (state.isFormatDialogVisible && state.videoMetadata != null) {
        FormatSelectionSheet(
            metadata = state.videoMetadata!!,
            onDismiss = { viewModel.hideFormatDialog() },
            onFormatSelected = { formatId, isAudio ->
                // Permission Check trigger
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.downloadMedia(formatId, isAudio)
            }
        )
    }
}