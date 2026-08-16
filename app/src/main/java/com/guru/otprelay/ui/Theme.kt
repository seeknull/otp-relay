package com.guru.otprelay.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF1D3557)
private val Red = Color(0xFFE63946)

private val Light = lightColorScheme(primary = Navy, error = Red)
private val Dark = darkColorScheme(primary = Color(0xFF9BB8E8), error = Red)

@Composable
fun OtpRelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
