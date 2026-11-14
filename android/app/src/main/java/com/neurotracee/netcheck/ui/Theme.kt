package com.neurotracee.netcheck.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.neurotracee.netcheck.model.Levels

/**
 * 配色与桌面端 HTML 报告同一套：同一组色号、同一套语义。
 * 深色模式是额外给安卓的（桌面报告只有浅色），语义色保持相同，只把底与文字反过来。
 */
data class Palette(
    val bg: Color,
    val card: Color,
    val ink: Color,
    val sub: Color,
    val line: Color,
    val good: Color,
    val fair: Color,
    val slow: Color,
    val limit: Color,
    val down: Color,
    val warnBg: Color,
    val warnBorder: Color,
    val warnText: Color,
    val track: Color,
    val accentSoft: Color
) {
    fun level(key: String): Color = when (key) {
        Levels.GOOD -> good
        Levels.FAIR -> fair
        Levels.SLOW -> slow
        Levels.LIMIT -> limit
        Levels.DOWN -> down
        else -> sub
    }
}

val LightPalette = Palette(
    bg = Color(0xFFF4F6F8),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF1A2027),
    sub = Color(0xFF6B7785),
    line = Color(0xFFE5E9EE),
    good = Color(0xFF12A150),
    fair = Color(0xFF3B82C4),
    slow = Color(0xFFC8870C),
    limit = Color(0xFFD9601A),
    down = Color(0xFFD0342C),
    warnBg = Color(0xFFFFF8E8),
    warnBorder = Color(0xFFF0DFB8),
    warnText = Color(0xFF9A6A08),
    track = Color(0xFFEEF1F4),
    accentSoft = Color(0xFFE8F7EE)
)

val DarkPalette = Palette(
    bg = Color(0xFF0F1319),
    card = Color(0xFF171C24),
    ink = Color(0xFFE6EAF0),
    sub = Color(0xFF97A3B2),
    line = Color(0xFF262D38),
    good = Color(0xFF3FC97A),
    fair = Color(0xFF6AA9DE),
    slow = Color(0xFFE0A93E),
    limit = Color(0xFFEE8248),
    down = Color(0xFFE4635B),
    warnBg = Color(0xFF2A2318),
    warnBorder = Color(0xFF4A3C1E),
    warnText = Color(0xFFD8B15E),
    track = Color(0xFF232A34),
    accentSoft = Color(0xFF16311F)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF12A150),
    onPrimary = Color.White,
    background = Color(0xFFF4F6F8),
    onBackground = Color(0xFF1A2027),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2027),
    secondary = Color(0xFF3B82C4)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF3FC97A),
    onPrimary = Color(0xFF06120B),
    background = Color(0xFF0F1319),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF171C24),
    onSurface = Color(0xFFE6EAF0),
    secondary = Color(0xFF6AA9DE)
)

@Composable
fun NetCheckTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalPalette provides if (dark) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = Typography(),
            content = content
        )
    }
}
