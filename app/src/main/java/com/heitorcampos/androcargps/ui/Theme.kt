package com.heitorcampos.androcargps.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Azul = Color(0xFF2F7CF6)
val Verde = Color(0xFF34C77B)
val Amarelo = Color(0xFFF5B341)
val Vermelho = Color(0xFFFF6B6B)
val Cinza = Color(0xFF6B7585)

private val Esquema = darkColorScheme(
    primary = Azul,
    onPrimary = Color.White,
    secondary = Azul,
    onSecondary = Color.White,
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFE8ECF3),
    surface = Color(0xFF151B26),
    onSurface = Color(0xFFE8ECF3),
    surfaceVariant = Color(0xFF1E2633),
    onSurfaceVariant = Color(0xFF9AA5B8),
    outline = Color(0xFF2E3848),
    error = Vermelho,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, content = content)
}
