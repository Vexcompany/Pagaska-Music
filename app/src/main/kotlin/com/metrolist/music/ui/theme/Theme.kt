/**
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = NeoBrutalismPalette.Yellow

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val baseColorScheme = rememberDynamicColorScheme(
        seedColor = themeColor,
        isDark = darkTheme,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = PaletteStyle.TonalSpot,
    )

    // Pagaska's Neo Brutalism palette is intentionally independent of wallpaper
    // colors. System mode changes only the light/dark surface treatment.
    val brutalScheme = remember(baseColorScheme, darkTheme) {
        baseColorScheme.copy(
            primary = NeoBrutalismPalette.Yellow,
            onPrimary = Color(0xFF111111),
            primaryContainer = Color(0xFFE6D82F),
            onPrimaryContainer = Color(0xFF111111),
            secondary = NeoBrutalismPalette.Lime,
            onSecondary = Color(0xFF111111),
            secondaryContainer = Color(0xFF9BD83D),
            onSecondaryContainer = Color(0xFF111111),
            tertiary = NeoBrutalismPalette.Pink,
            onTertiary = Color(0xFF111111),
            surface = if (darkTheme) NeoBrutalismPalette.PaperDark else NeoBrutalismPalette.White,
            surfaceDim = if (darkTheme) Color(0xFF101010) else NeoBrutalismPalette.Muted,
            surfaceBright = if (darkTheme) Color(0xFF292929) else NeoBrutalismPalette.White,
            surfaceContainerLowest = if (darkTheme) Color(0xFF0B0B0B) else NeoBrutalismPalette.Panel,
            surfaceContainerLow = if (darkTheme) Color(0xFF141414) else Color(0xFFF8F5EA),
            surfaceContainer = if (darkTheme) Color(0xFF1B1B1B) else Color(0xFFF1EEE3),
            surfaceContainerHigh = if (darkTheme) NeoBrutalismPalette.PanelDark else Color(0xFFEAE7DC),
            surfaceContainerHighest = if (darkTheme) NeoBrutalismPalette.MutedDark else Color(0xFFE2DFD4),
            background = if (darkTheme) Color(0xFF101010) else NeoBrutalismPalette.Paper,
            onSurface = if (darkTheme) Color(0xFFF8F6E9) else NeoBrutalismPalette.Ink,
            onSurfaceVariant = if (darkTheme) Color(0xFFD0CEC5) else Color(0xFF45443F),
            onBackground = if (darkTheme) Color(0xFFF8F6E9) else NeoBrutalismPalette.Ink,
            outline = if (darkTheme) Color(0xFFB8B5AA) else NeoBrutalismPalette.Ink,
            outlineVariant = if (darkTheme) Color(0xFF55534C) else Color(0xFF77746B),
        )
    }

    // Apply pureBlack modification if needed, similar to original logic
    val colorScheme = remember(brutalScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) brutalScheme.pureBlack(true) else brutalScheme
    }

    // Use standard MaterialTheme instead of MaterialExpressiveTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        ),
        content = content
    )
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
