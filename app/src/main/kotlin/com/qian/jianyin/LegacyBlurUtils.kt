package com.qian.jianyin

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation

object BlurCompat {
    /**
     * 检查当前系统是否原生支持 RenderEffect 模糊 (Android 12+)
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun supportsNativeBlur(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    /**
     * 统一的模糊 Modifier 适配器
     */
    fun Modifier.compatBlur(radius: Dp, alpha: Float = 0.5f): Modifier {
        return if (supportsNativeBlur()) {
            // Android 12+：使用原生高性能模糊
            this.blur(radius).graphicsLayer(alpha = alpha)
        } else {
            // Android 12以下：原生模糊不生效，这里只处理透明度，
            // 具体模糊逻辑需要配合 Coil 的 ImageRequest 实现。
            this.graphicsLayer(alpha = alpha)
        }
    }

    /**
     * 为 Coil 的 ImageRequest 配置旧版本兼容模糊
     */
    fun configureLegacyBlur(requestBuilder: ImageRequest.Builder, radius: Int = 25) {
        if (!supportsNativeBlur()) {
            // Android 12以下：使用我们手写的静态模糊 Transformation
            requestBuilder.transformations(ManualStackBlurTransformation(radius))
        }
    }

    /**
     * 自定义 Coil Transformation，用于手写模糊算法
     */
    private class ManualStackBlurTransformation(private val radius: Int) : Transformation {
        override val cacheKey: String = "manual_stack_blur_$radius"

        override suspend fun transform(input: Bitmap, size: Size): Bitmap {
            // 算法需要操作可变的 Bitmap
            val bitmap = input.copy(Bitmap.Config.ARGB_8888, true)
            return stackBlur(bitmap, radius)
        }
    }

    /**
     *经典的 Stack Blur 算法实现 (纯 Kotlin)
     * 这是一个高效的近似高斯模糊算法。
     * 核心思想是使用滑窗和权重堆栈来计算像素平均值。
     */
    private fun stackBlur(sentBitmap: Bitmap, radius: Int): Bitmap {
        var radius = radius
        if (radius < 1) return sentBitmap
        
        val w = sentBitmap.width
        val h = sentBitmap.height
        val pix = IntArray(w * h)
        
        // 获取所有像素点
        sentBitmap.getPixels(pix, 0, w, 0, 0, w, h)
        
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(Math.max(w, h))
        var divsum = div + 1 shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }
        
        yi = 0
        yw = 0
        
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        val r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int
        
        y = 0
        while (y < h) {
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            bsum = 0
            gsum = 0
            rsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))]
                sir = stack[i + radius]
                sir[0] = p shr 16 and 0xff
                sir[1] = p shr 8 and 0xff
                sir[2] = p and 0xff
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius
            
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                
                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]
                
                sir[0] = p shr 16 and 0xff
                sir[1] = p shr 8 and 0xff
                sir[2] = p and 0xff
                
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                
                yi++
                x++
            }
            yw += w
            y++
        }
        x = 0
        while (x < w) {
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            bsum = 0
            gsum = 0
            rsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = Math.max(0, yp) + x
                
                sir = stack[i + radius]
                
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                
                rbs = r1 - Math.abs(i)
                
                rsum += r[yi] * rbs
                gsum += g[yi] * rbs
                bsum += b[yi] * rbs
                
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                // 将处理后的 RGB 数据写回原始像素数组
                pix[yi] = -0x1000000 and pix[yi] or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                
                if (x == 0) {
                    vmin[y] = Math.min(y + radius + 1, hm) * w
                }
                p = x + vmin[y]
                
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                
                yi += w
                y++
            }
            x++
        }
        
        // 将处理后的像素数组写回 Bitmap
        sentBitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return sentBitmap
    }
}
