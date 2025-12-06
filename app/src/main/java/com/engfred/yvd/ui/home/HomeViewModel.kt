package com.engfred.yvd.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.engfred.yvd.common.Resource
import com.engfred.yvd.domain.model.AppTheme
import com.engfred.yvd.domain.model.VideoMetadata
import com.engfred.yvd.domain.repository.ThemeRepository
import com.engfred.yvd.domain.repository.YoutubeRepository
import com.engfred.yvd.util.MediaHelper
import com.engfred.yvd.worker.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class HomeState(
    val urlInput: String = "",
    val isFormatDialogVisible: Boolean = false,
    val isCancelDialogVisible: Boolean = false,
    val isThemeDialogVisible: Boolean = false,
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
    private val mediaHelper: MediaHelper,
    private val themeRepository: ThemeRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    val currentTheme = themeRepository.theme.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppTheme.SYSTEM
    )

    private var currentWorkId: UUID? = null

    // --- UI Interactions ---

    fun onUrlInputChanged(newUrl: String) {
        _state.update {
            it.copy(
                urlInput = newUrl,
                videoMetadata = if (newUrl.isBlank()) null else it.videoMetadata,
                downloadComplete = false,
                downloadedFile = null,
                isDownloading = false,
                downloadProgress = 0f,
                error = null
            )
        }
    }

    fun showFormatDialog() {
        if (_state.value.videoMetadata != null) {
            _state.update { it.copy(isFormatDialogVisible = true) }
        }
    }

    fun hideFormatDialog() { _state.update { it.copy(isFormatDialogVisible = false) } }

    fun showCancelDialog() {
        if (_state.value.isDownloading) {
            _state.update { it.copy(isCancelDialogVisible = true) }
        }
    }

    fun hideCancelDialog() { _state.update { it.copy(isCancelDialogVisible = false) } }

    fun showThemeDialog() { _state.update { it.copy(isThemeDialogVisible = true) } }

    fun hideThemeDialog() { _state.update { it.copy(isThemeDialogVisible = false) } }

    fun updateTheme(newTheme: AppTheme) {
        viewModelScope.launch {
            themeRepository.setTheme(newTheme)
            hideThemeDialog()
        }
    }

    fun clearError() { _state.update { it.copy(error = null) } }

    // --- Data Loading ---

    fun loadVideoInfo(url: String) {
        if (url.isBlank()) return
        if (url != _state.value.urlInput) onUrlInputChanged(url)

        _state.update { it.copy(isLoading = true, error = null, videoMetadata = null) }

        viewModelScope.launch {
            repository.getVideoMetadata(url).onEach { result ->
                when (result) {
                    is Resource.Loading -> _state.update { it.copy(isLoading = true) }
                    is Resource.Success -> _state.update { it.copy(isLoading = false, videoMetadata = result.data) }
                    is Resource.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                }
            }.launchIn(this)
        }
    }

    // --- Downloading Logic ---

    fun downloadMedia(formatId: String, isAudio: Boolean) {
        val currentState = _state.value
        val url = currentState.urlInput
        val title = currentState.videoMetadata?.title ?: "video"

        _state.update {
            it.copy(
                isDownloading = true,
                downloadProgress = 0f,
                downloadStatusText = "Initializing...",
                downloadComplete = false,
                error = null,
                isAudio = isAudio,
                isFormatDialogVisible = false
            )
        }

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
            .addTag("download_job") // Tag is crucial for finding it later
            .build()

        currentWorkId = downloadRequest.id

        workManager.enqueue(downloadRequest)
        observeWork(downloadRequest.id)
    }

    fun cancelDownload() {
        currentWorkId?.let { id ->
            workManager.cancelWorkById(id)
            _state.update {
                it.copy(
                    isDownloading = false,
                    downloadStatusText = "Cancelled",
                    isCancelDialogVisible = false
                )
            }
        }
    }

    private fun observeWork(id: UUID) {
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).collect { workInfo ->
                if (workInfo == null) return@collect

                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED -> {
                        _state.update { it.copy(isDownloading = true, downloadStatusText = "Pending...") }
                    }
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getFloat("progress", 0f)
                        val status = workInfo.progress.getString("status") ?: "Downloading..."
                        _state.update {
                            it.copy(isDownloading = true, downloadProgress = progress, downloadStatusText = status)
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val path = workInfo.outputData.getString("filePath")
                        val file = if (path != null) File(path) else null
                        _state.update {
                            it.copy(
                                isDownloading = false,
                                downloadComplete = true,
                                downloadProgress = 100f,
                                downloadedFile = file,
                                downloadStatusText = "Download Complete"
                            )
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val errorMsg = workInfo.outputData.getString("error") ?: "Download Failed"
                        _state.update {
                            it.copy(isDownloading = false, error = errorMsg, downloadStatusText = "")
                        }
                    }
                    WorkInfo.State.CANCELLED -> {
                        _state.update {
                            it.copy(isDownloading = false, downloadStatusText = "Download Cancelled")
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun openMediaFile() {
        val file = _state.value.downloadedFile ?: return
        try { mediaHelper.openMediaFile(file) } catch (e: Exception) { _state.update { it.copy(error = e.message) } }
    }

    fun shareMediaFile() {
        val file = _state.value.downloadedFile ?: return
        try { mediaHelper.shareMediaFile(file) } catch (e: Exception) { _state.update { it.copy(error = e.message) } }
    }
}