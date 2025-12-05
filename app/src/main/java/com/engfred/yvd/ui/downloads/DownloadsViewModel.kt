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

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadsRepository,
    private val mediaHelper: MediaHelper
) : ViewModel() {

    // Now holds DownloadItem instead of raw File
    private val _files = MutableStateFlow<List<DownloadItem>>(emptyList())
    val files = _files.asStateFlow()

    fun loadFiles() {
        viewModelScope.launch {
            // This is now safe to call from Main, as the repo handles the thread switching
            _files.value = repository.getDownloadedFiles()
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

    fun deleteFile(item: DownloadItem) {
        if(item.file.exists()) {
            item.file.delete()
            loadFiles() // Refresh list to update UI
        }
    }
}