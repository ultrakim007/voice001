package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CrimsonPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = CrimsonContainerLight,
    onPrimaryContainer = OnCrimsonContainerLight,
    secondary = PineSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = PineContainerLight,
    onSecondaryContainer = OnPineContainerLight,
    tertiary = CobaltTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = CobaltContainerLight,
    onTertiaryContainer = OnCobaltContainerLight,
    background = HanjiBackgroundLight,
    onBackground = InkPrimaryLight,
    surface = HanjiSurfaceLight,
    onSurface = InkPrimaryLight,
    surfaceVariant = HanjiContainerLight,
    onSurfaceVariant = InkSecondaryLight,
    surfaceContainerLowest = HanjiCardLight,
    surfaceContainerLow = HanjiCardLight,
    surfaceContainer = HanjiContainerLight,
    surfaceContainerHigh = HanjiContainerHighLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonPrimaryDark,
    onPrimary = Color(0xFF4A0000),
    primaryContainer = CrimsonContainerDark,
    onPrimaryContainer = OnCrimsonContainerDark,
    secondary = PineSecondaryDark,
    onSecondary = Color(0xFF003820),
    secondaryContainer = PineContainerDark,
    onSecondaryContainer = OnPineContainerDark,
    tertiary = CobaltTertiaryDark,
    onTertiary = Color(0xFF002E5C),
    tertiaryContainer = CobaltContainerDark,
    onTertiaryContainer = OnCobaltContainerDark,
    background = HanjiBackgroundDark,
    onBackground = InkPrimaryDark,
    surface = HanjiSurfaceDark,
    onSurface = InkPrimaryDark,
    surfaceVariant = HanjiContainerDark,
    onSurfaceVariant = InkSecondaryDark,
    surfaceContainerLowest = HanjiCardDark,
    surfaceContainerLow = HanjiCardDark,
    surfaceContainer = HanjiContainerDark,
    surfaceContainerHigh = HanjiContainerHighDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = Color(0xFF690005),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun SeniorVoiceMemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
