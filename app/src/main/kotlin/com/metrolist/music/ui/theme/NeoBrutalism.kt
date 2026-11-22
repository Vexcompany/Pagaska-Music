/**
 * Pagaska Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Shared visual language for Pagaska's Neo Brutalism interface.
 * Keep these tokens independent from wallpaper/system dynamic colors.
 */
package com.metrolist.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object NeoBrutalismPalette {
    val Ink = Color(0xFF171717)
    val Paper = Color(0xFFFFFDF5)
    val PaperDark = Color(0xFF171717)
    val Panel = Color(0xFFFFFFFF)
    val PanelDark = Color(0xFF242424)
    val Muted = Color(0xFFECE9DF)
    val MutedDark = Color(0xFF303030)
    val Yellow = Color(0xFFF5E84B)
    val Lime = Color(0xFFB8F35A)
    val Pink = Color(0xFFFF70A6)
    val Blue = Color(0xFF83C9FF)
    val Orange = Color(0xFFFFA45B)
    val White = Color(0xFFFFFEF8)
}

object NeoBrutalismMetrics {
    val Border: Dp = 2.dp
    val StrongBorder: Dp = 3.dp
    val ShadowOffset: Dp = 4.dp
    val SmallShadowOffset: Dp = 2.dp
    val Corner = 3.dp
    val PanelPadding = 16.dp
    val ElementGap = 12.dp
}

/**
 * Draws a crisp, unblurred offset shadow, then a flat bordered surface.
 * Use on cards, panels and tactile controls; avoid applying it to every row.
 */
fun Modifier.neoPanel(
    surface: Color = NeoBrutalismPalette.Panel,
    outline: Color = NeoBrutalismPalette.Ink,
    shadow: Color = NeoBrutalismPalette.Ink,
    shadowOffset: Dp = NeoBrutalismMetrics.ShadowOffset,
    borderWidth: Dp = NeoBrutalismMetrics.Border,
    cornerRadius: Dp = NeoBrutalismMetrics.Corner,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .drawBehind {
            val offset = shadowOffset.toPx()
            drawRoundRect(
                color = shadow,
                topLeft = Offset(offset, offset),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx())
            )
        }
        .clip(shape)
        .background(surface, shape)
        .border(borderWidth, outline, shape)
}
