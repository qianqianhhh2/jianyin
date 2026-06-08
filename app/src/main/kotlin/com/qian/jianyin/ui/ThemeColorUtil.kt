package com.qian.jianyin.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从壁纸/图片提取种子色，构建主题配色。
 * 取色管线对齐 NeriPlayer：Coil 子采样解码 + Palette clearFilters。
 */
object ThemeColorUtil {

    private const val COVER_SAMPLE_SIZE = 96
    private val cache = LruCache<String, Long>(64)

    /** 从系统壁纸提取种子色 */
    fun extractFromWallpaper(context: Context): Long? {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val drawable = wm.drawable ?: wm.fastDrawable ?: return null
            val bitmap = drawableToBitmap(drawable)
            val scaled = scaleDown(bitmap, 400)
            bitmap.recycle()
            val seed = extract(scaled)
            scaled.recycle()
            seed
        } catch (e: Exception) {
            null
        }
    }

    /** 从 URL 下载图片并提取种子色（对齐 NeriPlayer 的 Coil 管线） */
    suspend fun extractFromUrl(context: Context, imageUrl: String): Long? {
        cache.get(imageUrl)?.let { return it }

        val result = runCatching {
            val loader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .size(COVER_SAMPLE_SIZE)
                .precision(Precision.INEXACT)
                .build()
            val imgResult = withContext(Dispatchers.IO) { loader.execute(request) }
            val bitmap = ((imgResult as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                ?: return@runCatching null
            withContext(Dispatchers.Default) { extract(bitmap) }
        }.getOrNull()

        if (result != null) cache.put(imageUrl, result)
        return result
    }

    fun seedLongToColor(argb: Long): Color = Color(argb.toInt())

    private fun extract(bitmap: Bitmap): Long {
        val palette = Palette.from(bitmap)
            .clearFilters()
            .generate()

        val baseColor = palette.getVibrantColor(
            palette.getMutedColor(
                palette.getDominantColor(0xFF808080.toInt())
            )
        )
        return baseColor.toLong() and 0xFFFFFFFFL
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bitmap
    }

    private fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
        val ratio = (maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
