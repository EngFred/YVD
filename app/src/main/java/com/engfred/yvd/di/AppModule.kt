package com.engfred.yvd.di

import android.content.Context
import androidx.work.WorkManager
import com.engfred.yvd.data.repository.ThemeRepositoryImpl
import com.engfred.yvd.data.repository.YoutubeRepositoryImpl
import com.engfred.yvd.domain.repository.ThemeRepository
import com.engfred.yvd.domain.repository.YoutubeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideYoutubeRepository(
        @ApplicationContext context: Context
    ): YoutubeRepository {
        return YoutubeRepositoryImpl(context)
    }

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideThemeRepository(
        @ApplicationContext context: Context
    ): ThemeRepository {
        return ThemeRepositoryImpl(context)
    }
}