package com.engfred.yvd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.engfred.yvd.util.MediaHelper
import java.io.File

/**
 * A smart thumbnail loader.
 * Uses MediaHelper to extract art/thumbnails on a background thread.
 */
@Composable
fun FileThumbnail(
    file: File,
    isAudio: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // We store the image data as Any? because Coil handles both Bitmap and ByteArray
    var thumbnailData by remember { mutableStateOf<Any?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(file, isAudio) {
        thumbnailData = if (isAudio) {
            MediaHelper.getAudioArtwork(file)
        } else {
            MediaHelper.getVideoThumbnail(file)
        }
        isLoaded = true
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (isLoaded && thumbnailData != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailData)
                    .crossfade(true)
                    .build(),
                contentDescription = "Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Fallback icons depending on type
            Icon(
                imageVector = if (isAudio) Icons.Rounded.Audiotrack else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp).size(36.dp)
            )
        }
    }
}