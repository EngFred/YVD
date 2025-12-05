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
    val isAudio: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YoutubeRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()
    private val workManager = WorkManager.getInstance(context)

    // Track current worker ID to enable cancellation
    private var currentWorkId: UUID? = null

    fun onUrlChanged() {
        _state.value = _state.value.copy(
            videoMetadata = null,
            error = null,
            downloadComplete = false,
            downloadedFile = null,
            isDownloading = false,
            downloadProgress = 0f
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

        // Reset state for new download
        _state.value = _state.value.copy(
            isDownloading = true,
            downloadProgress = 0f,
            downloadStatusText = "Initializing...",
            downloadComplete = false,
            error = null,
            isAudio = isAudio
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

        // Save ID for cancellation
        currentWorkId = downloadRequest.id

        workManager.enqueue(downloadRequest)
        observeWork(downloadRequest.id)
    }

    fun cancelDownload() {
        currentWorkId?.let { id ->
            workManager.cancelWorkById(id)
            _state.value = _state.value.copy(
                isDownloading = false,
                downloadStatusText = "Cancelled"
            )
        }
    }

    private fun observeWork(id: UUID) {
        workManager.getWorkInfoByIdLiveData(id).observeForever { workInfo ->
            if (workInfo == null) return@observeForever

            when (workInfo.state) {
                WorkInfo.State.ENQUEUED -> {
                    _state.value = _state.value.copy(
                        isDownloading = true,
                        downloadStatusText = "Pending..."
                    )
                }
                WorkInfo.State.RUNNING -> {
                    val progress = workInfo.progress.getFloat("progress", 0f)
                    val status = workInfo.progress.getString("status") ?: "Downloading..."

                    _state.value = _state.value.copy(
                        isDownloading = true,
                        downloadProgress = progress,
                        downloadStatusText = status
                    )
                }
                WorkInfo.State.SUCCEEDED -> {
                    val path = workInfo.outputData.getString("filePath")
                    val file = if (path != null) File(path) else null

                    _state.value = _state.value.copy(
                        isDownloading = false,
                        downloadComplete = true,
                        downloadProgress = 100f,
                        downloadedFile = file,
                        downloadStatusText = "Download Complete"
                    )
                }
                WorkInfo.State.FAILED -> {
                    val errorMsg = workInfo.outputData.getString("error") ?: "Download Failed"
                    _state.value = _state.value.copy(
                        isDownloading = false,
                        error = errorMsg,
                        downloadStatusText = ""
                    )
                }
                WorkInfo.State.CANCELLED -> {
                    _state.value = _state.value.copy(
                        isDownloading = false,
                        downloadStatusText = "Download Cancelled"
                    )
                }
                else -> {}
            }
        }
    }

    fun openMediaFile(context: Context) {
        val file = _state.value.downloadedFile ?: return
        if (!file.exists()) {
            _state.value = _state.value.copy(error = "File not found. It may have been deleted.")
            return
        }

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val extension = file.extension.lowercase()
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

    fun shareMediaFile(context: Context) {
        val file = _state.value.downloadedFile ?: return
        if (!file.exists()) return

        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val extension = file.extension.lowercase()
            val mimeType = when(extension) {
                "m4a", "mp3", "wav", "ogg" -> "audio/*"
                else -> "video/*"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = "Could not share file: ${e.message}")
        }
    }
}