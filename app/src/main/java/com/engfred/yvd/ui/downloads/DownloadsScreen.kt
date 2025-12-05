package com.engfred.yvd.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.engfred.yvd.domain.model.DownloadItem
import com.engfred.yvd.ui.components.ConfirmationDialog
import com.engfred.yvd.ui.components.FileThumbnail

enum class DeleteMode {
    NONE, SINGLE, SELECTED, ALL
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Selection State
    val selectedItems = remember { mutableStateListOf<DownloadItem>() }
    val isSelectionMode = selectedItems.isNotEmpty()

    // Deletion Dialog State
    var deleteMode by remember { mutableStateOf(DeleteMode.NONE) }
    var singleItemToDelete by remember { mutableStateOf<DownloadItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadFiles()
    }

    BackHandler(enabled = isSelectionMode) {
        selectedItems.clear()
    }

    // --- Dialog Logic ---
    if (deleteMode != DeleteMode.NONE) {
        val (title, text, action) = when (deleteMode) {
            DeleteMode.SINGLE -> Triple(
                "Delete File?",
                "Are you sure you want to delete '${singleItemToDelete?.fileName}'?",
                { singleItemToDelete?.let { viewModel.deleteFile(it) } }
            )
            DeleteMode.SELECTED -> Triple(
                "Delete ${selectedItems.size} items?",
                "Are you sure you want to delete these ${selectedItems.size} files? This cannot be undone.",
                {
                    viewModel.deleteFiles(selectedItems.toList())
                    selectedItems.clear()
                }
            )
            DeleteMode.ALL -> Triple(
                "Delete All Files?",
                "Are you sure you want to delete ALL downloaded files? This is permanent.",
                { viewModel.deleteAllFiles() }
            )
            else -> Triple("", "", {})
        }

        ConfirmationDialog(
            title = title,
            text = text,
            confirmText = "Delete",
            onConfirm = {
                action()
                deleteMode = DeleteMode.NONE
                singleItemToDelete = null
            },
            onDismiss = {
                deleteMode = DeleteMode.NONE
                singleItemToDelete = null
            }
        )
    }

    Scaffold(
        topBar = {
            // Contextual Top Bar
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedItems.size} Selected")
                    } else {
                        Text("My Downloads")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = if (isSelectionMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                    titleContentColor = if (isSelectionMode) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                    actionIconContentColor = if (isSelectionMode) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedItems.clear() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Selection")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { deleteMode = DeleteMode.SELECTED }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Selected")
                        }
                    } else {
                        // Only show Delete All if there are actually files
                        if (uiState.files.isNotEmpty()) {
                            IconButton(onClick = { deleteMode = DeleteMode.ALL }) {
                                Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete All")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.files.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Downloads Yet",
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your downloaded videos and music will appear here safe and sound.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // --- FILE LIST ---
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.files) { item ->
                        val isSelected = selectedItems.contains(item)

                        ListItem(
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedItems.remove(item) else selectedItems.add(item)
                                        } else {
                                            viewModel.playFile(item)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            selectedItems.add(item)
                                        }
                                    }
                                )
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else Color.Transparent
                                ),
                            headlineContent = {
                                Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text("${item.file.extension.uppercase()} • ${item.sizeLabel}")
                            },
                            leadingContent = {
                                Box(contentAlignment = Alignment.Center) {
                                    FileThumbnail(
                                        file = item.file,
                                        isAudio = item.isAudio,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    // Overlay checkmark when selected
                                    AnimatedVisibility(visible = isSelected, enter = fadeIn(), exit = fadeOut()) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                // Hide action buttons in selection mode to prevent accidental clicks
                                if (!isSelectionMode) {
                                    Row {
                                        IconButton(onClick = { viewModel.playFile(item) }) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = "play", Modifier.size(34.dp))
                                        }

                                        IconButton(onClick = { viewModel.shareFile(item) }) {
                                            Icon(Icons.Rounded.Share, contentDescription = "Share")
                                        }
                                        IconButton(onClick = {
                                            singleItemToDelete = item
                                            deleteMode = DeleteMode.SINGLE
                                        }) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                } else {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null // Handled by ListItem click
                                    )
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}