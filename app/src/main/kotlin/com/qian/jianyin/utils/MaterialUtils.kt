package com.qian.jianyin

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

object MaterialUtils {
    // 检查系统是否支持 (Android 12+)
    fun isSupported(): Boolean = android.os.Build.VERSION.SDK_INT >= 31

    /**
     * 高级模糊修饰符
     * @param enabled 是否开启
     * @param radius 模糊半径
     */
    fun Modifier.advancedBlur(enabled: Boolean, radius: Float = 35f): Modifier = if (enabled && isSupported()) {
        this.graphicsLayer {
            renderEffect = RenderEffect.createBlurEffect(
                radius, radius, Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    } else this
}
