package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.domain.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = WarmAmberSecondary,
    onSecondary = Color.White,
    secondaryContainer = WarmAmberContainer,
    onSecondaryContainer = OnWarmAmberContainer,
    tertiary = EmeraldTertiary,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = AlabasterBackground,
    onBackground = InkOnSurface,
    surface = CrispSurface,
    onSurface = InkOnSurface,
    surfaceVariant = SoftPaperVariant,
    onSurfaceVariant = MutedInk,
    outline = SubtleOutline,
    outlineVariant = SubtleOutline,
    error = RatingAgainColor,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryLight,
    onPrimary = Color(0xFF0A154A),
    primaryContainer = Color(0xFF1B2B6B),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF3E2200),
    secondaryContainer = Color(0xFF573500),
    onSecondaryContainer = Color(0xFFFFE5B4),
    tertiary = Color(0xFF34D399),
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF044E36),
    onTertiaryContainer = Color(0xFFBBF7D0),
    background = NightBackground,
    onBackground = NightOnSurface,
    surface = NightSurface,
    onSurface = NightOnSurface,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = NightMutedInk,
    outline = NightOutline,
    outlineVariant = NightOutline,
    error = Color(0xFFFB7185),
    onError = Color(0xFF4C0519)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FlashCraftTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
