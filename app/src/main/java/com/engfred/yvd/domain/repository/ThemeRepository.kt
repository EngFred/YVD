package com.engfred.yvd.domain.repository


import com.engfred.yvd.domain.model.AppTheme
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    val theme: Flow<AppTheme>
    suspend fun setTheme(theme: AppTheme)
}