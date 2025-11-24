package com.engfred.yvd.ui.home

import android.content.Context
import android.content.Intent
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

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    // NEW: Called when user changes text. Resets UI so the button reappears.
    fun onUrlChanged() {
        _state.value = _state.value.copy(
            videoMetadata = null, // Clear the card
            error = null,
            downloadComplete = false,
            downloadedFile = null
        )
    }

    fun loadVideoInfo(url: String) {
        if (url.isBlank()) return

        // Reset download state but KEEP videoMetadata null for now so loading spinner shows
        _state.value = _state.value.copy(
            downloadComplete = false,
            downloadedFile = null,
            downloadProgress = 0f,
            downloadStatusText = "",
            error = null
        )

        viewModelScope.launch {
            repository.getVideoMetadata(url).onEach { result ->
                when (result) {
                    is Resource.Loading -> _state.value = _state.value.copy(isLoading = true, error = null)
                    is Resource.Success -> _state.value = _state.value.copy(isLoading = false, videoMetadata = result.data)
                    is Resource.Error -> _state.value = _state.value.copy(isLoading = false, error = result.message)
                }
            }.launchIn(this)
        }
    }

    fun downloadVideo(url: String, formatId: String) {
        val title = _state.value.videoMetadata?.title ?: "video"

        viewModelScope.launch {
            repository.downloadVideo(url, formatId, title).onEach { status ->
                when (status) {
                    is DownloadStatus.Progress -> {
                        _state.value = _state.value.copy(
                            isDownloading = true,
                            downloadProgress = status.progress,
                            downloadStatusText = status.text,
                            error = null
                        )
                    }
                    is DownloadStatus.Success -> {
                        _state.value = _state.value.copy(
                            isDownloading = false,
                            downloadComplete = true,
                            downloadProgress = 100f,
                            downloadStatusText = "Download Complete",
                            downloadedFile = status.file
                        )
                    }
                    is DownloadStatus.Error -> {
                        _state.value = _state.value.copy(
                            isDownloading = false,
                            error = status.message
                        )
                    }
                }
            }.launchIn(this)
        }
    }

    fun openVideoFile(context: Context) {
        val file = _state.value.downloadedFile ?: return
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = "Could not open video player: ${e.message}")
        }
    }
}