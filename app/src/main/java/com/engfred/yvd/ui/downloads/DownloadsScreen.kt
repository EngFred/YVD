package com.engfred.yvd.ui.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.engfred.yvd.domain.model.DownloadItem
import com.engfred.yvd.ui.components.ConfirmationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloadItems by viewModel.files.collectAsState()

    // State to track which item the user wants to delete
    var itemToDelete by remember { mutableStateOf<DownloadItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadFiles()
    }

    if (itemToDelete != null) {
        ConfirmationDialog(
            title = "Delete File?",
            text = "Are you sure you want to delete '${itemToDelete?.fileName}'? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                itemToDelete?.let { viewModel.deleteFile(it) }
                itemToDelete = null
            },
            onDismiss = {
                itemToDelete = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Downloads") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (downloadItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No downloads yet")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                // Iterating over efficient data class
                items(downloadItems) { item ->
                    ListItem(
                        headlineContent = {
                            Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        // No calculations here! Just reading the string.
                        supportingContent = {
                            Text("${item.file.extension.uppercase()} • ${item.sizeLabel}")
                        },
                        leadingContent = {
                            Icon(
                                if (item.isAudio) Icons.Rounded.Audiotrack else Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.playFile(item) }) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                                }
                                IconButton(onClick = { viewModel.shareFile(item) }) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Share")
                                }
                                IconButton(onClick = {
                                    itemToDelete = item
                                }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}