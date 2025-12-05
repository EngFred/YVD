package com.engfred.yvd.ui.home

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.engfred.yvd.ui.components.ConfirmationDialog
import com.engfred.yvd.ui.components.DownloadProgressCard
import com.engfred.yvd.ui.components.FormatSelectionSheet
import com.engfred.yvd.ui.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    var urlText by remember { mutableStateOf("") }
    var showFormatDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

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
    
    if (showCancelDialog) {
        ConfirmationDialog(
            title = "Cancel Download?",
            text = "Are you sure you want to stop the current download?",
            confirmText = "Yes, Cancel",
            onConfirm = {
                viewModel.cancelDownload()
                showCancelDialog = false
            },
            onDismiss = { showCancelDialog = false }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("YV Downloader") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // URL Input Field
            OutlinedTextField(
                value = urlText,
                onValueChange = {
                    urlText = it
                    viewModel.onUrlChanged()
                },
                label = { Text("Paste YouTube Link") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        if (clipboardManager.hasText()) {
                            clipboardManager.getText()?.let { clipData ->
                                val pastedText = clipData.text.toString()
                                urlText = pastedText
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
            AnimatedVisibility(
                visible = state.videoMetadata == null && !state.isDownloading && !state.downloadComplete,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.loadVideoInfo(urlText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(contentColor = Color.White),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Get Video Info")
                    }
                }
            }

            // Video Metadata Card
            state.videoMetadata?.let { metadata ->
                Spacer(modifier = Modifier.height(16.dp))
                VideoCard(
                    metadata = metadata,
                    isDownloading = state.isDownloading,
                    onDownloadClick = { showFormatDialog = true }
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
                    onCancel = { showCancelDialog = true },
                    onPlay = { viewModel.openMediaFile(context) },
                    onShare = { viewModel.shareMediaFile(context) }
                )
            }
        }
    }

    if (showFormatDialog && state.videoMetadata != null) {
        FormatSelectionSheet(
            metadata = state.videoMetadata!!,
            onDismiss = { showFormatDialog = false },
            onFormatSelected = { formatId, isAudio ->
                showFormatDialog = false
                // Permission Check happens here before downloading
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.downloadMedia(urlText, formatId, isAudio)
            }
        )
    }
}