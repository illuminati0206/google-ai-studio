package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AtelierLightColorScheme = lightColorScheme(
    primary = AtelierTerracotta,
    onPrimary = AtelierLinenSurface,
    primaryContainer = AtelierTerracottaContainer,
    onPrimaryContainer = AtelierOnTerracottaContainer,
    secondary = AtelierChampagneGold,
    onSecondary = AtelierObsidian,
    secondaryContainer = AtelierGoldContainer,
    onSecondaryContainer = AtelierOnGoldContainer,
    tertiary = AtelierBotanicalSage,
    onTertiary = AtelierLinenSurface,
    tertiaryContainer = AtelierSageContainer,
    onTertiaryContainer = AtelierOnSageContainer,
    background = AtelierAlabaster,
    onBackground = AtelierEspressoInk,
    surface = AtelierLinenSurface,
    onSurface = AtelierEspressoInk,
    surfaceVariant = AtelierSandstone,
    onSurfaceVariant = AtelierTaupeSecondary,
    surfaceContainerLowest = AtelierLinenSurface,
    surfaceContainerLow = AtelierAlabaster,
    surfaceContainer = AtelierSandstone,
    surfaceContainerHigh = AtelierWarmStone,
    outline = AtelierHairline,
    outlineVariant = AtelierWarmStone,
    inverseSurface = AtelierObsidian,
    inverseOnSurface = AtelierAlabaster,
    error = AtelierError,
    errorContainer = AtelierErrorContainer
)

private val AtelierDarkColorScheme = darkColorScheme(
    primary = AtelierDarkTerracotta,
    onPrimary = AtelierObsidian,
    primaryContainer = AtelierDarkTerracottaContainer,
    onPrimaryContainer = AtelierDarkOnSurface,
    secondary = AtelierDarkGold,
    onSecondary = AtelierObsidian,
    secondaryContainer = AtelierDarkGoldContainer,
    onSecondaryContainer = AtelierDarkOnSurface,
    tertiary = AtelierDarkSage,
    onTertiary = AtelierObsidian,
    tertiaryContainer = AtelierDarkSageContainer,
    onTertiaryContainer = AtelierDarkOnSurface,
    background = AtelierDarkCanvas,
    onBackground = AtelierDarkOnSurface,
    surface = AtelierDarkSurface,
    onSurface = AtelierDarkOnSurface,
    surfaceVariant = AtelierDarkSurfaceHigh,
    onSurfaceVariant = AtelierDarkMuted,
    surfaceContainerLowest = AtelierDarkCanvas,
    surfaceContainerLow = AtelierDarkSurface,
    surfaceContainer = AtelierDarkSurfaceHigh,
    surfaceContainerHigh = ColorTokens.DarkElevated,
    outline = AtelierDarkHairline,
    outlineVariant = AtelierDarkSurfaceHigh,
    inverseSurface = AtelierAlabaster,
    inverseOnSurface = AtelierEspressoInk
)

private object ColorTokens {
    val DarkElevated = androidx.compose.ui.graphics.Color(0xFF302B25)
}

val AtelierShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AtelierTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AtelierDarkColorScheme else AtelierLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AtelierTypography,
        shapes = AtelierShapes,
        content = content
    )
}
