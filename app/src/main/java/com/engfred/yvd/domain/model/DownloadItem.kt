package com.engfred.yvd.domain.model

import java.io.File

/**
 * A lightweight wrapper around the File object.
 * We pre-calculate the size label and audio status here so the UI
 * doesn't have to perform disk I/O during the drawing phase.
 */
data class DownloadItem(
    val file: File,
    val fileName: String,
    val sizeLabel: String, // e.g., "24 MB"
    val isAudio: Boolean,
    val lastModified: Long
)