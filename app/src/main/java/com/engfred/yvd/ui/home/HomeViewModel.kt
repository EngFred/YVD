package com.engfred.yvd.ui.home

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.engfred.yvd.common.Resource
import com.engfred.yvd.domain.model.VideoMetadata
import com.engfred.yvd.domain.repository.YoutubeRepository
import com.engfred.yvd.worker.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class HomeState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val videoMetadata: VideoMetadata? = null,
    val downloadProgress: Float = 0f,
    val downloadStatusText: String = "",
    val isDownloading: Boolean = false,
    val downloadComplete: Boolean = false,
    val downloadedFile: File? = null,
    val isAudio: Boolean = false // FIX: Added this field to track download type
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YoutubeRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()
    private val workManager = WorkManager.getInstance(context)

    fun onUrlChanged() {
        _state.value = _state.value.copy(
            videoMetadata = null,
            error = null,
            downloadComplete = false,
            downloadedFile = null
        )
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    fun loadVideoInfo(url: String) {
        if (url.isBlank()) return
        _state.value = _state.value.copy(isLoading = true, error = null, videoMetadata = null)

        viewModelScope.launch {
            repository.getVideoMetadata(url).onEach { result ->
                when (result) {
                    is Resource.Loading -> _state.value = _state.value.copy(isLoading = true)
                    is Resource.Success -> _state.value = _state.value.copy(isLoading = false, videoMetadata = result.data)
                    is Resource.Error -> _state.value = _state.value.copy(isLoading = false, error = result.message)
                }
            }.launchIn(this)
        }
    }

    fun downloadMedia(url: String, formatId: String, isAudio: Boolean) {
        val title = _state.value.videoMetadata?.title ?: "video"

        _state.value = _state.value.copy(
            isDownloading = true,
            downloadProgress = 0f,
            downloadStatusText = "Starting ${if(isAudio) "Audio" else "Video"} Download...",
            downloadComplete = false,
            error = null,
            isAudio = isAudio // FIX: Update state so UI knows we are downloading audio
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            "url" to url,
            "formatId" to formatId,
            "title" to title,
            "isAudio" to isAudio
        )

        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("download_job")
            .build()

        workManager.enqueue(downloadRequest)
        observeWork(downloadRequest.id)
    }

    private fun observeWork(id: UUID) {
        workManager.getWorkInfoByIdLiveData(id).observeForever { workInfo ->
            if (workInfo == null) return@observeForever
            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    val progress = workInfo.progress.getFloat("progress", 0f)
                    val status = workInfo.progress.getString("status") ?: "Downloading..."
                    _state.value = _state.value.copy(isDownloading = true, downloadProgress = progress, downloadStatusText = status)
                }
                WorkInfo.State.SUCCEEDED -> {
                    val path = workInfo.outputData.getString("filePath")
                    val file = if (path != null) File(path) else null
                    _state.value = _state.value.copy(isDownloading = false, downloadComplete = true, downloadProgress = 100f, downloadedFile = file, downloadStatusText = "Complete")
                }
                WorkInfo.State.FAILED -> {
                    val errorMsg = workInfo.outputData.getString("error") ?: "Failed"
                    _state.value = _state.value.copy(isDownloading = false, error = errorMsg)
                }
                else -> {}
            }
        }
    }

    fun openMediaFile(context: Context) {
        val file = _state.value.downloadedFile ?: return
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val extension = file.extension.lowercase()
            // Improved MIME type detection
            val mimeType = when(extension) {
                "m4a", "mp3", "wav", "ogg" -> "audio/*"
                else -> "video/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = "Could not open file: ${e.message}")
        }
    }
}