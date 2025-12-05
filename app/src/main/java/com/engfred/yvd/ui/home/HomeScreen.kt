package com.engfred.yvd.ui.home

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.engfred.yvd.ui.components.ConfirmationDialog
import com.engfred.yvd.ui.components.DownloadProgressCard
import com.engfred.yvd.ui.components.FormatSelectionSheet
import com.engfred.yvd.ui.components.ThemeSelectionDialog
import com.engfred.yvd.ui.components.VideoCard
import com.engfred.yvd.util.openYoutube

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    var urlText by rememberSaveable { mutableStateOf("") }

    var showFormatDialog by rememberSaveable { mutableStateOf(false) }
    var showCancelDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }

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

    // Theme Dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = currentTheme,
            onThemeSelected = { theme ->
                viewModel.updateTheme(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    Scaffold(
        // Prevent inner scaffold from doubling up system window insets, relying on padding instead
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("YV Downloader") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showThemeDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsBrightness,
                            contentDescription = "Change Theme",
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                // Added padding to clear the Bottom Navigation Bar in MainScreen
                modifier = Modifier.padding(bottom = 135.dp),
                onClick = { openYoutube(context) },
                containerColor = Color(0xFFFF0000),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmartDisplay,
                    modifier = Modifier.size(34.dp),
                    contentDescription = "Open YouTube"
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

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
            if (
                state.videoMetadata == null && !state.isDownloading && !state.downloadComplete
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
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Get Video Info", color = Color.White)
                    }
                }
            }


            if (
                state.videoMetadata == null && !state.isDownloading && !state.downloadComplete && !state.isLoading
            ){
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .padding(start = 24.dp, end = 24.dp)
                        .navigationBarsPadding()
                ) {
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
                    onPlay = { viewModel.openMediaFile() },
                    onShare = { viewModel.shareMediaFile() }
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
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