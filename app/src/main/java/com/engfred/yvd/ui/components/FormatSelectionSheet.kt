package com.engfred.yvd.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.engfred.yvd.domain.model.VideoMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSelectionSheet(
    metadata: VideoMetadata,
    onDismiss: () -> Unit,
    onFormatSelected: (formatId: String, isAudio: Boolean) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
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

            if (selectedTab == 0) {
                // VIDEO LIST
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(metadata.videoFormats) { format ->
                        ListItem(
                            headlineContent = { Text(format.resolution) },
                            supportingContent = { Text("${format.ext.uppercase()} • ${format.fileSize}") },
                            leadingContent = { Icon(Icons.Rounded.Movie, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                onFormatSelected(format.formatId, false)
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            } else {
                // AUDIO LIST
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(metadata.audioFormats) { format ->
                        ListItem(
                            headlineContent = { Text(format.bitrate) },
                            supportingContent = { Text("${format.ext.uppercase()} • ${format.fileSize}") },
                            leadingContent = { Icon(Icons.Rounded.Audiotrack, null, tint = MaterialTheme.colorScheme.secondary) },
                            trailingContent = { Icon(Icons.Rounded.Download, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                onFormatSelected(format.formatId, true)
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}