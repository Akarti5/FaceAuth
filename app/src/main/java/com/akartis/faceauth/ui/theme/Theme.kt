package com.akartis.faceauth.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FaceAuthDarkColorScheme = darkColorScheme(
    primary = FaceAuthGreen,
    onPrimary = Color.Black,
    secondary = FaceAuthGreen,
    onSecondary = Color.Black,
    tertiary = FaceAuthGreen,
    background = FaceAuthBackground,
    onBackground = FaceAuthTextPrimary,
    surface = FaceAuthSurface,
    onSurface = FaceAuthTextPrimary,
    surfaceVariant = FaceAuthSurface,
    onSurfaceVariant = FaceAuthTextSecondary,
    outline = FaceAuthBorder,
    error = FaceAuthError,
    onError = Color.White
)

@Composable
fun FaceAuthTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FaceAuthDarkColorScheme,
        typography = Typography,
        content = content
    )
}
