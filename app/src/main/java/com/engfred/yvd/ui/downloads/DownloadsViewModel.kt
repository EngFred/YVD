package com.engfred.yvd.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engfred.yvd.data.repository.DownloadsRepository
import com.engfred.yvd.domain.model.DownloadItem
import com.engfred.yvd.util.MediaHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// UI State to manage data and loading status
data class DownloadsUiState(
    val files: List<DownloadItem> = emptyList()
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadsRepository,
    private val mediaHelper: MediaHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState = _uiState.asStateFlow()

    fun loadFiles() {
        viewModelScope.launch {
            val files = repository.getDownloadedFiles()
            _uiState.value = DownloadsUiState(
                files = files
            )
        }
    }

    fun playFile(item: DownloadItem) {
        try {
            mediaHelper.openMediaFile(item.file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareFile(item: DownloadItem) {
        try {
            mediaHelper.shareMediaFile(item.file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Handles single file deletion
    fun deleteFile(item: DownloadItem) {
        if(item.file.exists()) {
            item.file.delete()
            loadFiles() // Refresh list
        }
    }

    // Handles batch deletion
    fun deleteFiles(items: List<DownloadItem>) {
        viewModelScope.launch {
            items.forEach { item ->
                if(item.file.exists()) {
                    item.file.delete()
                }
            }
            loadFiles()
        }
    }

    // Handles "Delete All"
    fun deleteAllFiles() {
        val currentList = _uiState.value.files
        deleteFiles(currentList)
    }
}