package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.models.AppSettings
import com.example.models.ThemeMode
import com.example.navigation.MemoDeckAppScaffold
import com.example.theme.MemoDeckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as MemoDeckApplication).container

        setContent {
            val settings by appContainer.settingsRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            val systemDark = isSystemInDarkTheme()
            val useDarkBars = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM_DEFAULT -> systemDark
            }

            LaunchedEffect(useDarkBars) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDarkBars) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (useDarkBars) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            MemoDeckTheme(themeMode = settings.themeMode) {
                MemoDeckAppScaffold(container = appContainer)
            }
        }
    }
}
