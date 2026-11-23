package com.moonsuivi.app.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.moonsuivi.app.data.Prefs

/** Thème choisi : suit le téléphone, toujours clair, ou toujours sombre. */
enum class ModeTheme(val label: String) {
    SYSTEME("Comme le téléphone"),
    CLAIR("Clair"),
    SOMBRE("Sombre")
}

object Apparence {
    val mode = mutableStateOf(ModeTheme.SYSTEME)

    fun charger(ctx: Context) {
        mode.value = runCatching { ModeTheme.valueOf(Prefs.theme(ctx)) }.getOrDefault(ModeTheme.SYSTEME)
    }

    fun changer(ctx: Context, m: ModeTheme) {
        mode.value = m
        Prefs.setTheme(ctx, m.name)
    }

    @Composable
    fun estSombre(): Boolean = when (mode.value) {
        ModeTheme.SOMBRE -> true
        ModeTheme.CLAIR -> false
        ModeTheme.SYSTEME -> isSystemInDarkTheme()
    }
}

private val CouleursClaires = lightColorScheme(
    primary = Color(0xFF3F51B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F0EA),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFF57C00),
    tertiaryContainer = Color(0xFFFFF8E1),
    onTertiaryContainer = Color(0xFF4E342E),
    surfaceVariant = Color(0xFFECEEF6)
)

private val CouleursSombres = darkColorScheme(
    primary = Color(0xFFAAB4FF),
    onPrimary = Color(0xFF0B1A63),
    primaryContainer = Color(0xFF2C3A8C),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF1F4D47),
    onSecondaryContainer = Color(0xFFD0F0EA),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF3E2500),
    tertiaryContainer = Color(0xFF4A3A1E),
    onTertiaryContainer = Color(0xFFFFE0B2),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF2A2D38),
    onSurfaceVariant = Color(0xFFC5C6D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun MoonSuiviTheme(sombre: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (sombre) CouleursSombres else CouleursClaires, content = content)
}
