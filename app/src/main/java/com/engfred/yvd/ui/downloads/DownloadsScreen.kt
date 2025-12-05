package com.engfred.yvd.ui.downloads

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.engfred.yvd.domain.model.DownloadItem
import com.engfred.yvd.ui.components.ConfirmationDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(padding), contentAlignment = Alignment.Center) {
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
                            // Custom Thumbnail Component
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

/**
 * A smart thumbnail loader.
 * - If Video: Uses Coil's VideoFrameDecoder to load a frame from the File.
 * - If Audio: Uses MediaMetadataRetriever to extract embedded art. Fallback to Icon.
 */
@Composable
fun FileThumbnail(
    file: File,
    isAudio: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // State for Audio Album Art (ByteArray)
    // For Video, we pass the file directly to Coil, so we don't need this state
    var audioArtworkData by remember { mutableStateOf<ByteArray?>(null) }
    var isAudioArtLoaded by remember { mutableStateOf(false) }

    // If it's an audio file, extract metadata in the background
    LaunchedEffect(file, isAudio) {
        if (isAudio) {
            withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    audioArtworkData = retriever.embeddedPicture
                    retriever.release()
                } catch (e: Exception) {
                    audioArtworkData = null
                }
                isAudioArtLoaded = true
            }
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!isAudio) {
            // --- VIDEO THUMBNAIL ---
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(file)
                    // Important: Use VideoFrameDecoder for video files
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .crossfade(true)
                    .build(),
                contentDescription = "Video Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // --- AUDIO ARTWORK ---
            if (isAudioArtLoaded && audioArtworkData != null) {
                // If we successfully found album art
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(audioArtworkData)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Default fallback for Audio (or while loading)
                Icon(
                    imageVector = Icons.Rounded.Audiotrack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp) // Add padding so icon isn't huge
                )
            }
        }
    }
}