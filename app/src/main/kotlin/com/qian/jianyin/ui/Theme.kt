package com.qian.jianyin.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.materialkolor.rememberDynamicColorScheme
import com.qian.jianyin.DownloadSettingsStore

// ── 默认内置配色 ──

private val BuiltInDark = darkColorScheme(
    primary = Color(0xFFA7C2F7),
    onPrimary = Color(0xFF002F5C),
    primaryContainer = Color(0xFF004187),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBAC4D8),
    onSecondary = Color(0xFF252F3E),
    secondaryContainer = Color(0xFF3B4758),
    onSecondaryContainer = Color(0xFFD6E0F0),
    tertiary = Color(0xFFFFA726),
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF553800),
    error = Color(0xFFFF4444),
    onError = Color(0xFF690005),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE1E1E1),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE1E1E1),
    surfaceVariant = Color(0xFF2D3748),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E)
)

private val BuiltInLight = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF535F73),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E0F0),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFFFFA726),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE0B2),
    error = Color(0xFFFF4444),
    onError = Color(0xFFFFFFFF),
    background = Color(0xFFF0F4F9),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF0F4F9),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE3EAF6),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF)
)

// ── 主题入口 ──

@Composable
fun JianYinTheme(content: @Composable () -> Unit) {
    val darkModeSetting by DownloadSettingsStore.darkModeFlow.collectAsState()
    val themeSource by DownloadSettingsStore.themeSourceFlow.collectAsState()
    val seedColorLong by DownloadSettingsStore.seedColorFlow.collectAsState()
    val coverColorLong by DownloadSettingsStore.coverColorFlow.collectAsState()

    val darkTheme = when (darkModeSetting) {
        0 -> isSystemInDarkTheme()
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    // 计算目标种子色
    val targetSeed = when {
        themeSource == 2 -> {
            if (coverColorLong != 0L) ThemeColorUtil.seedLongToColor(coverColorLong)
            else if (seedColorLong != 0L) ThemeColorUtil.seedLongToColor(seedColorLong)
            else null
        }
        (themeSource == 1 || themeSource == 3) && seedColorLong != 0L -> ThemeColorUtil.seedLongToColor(seedColorLong)
        else -> null
    }

    // 平滑动画过渡种子色（歌曲切换时颜色渐变）
    val animatedSeed by animateColorAsState(
        targetValue = targetSeed ?: if (darkTheme) BuiltInDark.primary else BuiltInLight.primary,
        animationSpec = tween(durationMillis = 600)
    )

    // materialkolor 生成标准 Material You 配色方案
    val colorScheme = if (targetSeed != null) {
        rememberDynamicColorScheme(seedColor = animatedSeed, isDark = darkTheme)
    } else {
        if (darkTheme) BuiltInDark else BuiltInLight
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
