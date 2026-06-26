package com.qian.jianyin.ui.view

import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * 流光着色器背景
 * 使用 RuntimeShader (AGSL) 渲染动态流光效果
 * - Android 13+ (API 33): 启用 RuntimeShader 流光效果
 * - 低版本: 自动降级为透明背景
 *
 * 颜色从封面图片 Palette 提取，随封面变化自动切换
 */
@Composable
fun ShaderBackground(
    modifier: Modifier = Modifier,
    coverUrl: String?,
    isDark: Boolean = isSystemInDarkTheme(),
    refreshKey: Int = 0
) {
    val context = LocalContext.current
    val currentIsDark by rememberUpdatedState(isDark)

    // 仅 Android 13+ 创建 painter
    val painter = remember(currentIsDark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            BgEffectPainter(context)
        } else null
    }

    var hostView by remember { mutableStateOf<View?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            View(ctx).apply {
                setWillNotDraw(false)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                hostView = this
            }
        },
        update = { v ->
            hostView = v
        }
    )

    // 等待 View 真正就绪
    suspend fun awaitViewReady(v: View) {
        while (!v.isAttachedToWindow || v.parent == null || !v.isLaidOut || v.width == 0 || v.height == 0) {
            withFrameNanos { /* 等待下一帧 */ }
        }
    }

    // 驱动着色器逐帧更新
    LaunchedEffect(painter, hostView, refreshKey) {
        if (painter == null || hostView == null) return@LaunchedEffect
        val v = hostView!!

        awaitViewReady(v)

        var startNs = 0L
        while (isActive) {
            withFrameNanos { t ->
                if (startNs == 0L) startNs = t
                val seconds = ((t - startNs) / 1_000_000_000.0).toFloat()
                painter.setAnimTime(seconds % 62.831852f)

                val w = v.width
                val h = v.height
                if (w > 0 && h > 0) {
                    painter.setResolution(w.toFloat(), h.toFloat())
                }
                painter.updateAndApply(v)
            }
        }
    }

    // 从封面提取颜色馈入着色器
    LaunchedEffect(coverUrl, currentIsDark, refreshKey) {
        if (painter == null || coverUrl.isNullOrBlank()) return@LaunchedEffect

        try {
            val loader = Coil.imageLoader(context)
            val req = ImageRequest.Builder(context)
                .data(coverUrl)
                .allowHardware(false) // Palette 需要 software bitmap
                .build()

            val result = withContext(Dispatchers.IO) { loader.execute(req) }
            val bmp = (result as? coil.request.SuccessResult)?.drawable?.toBitmap() ?: return@LaunchedEffect

            val palette = withContext(Dispatchers.Default) {
                Palette.from(bmp)
                    .clearFilters()
                    .maximumColorCount(16)
                    .generate()
            }

            fun pickColor(vararg candidates: Int?): Int =
                candidates.firstOrNull { it != null && it != 0 } ?: 0xFF808080.toInt()

            val c1 = pickColor(
                palette.dominantSwatch?.rgb,
                palette.vibrantSwatch?.rgb,
                palette.mutedSwatch?.rgb
            )
            val c2 = pickColor(
                palette.lightVibrantSwatch?.rgb,
                palette.lightMutedSwatch?.rgb,
                c1
            )
            val c3 = pickColor(
                palette.mutedSwatch?.rgb,
                palette.vibrantSwatch?.rgb,
                c1
            )
            val c4 = pickColor(
                palette.darkMutedSwatch?.rgb,
                palette.darkVibrantSwatch?.rgb,
                c1
            )

            fun to01(x: Int) = (x and 0xFF) / 255f
            fun argbToVec4(c: Int): FloatArray {
                val r = to01(c ushr 16)
                val g = to01(c ushr 8)
                val b = to01(c)
                return floatArrayOf(r, g, b, 1f)
            }

            val colors = floatArrayOf(
                *argbToVec4(c1), *argbToVec4(c2), *argbToVec4(c3), *argbToVec4(c4)
            )

            // 根据明暗调整亮度/饱和度
            fun luma(c: Int): Float {
                val r = to01(c ushr 16)
                val g = to01(c ushr 8)
                val b = to01(c)
                return 0.2126f * r + 0.7152f * g + 0.0722f * b
            }
            val lumaValue = luma(c1)
            val lightOffset = when {
                currentIsDark -> (-0.06f + 0.12f * (lumaValue - 0.5f)).coerceIn(-0.12f, 0.12f)
                else -> (0.08f + 0.10f * (0.5f - lumaValue)).coerceIn(-0.12f, 0.12f)
            }
            val saturateOffset = if (currentIsDark) 0.24f else 0.16f

            painter.setColors(colors)
            painter.setLightOffset(lightOffset)
            painter.setSaturateOffset(saturateOffset)
        } catch (_: Throwable) {
            // 颜色提取失败，继续使用默认配色
        }
    }
}
