package com.engfred.yvd.ui.home

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.engfred.yvd.domain.model.VideoMetadata

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

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Notifications needed for background downloads", Toast.LENGTH_LONG).show()
        }
    }

    // Request permission on start
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { errorMessage ->
            snackbarHostState.showSnackbar(
                message = errorMessage,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
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
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(text = data.visuals.message, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
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
                    colors = ButtonDefaults.buttonColors(
                        contentColor = Color.White
                    ),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
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
                    onDownloadClick = {
                        showFormatDialog = true
                    }
                )
            }

            // Downloading Progress Section
            if (state.isDownloading || state.downloadComplete) {
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = state.downloadStatusText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (state.isDownloading && state.downloadProgress == 0f) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.surfaceDim,
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { state.downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.surfaceDim,
                            )
                        }

                        AnimatedVisibility(visible = state.downloadComplete) {
                            Button(
                                onClick = { viewModel.openMediaFile(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                // FIX: state.isAudio is now valid
                                Text(if (state.isAudio) "Play Audio" else "Play Video")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFormatDialog && state.videoMetadata != null) {
        ModalBottomSheet(onDismissRequest = { showFormatDialog = false }) {
            val metadata = state.videoMetadata!!
            val tabTitles = listOf("Video", "Audio")
            var selectedTab by remember { mutableIntStateOf(0) }

            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (index == 0) Icons.Rounded.Movie else Icons.Rounded.Audiotrack,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(title)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTab == 0) {
                    // VIDEO LIST
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // FIX: Changed from items(metadata.formats) to items(metadata.videoFormats)
                        items(metadata.videoFormats) { format ->
                            ListItem(
                                headlineContent = { Text(format.resolution) },
                                supportingContent = { Text("${format.ext.uppercase()} • ${format.fileSize}") },
                                leadingContent = {
                                    Icon(Icons.Rounded.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                trailingContent = {
                                    Icon(
                                        Icons.Rounded.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.clickable {
                                    showFormatDialog = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    // FIX: Added urlText parameter
                                    viewModel.downloadMedia(urlText, format.formatId, isAudio = false)
                                }
                            )
                            Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                } else {
                    // AUDIO LIST
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(metadata.audioFormats) { format ->
                            ListItem(
                                // FIX: Changed format.quality to format.bitrate
                                headlineContent = { Text(format.bitrate) },
                                supportingContent = { Text("${format.ext.uppercase()} • ${format.fileSize}") },
                                leadingContent = {
                                    Icon(Icons.Rounded.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                },
                                trailingContent = {
                                    Icon(
                                        Icons.Rounded.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.clickable {
                                    showFormatDialog = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    // FIX: Added urlText parameter
                                    viewModel.downloadMedia(urlText, format.formatId, isAudio = true)
                                }
                            )
                            Divider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCard(
    metadata: VideoMetadata,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(metadata.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = metadata.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloading
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download in Progress...")
                    } else {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}