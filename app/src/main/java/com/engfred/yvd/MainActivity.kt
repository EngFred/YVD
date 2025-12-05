package com.engfred.yvd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.engfred.yvd.domain.model.AppTheme
import com.engfred.yvd.ui.MainScreen
import com.engfred.yvd.ui.theme.YVDTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splashScreen.setKeepOnScreenCondition {
            viewModel.theme.value == null
        }

        setContent {
            val appTheme by viewModel.theme.collectAsState()

            // Because of setKeepOnScreenCondition, appTheme will likely be non-null here.
            // But we handle null safely just in case.
            if (appTheme != null) {
                val useDarkTheme = when (appTheme) {
                    AppTheme.LIGHT -> false
                    AppTheme.DARK -> true
                    else -> isSystemInDarkTheme()
                }

                YVDTheme(darkTheme = useDarkTheme) {
                    MainScreen()
                }
            }
        }
    }
}