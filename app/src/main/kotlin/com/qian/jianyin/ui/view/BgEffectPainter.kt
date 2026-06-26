package com.qian.jianyin.ui.view

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * 流光背景着色器管理器
 * 基于 RuntimeShader (AGSL) 实现动态流光效果
 * 需要 Android 13+ (API 33)
 *
 * 参考: NeriPlayer / HyperCeiler
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class BgEffectPainter(context: Context) {

    companion object {
        private const val TAG = "BgEffectPainter"
    }

    val renderEffect: RenderEffect
        get() = RenderEffect.createRuntimeShaderEffect(shader, "uTex")

    private val shader: RuntimeShader
    private var uResolution = floatArrayOf(1f, 1f)
    private var uAnimTime = System.nanoTime().toFloat() / 1.0E9f

    // 颜色点 (RGBA × 4)
    private var uColors = floatArrayOf(
        0.57f, 0.76f, 0.98f, 1.0f,  // 蓝
        0.98f, 0.85f, 0.68f, 1.0f,  // 橙
        0.98f, 0.75f, 0.93f, 1.0f,  // 粉
        0.73f, 0.70f, 0.98f, 1.0f   // 紫
    )

    // 光点位置与半径 (x, y, r) × 4
    private var uPoints = floatArrayOf(
        0.67f, 0.42f, 1.0f,
        0.69f, 0.75f, 1.0f,
        0.14f, 0.71f, 0.95f,
        0.14f, 0.27f, 0.8f
    )

    private var uSaturateOffset = 0.2f
    private var uLightOffset = 0.1f

    init {
        val shaderCode = loadShader(context)
        shader = RuntimeShader(shaderCode).apply {
            setFloatUniform("uTranslateY", 0.0f)
            setFloatUniform("uPoints", uPoints)
            setFloatUniform("uColors", uColors)
            setFloatUniform("uNoiseScale", 1.5f)
            setFloatUniform("uPointOffset", 0.1f)
            setFloatUniform("uPointRadiusMulti", 1.0f)
            setFloatUniform("uSaturateOffset", uSaturateOffset)
            setFloatUniform("uShadowColorMulti", 0.3f)
            setFloatUniform("uShadowColorOffset", 0.3f)
            setFloatUniform("uShadowOffset", 0.01f)
            setFloatUniform("uBound", floatArrayOf(0.0f, 0.0f, 1.0f, 1.0f))
            setFloatUniform("uAlphaMulti", 1.0f)
            setFloatUniform("uLightOffset", uLightOffset)
            setFloatUniform("uAlphaOffset", 0.5f)
            setFloatUniform("uShadowNoiseScale", 5.0f)
            setFloatUniform("uMusicLevel", 0f)
            setFloatUniform("uBeat", 0f)
        }
    }

    fun setAnimTime(seconds: Float) {
        uAnimTime = seconds
    }

    fun setResolution(w: Float, h: Float) {
        uResolution = floatArrayOf(w, h)
    }

    fun setColors(colors: FloatArray) {
        uColors = colors
        shader.setFloatUniform("uColors", colors)
    }

    fun setLightOffset(offset: Float) {
        uLightOffset = offset
        shader.setFloatUniform("uLightOffset", offset)
    }

    fun setSaturateOffset(offset: Float) {
        uSaturateOffset = offset
        shader.setFloatUniform("uSaturateOffset", offset)
    }

    /**
     * 应用 RenderEffect 到目标 View，每帧调用
     */
    fun updateAndApply(view: View) {
        shader.apply {
            setFloatUniform("uAnimTime", uAnimTime)
            setFloatUniform("uResolution", uResolution)
        }
        view.setRenderEffect(renderEffect)
    }

    private fun loadShader(context: Context): String {
        return try {
            context.assets.open("hyper_background_effect.glsl").use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load shader asset", e)
            ""
        }
    }
}
