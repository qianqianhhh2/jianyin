package com.qian.jianyin

import android.graphics.Bitmap
import android.graphics.Color

/**
 * 图片亮度分析工具类
 * 用于分析封面图片的亮度，判断是否需要压暗处理
 */
object ImageBrightnessAnalyzer {

    /**
     * 亮度阈值（0-255），超过此值认为图片过亮
     * 255表示纯白色，180约等于70%的亮度
     */
    private const val BRIGHTNESS_THRESHOLD = 180

    /**
     * 采样步长，用于减少计算量
     */
    private const val SAMPLE_STEP = 10

    /**
     * 分析图片是否过亮
     * @param bitmap 图片位图
     * @return true 如果图片过亮需要压暗
     */
    fun isImageTooBright(bitmap: Bitmap): Boolean {
        return calculateAverageBrightness(bitmap) >= BRIGHTNESS_THRESHOLD
    }

    /**
     * 计算图片的平均亮度
     * @param bitmap 图片位图
     * @return 平均亮度值（0-255）
     */
    fun calculateAverageBrightness(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        
        var totalBrightness = 0
        var pixelCount = 0

        // 采样分析，每隔SAMPLE_STEP个像素取一个样本
        for (y in 0 until height step SAMPLE_STEP) {
            for (x in 0 until width step SAMPLE_STEP) {
                val pixel = bitmap.getPixel(x, y)
                
                // 计算该像素的亮度
                // 使用标准的RGB转亮度公式：L = 0.299*R + 0.587*G + 0.114*B
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val brightness = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                
                totalBrightness += brightness
                pixelCount++
            }
        }

        return if (pixelCount > 0) {
            totalBrightness / pixelCount
        } else {
            0
        }
    }

    /**
     * 根据图片亮度计算需要压暗的程度
     * @param bitmap 图片位图
     * @return 压暗程度（0.0-0.5），0表示不需要压暗，0.5表示最大压暗
     */
    fun calculateDarkenAlpha(bitmap: Bitmap): Float {
        val brightness = calculateAverageBrightness(bitmap)
        
        if (brightness < BRIGHTNESS_THRESHOLD) {
            return 0f // 不需要压暗
        }

        // 根据亮度超出阈值的程度计算压暗程度
        // 亮度范围：180-255，压暗程度：0-0.5
        val excessBrightness = brightness - BRIGHTNESS_THRESHOLD
        val maxExcess = 255 - BRIGHTNESS_THRESHOLD
        
        return (excessBrightness.toFloat() / maxExcess) * 0.5f
    }
}