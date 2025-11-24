package com.engfred.yvd.ui.home

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engfred.yvd.common.Resource
import com.engfred.yvd.data.repository.DownloadStatus
import com.engfred.yvd.domain.model.VideoMetadata
import com.engfred.yvd.domain.repository.YoutubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class HomeState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val videoMetadata: VideoMetadata? = null,
    val downloadProgress: Float = 0f,
    val downloadStatusText: String = "",
    val isDownloading: Boolean = false,
    val downloadComplete: Boolean = false,
    val downloadedFile: File? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YoutubeRepository
) : ViewModel() {

    private val TAG = "YVD_VM"

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    fun onUrlChanged() {
        Log.d(TAG, "🔄 URL changed - resetting UI state")
        _state.value = _state.value.copy(
            videoMetadata = null,
            error = null,
            downloadComplete = false,
            downloadedFile = null
        )
    }

    fun clearError() {
        Log.d(TAG, "🧹 Clearing error state")
        _state.value = _state.value.copy(error = null)
    }

    fun loadVideoInfo(url: String) {
        if (url.isBlank()) {
            Log.w(TAG, "⚠️ loadVideoInfo called with blank URL")
            return
        }

        Log.d(TAG, "")
        Log.d(TAG, "========================================")
        Log.d(TAG, "📥 LOADING VIDEO INFO")
        Log.d(TAG, "========================================")
        Log.d(TAG, "🔗 URL: $url")
        Log.d(TAG, "========================================")

        // Reset download state
        _state.value = _state.value.copy(
            downloadComplete = false,
            downloadedFile = null,
            downloadProgress = 0f,
            downloadStatusText = "",
            error = null,
            isDownloading = false
        )

        viewModelScope.launch {
            repository.getVideoMetadata(url).onEach { result ->
                when (result) {
                    is Resource.Loading -> {
                        Log.d(TAG, "⏳ Loading video metadata...")
                        _state.value = _state.value.copy(isLoading = true, error = null)
                    }
                    is Resource.Success -> {
                        Log.d(TAG, "✅ Video metadata loaded successfully")
                        Log.d(TAG, "📹 Title: ${result.data?.title}")
                        Log.d(TAG, "🎯 Available formats: ${result.data?.formats?.size}")
                        _state.value = _state.value.copy(isLoading = false, videoMetadata = result.data)
                    }
                    is Resource.Error -> {
                        Log.e(TAG, "❌ Error loading metadata: ${result.message}")
                        _state.value = _state.value.copy(isLoading = false, error = result.message)
                    }
                }
            }.launchIn(this)
        }
    }

    fun downloadVideo(url: String, formatId: String) {
        val title = _state.value.videoMetadata?.title ?: "video"

        Log.d(TAG, "")
        Log.d(TAG, "========================================")
        Log.d(TAG, "🎬 DOWNLOAD INITIATED FROM UI")
        Log.d(TAG, "========================================")
        Log.d(TAG, "📹 Title: $title")
        Log.d(TAG, "🎯 Format: $formatId")
        Log.d(TAG, "========================================")

        // IMMEDIATELY set isDownloading to true with initial progress
        _state.value = _state.value.copy(
            isDownloading = true,
            downloadProgress = 0f,
            downloadStatusText = "Starting download...",
            downloadComplete = false,
            error = null
        )
        Log.d(TAG, "✅ UI state updated - isDownloading = true")

        viewModelScope.launch {
            Log.d(TAG, "🚀 Launching download coroutine...")

            repository.downloadVideo(url, formatId, title).onEach { status ->
                when (status) {
                    is DownloadStatus.Progress -> {
                        Log.d(TAG, "📊 VM received Progress: ${status.progress}% - ${status.text}")
                        _state.value = _state.value.copy(
                            isDownloading = true,
                            downloadProgress = status.progress,
                            downloadStatusText = status.text,
                            error = null
                        )
                    }
                    is DownloadStatus.Success -> {
                        Log.d(TAG, "")
                        Log.d(TAG, "========================================")
                        Log.d(TAG, "✅ VM RECEIVED SUCCESS")
                        Log.d(TAG, "========================================")
                        Log.d(TAG, "📄 File: ${status.file.name}")
                        Log.d(TAG, "📦 Size: ${status.file.length() / 1024 / 1024} MB")
                        Log.d(TAG, "========================================")
                        Log.d(TAG, "")

                        _state.value = _state.value.copy(
                            isDownloading = false,
                            downloadComplete = true,
                            downloadProgress = 100f,
                            downloadStatusText = "Download Complete",
                            downloadedFile = status.file
                        )
                    }
                    is DownloadStatus.Error -> {
                        Log.e(TAG, "")
                        Log.e(TAG, "========================================")
                        Log.e(TAG, "❌ VM RECEIVED ERROR")
                        Log.e(TAG, "========================================")
                        Log.e(TAG, "Error: ${status.message}")
                        Log.e(TAG, "========================================")
                        Log.e(TAG, "")

                        _state.value = _state.value.copy(
                            isDownloading = false,
                            error = status.message
                        )
                    }
                }
            }.launchIn(this)

            Log.d(TAG, "✅ Download flow collection started")
        }
    }

    fun openVideoFile(context: Context) {
        val file = _state.value.downloadedFile

        if (file == null) {
            Log.w(TAG, "⚠️ openVideoFile called but no file available")
            return
        }

        Log.d(TAG, "▶️ Opening video file: ${file.name}")

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            Log.d(TAG, "📍 File URI: $uri")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Log.d(TAG, "✅ Video player launched successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening video player", e)
            _state.value = _state.value.copy(error = "Could not open video player: ${e.message}")
        }
    }
}