package com.engfred.yvd.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engfred.yvd.data.repository.DownloadsRepository
import com.engfred.yvd.domain.model.DownloadItem
import com.engfred.yvd.util.MediaHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DeleteMode {
    NONE, SINGLE, SELECTED, ALL
}

data class DownloadsUiState(
    val files: List<DownloadItem> = emptyList(),
    val selectedItems: Set<DownloadItem> = emptySet(),
    val deleteMode: DeleteMode = DeleteMode.NONE,
    val singleItemToDelete: DownloadItem? = null
) {
    val isSelectionMode: Boolean
        get() = selectedItems.isNotEmpty()
}

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
            _uiState.update { it.copy(files = files) }
        }
    }

    // --- Selection Logic ---

    fun toggleSelection(item: DownloadItem) {
        _uiState.update { currentState ->
            val newSelection = currentState.selectedItems.toMutableSet()
            if (newSelection.contains(item)) {
                newSelection.remove(item)
            } else {
                newSelection.add(item)
            }
            currentState.copy(selectedItems = newSelection)
        }
    }

    fun selectSingleItemForLongPress(item: DownloadItem) {
        _uiState.update { it.copy(selectedItems = setOf(item)) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedItems = emptySet()) }
    }

    // --- Media Actions ---

    fun playFile(item: DownloadItem) {
        if (_uiState.value.isSelectionMode) return
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

    // --- Deletion Logic ---

    fun showDeleteSingleDialog(item: DownloadItem) {
        _uiState.update {
            it.copy(
                deleteMode = DeleteMode.SINGLE,
                singleItemToDelete = item
            )
        }
    }

    fun showDeleteSelectedDialog() {
        if (_uiState.value.selectedItems.isNotEmpty()) {
            _uiState.update { it.copy(deleteMode = DeleteMode.SELECTED) }
        }
    }

    fun showDeleteAllDialog() {
        if (_uiState.value.files.isNotEmpty()) {
            _uiState.update { it.copy(deleteMode = DeleteMode.ALL) }
        }
    }

    fun dismissDeleteDialog() {
        _uiState.update {
            it.copy(
                deleteMode = DeleteMode.NONE,
                singleItemToDelete = null
            )
        }
    }

    fun confirmDelete() {
        val currentState = _uiState.value
        when (currentState.deleteMode) {
            DeleteMode.SINGLE -> {
                currentState.singleItemToDelete?.let { deleteFileInternal(it) }
            }
            DeleteMode.SELECTED -> {
                deleteFilesInternal(currentState.selectedItems.toList())
                clearSelection()
            }
            DeleteMode.ALL -> {
                deleteFilesInternal(currentState.files)
                clearSelection()
            }
            DeleteMode.NONE -> { /* Do nothing */ }
        }
        dismissDeleteDialog()
    }

    private fun deleteFileInternal(item: DownloadItem) {
        if (item.file.exists()) {
            item.file.delete()
            loadFiles()
        }
    }

    private fun deleteFilesInternal(items: List<DownloadItem>) {
        viewModelScope.launch {
            items.forEach { item ->
                if (item.file.exists()) {
                    item.file.delete()
                }
            }
            loadFiles()
        }
    }
}