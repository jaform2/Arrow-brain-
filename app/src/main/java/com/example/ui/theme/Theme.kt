package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ArrowBrainLightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = PureWhite,
    primaryContainer = SkyBlueSoft,
    onPrimaryContainer = NavyPrimary,
    secondary = NavyPrimary,
    onSecondary = PureWhite,
    secondaryContainer = SlateSurface,
    onSecondaryContainer = NavyPrimary,
    tertiary = GoldStar,
    onTertiary = PureWhite,
    tertiaryContainer = GoldSoft,
    onTertiaryContainer = NavyDeep,
    background = SoftSnowBg,
    onBackground = TextPrimary,
    surface = PureWhite,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurface,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSubtle,
    error = CoralError,
    onError = PureWhite,
    errorContainer = CoralSoft,
    onErrorContainer = CoralError
)

val ArrowBrainShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ArrowBrainTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArrowBrainLightColorScheme,
        typography = ArrowBrainTypography,
        shapes = ArrowBrainShapes,
        content = content
    )
}
