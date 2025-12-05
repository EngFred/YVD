package com.engfred.yvd.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engfred.yvd.data.repository.DownloadsRepository
import com.engfred.yvd.util.MediaHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadsRepository,
    private val mediaHelper: MediaHelper
) : ViewModel() {

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files = _files.asStateFlow()

    fun loadFiles() {
        viewModelScope.launch {
            _files.value = repository.getDownloadedFiles()
        }
    }

    fun playFile(file: File) {
        try {
            mediaHelper.openMediaFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareFile(file: File) {
        try {
            mediaHelper.shareMediaFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteFile(file: File) {
        if(file.exists()) {
            file.delete()
            loadFiles() // Refresh list
        }
    }
}