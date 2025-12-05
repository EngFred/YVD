package com.engfred.yvd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.engfred.yvd.domain.model.AppTheme
import com.engfred.yvd.domain.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val themeRepository: ThemeRepository
) : ViewModel() {

    private val _theme = MutableStateFlow<AppTheme?>(null)
    val theme = _theme.asStateFlow()

    init {
        viewModelScope.launch {
            themeRepository.theme.collect {
                _theme.value = it
            }
        }
    }
}