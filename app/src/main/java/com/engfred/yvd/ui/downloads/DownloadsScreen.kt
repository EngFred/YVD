package com.engfred.yvd.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.engfred.yvd.domain.model.DownloadItem
import com.engfred.yvd.ui.components.ConfirmationDialog
import com.engfred.yvd.ui.components.FileThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val downloadItems by viewModel.files.collectAsState()

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
            onDismiss = { itemToDelete = null }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No downloads yet")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(downloadItems) { item ->
                    ListItem(
                        headlineContent = {
                            Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text("${item.file.extension.uppercase()} • ${item.sizeLabel}")
                        },
                        leadingContent = {
                            FileThumbnail(
                                file = item.file,
                                isAudio = item.isAudio,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
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
                                IconButton(onClick = { itemToDelete = item }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
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