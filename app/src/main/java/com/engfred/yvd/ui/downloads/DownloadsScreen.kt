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
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.engfred.yvd.ui.components.ConfirmationDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val files by viewModel.files.collectAsState()

    // State to track which file the user wants to delete
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadFiles()
    }

    if (fileToDelete != null) {
        ConfirmationDialog(
            title = "Delete File?",
            text = "Are you sure you want to delete '${fileToDelete?.name}'? This cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                fileToDelete?.let { viewModel.deleteFile(it) }
                fileToDelete = null
            },
            onDismiss = {
                fileToDelete = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Downloads") })
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No downloads yet")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(files) { file ->
                    val isAudio = file.extension == "m4a"
                    val fileSizeMb = if (file.length() > 0) file.length() / (1024 * 1024) else 0

                    ListItem(
                        headlineContent = {
                            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text("${file.extension.uppercase()} • ${fileSizeMb}MB") },
                        leadingContent = {
                            Icon(
                                if (isAudio) Icons.Rounded.Audiotrack else Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.playFile(file) }) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                                }
                                IconButton(onClick = { viewModel.shareFile(file) }) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Share")
                                }
                                IconButton(onClick = {
                                    // Trigger the dialog instead of deleting immediately
                                    fileToDelete = file
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