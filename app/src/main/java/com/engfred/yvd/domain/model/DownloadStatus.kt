package com.engfred.yvd.domain.model

import java.io.File

sealed class DownloadStatus {
    data class Progress(val progress: Float, val text: String) : DownloadStatus()
    data class Success(val file: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}