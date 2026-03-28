package com.qian.jianyin

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 模拟 HTML backdrop-filter: blur() 的独立背景墙组件
 */
@Composable
fun HtmlLikeBlurBackWall(
    isEnabled: Boolean,
    backgroundColor: Color,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        if (isEnabled && Build.VERSION.SDK_INT >= 31) {
            // 开启模糊：应用 RenderEffect 并设为半透明
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = RenderEffect.createBlurEffect(
                            50f, 50f, Shader.TileMode.CLAMP
                        ).asComposeRenderEffect()
                    }
                    .background(backgroundColor.copy(alpha = 0.65f))
            )
        } else {
            // 关闭模糊：显示实心背景，完全不透明
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
            )
        }
    }
}
