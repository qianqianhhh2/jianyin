package com.qian.jianyin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

// ── 平滑时间常量 ──
private const val LYRIC_TIME_SMOOTHING_DURATION_MS = 96
private const val LYRIC_TIME_SMOOTHING_MAX_DELTA_MS = 240L

// ── 视觉规格 ──
@Stable
data class LyricVisualSpec(
    val glowColor: Color = Color.White,
    val glowRadiusExpanded: Dp = 48.dp,
    val glowAlpha: Float = 0.85f,
    val glowMoveSmoothingMs: Int = 110,
    val glowPulseStiffness: Float = Spring.StiffnessMedium,
    val glowPulseDamping: Float = 0.72f
)

// ═══════════════════════════════════════════════
// 进度计算
// ═══════════════════════════════════════════════

/**
 * 根据当前时间计算该行的高亮进度（0f..1f），基于字符数进行精确计算。
 * 无 WordTiming 时回退到线性插值。
 */
fun calculateLineProgress(line: LyricEntry, currentTimeMs: Long): Float {
    val start = line.startTimeMs
    val end = line.endTimeMs

    if (currentTimeMs <= start) return 0f
    if (currentTimeMs >= end) return 1f

    val words = line.words
    val totalChars = line.text.length
    if (words.isNullOrEmpty() || totalChars == 0) {
        val lineDur = (end - start).coerceAtLeast(1)
        return ((currentTimeMs - start).toFloat() / lineDur).coerceIn(0f, 1f)
    }

    var completedChars = 0
    for (word in words) {
        val ws = word.startTimeMs
        val we = word.endTimeMs

        if (currentTimeMs < ws) {
            return completedChars.toFloat() / totalChars
        }

        if (currentTimeMs < we) {
            val wordDur = (we - ws).coerceAtLeast(1)
            val timeInWord = currentTimeMs - ws
            val partialProgress = timeInWord.toFloat() / wordDur
            val partialChars = partialProgress * word.charCount
            return ((completedChars + partialChars) / totalChars).coerceIn(0f, 1f)
        }

        completedChars += word.charCount
    }

    return 1f
}

/** 二分查找当前行索引 */
fun findCurrentLineIndex(lines: List<LyricEntry>, currentTimeMs: Long): Int {
    if (lines.isEmpty()) return -1
    var low = 0
    var high = lines.lastIndex
    var result = 0
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].startTimeMs <= currentTimeMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

// ═══════════════════════════════════════════════
// 平滑时间
// ═══════════════════════════════════════════════

internal fun shouldSnapLyricTimeSmoothing(
    displayedTimeMs: Long,
    targetTimeMs: Long,
    maxAnimatedDeltaMs: Long = LYRIC_TIME_SMOOTHING_MAX_DELTA_MS
): Boolean {
    val delta = targetTimeMs - displayedTimeMs
    return delta < 0L || delta > maxAnimatedDeltaMs
}

@Composable
fun rememberSmoothedLyricTimeMs(targetTimeMs: Long): Long {
    val smoothedTime = remember { Animatable(targetTimeMs.toFloat()) }

    LaunchedEffect(targetTimeMs) {
        val displayedTimeMs = smoothedTime.value.roundToLong()
        if (shouldSnapLyricTimeSmoothing(displayedTimeMs, targetTimeMs)) {
            smoothedTime.snapTo(targetTimeMs.toFloat())
        } else {
            smoothedTime.animateTo(
                targetValue = targetTimeMs.toFloat(),
                animationSpec = tween(
                    durationMillis = LYRIC_TIME_SMOOTHING_DURATION_MS,
                    easing = LinearEasing
                )
            )
        }
    }

    return smoothedTime.value.roundToLong()
}

// ═══════════════════════════════════════════════
// 单词合并 & 活动词检测（用于光晕）
// ═══════════════════════════════════════════════

data class ActiveWord(val range: IntRange, val sustainWeight: Float, val tInWord: Float)

internal fun mergeWordTimings(
    words: List<WordTiming>?,
    mergeGapMs: Long = 90L
): List<Triple<IntRange, Long, Long>> {
    if (words.isNullOrEmpty()) return emptyList()

    val merged = mutableListOf<Triple<IntRange, Long, Long>>()
    var accStart = words.first().startTimeMs
    var accEnd = words.first().endTimeMs
    var accRangeStart = 0
    var accRangeEnd = words.first().charCount.coerceAtLeast(1) - 1

    fun flush() { merged += Triple(accRangeStart..accRangeEnd, accStart, accEnd) }

    for (i in 1 until words.size) {
        val wPrevEnd = accEnd
        val w = words[i]
        val chars = w.charCount.coerceAtLeast(1)
        val rEnd = accRangeEnd + chars

        if (w.startTimeMs - wPrevEnd <= mergeGapMs) {
            accEnd = maxOf(accEnd, w.endTimeMs)
            accRangeEnd = rEnd
        } else {
            flush()
            accStart = w.startTimeMs
            accEnd = w.endTimeMs
            accRangeStart = accRangeEnd + 1
            accRangeEnd = accRangeStart + chars - 1
        }
    }
    flush()
    return merged
}

internal fun findActiveWord(
    mergedWords: List<Triple<IntRange, Long, Long>>,
    t: Long,
    marginMs: Long = 80L
): ActiveWord? {
    for ((range, start, end) in mergedWords) {
        val s = start - marginMs
        val e = end + marginMs
        if (t in s..e) {
            val dur = (end - start).coerceAtLeast(1)
            val tIn = ((t - start).toFloat() / dur).coerceIn(0f, 1f)
            val sustain = ((dur - 140f) / (900f - 140f)).coerceIn(0f, 1f)
            return ActiveWord(range, sustain, tIn)
        }
    }
    return null
}

// ═══════════════════════════════════════════════
// 多行渐变逐字揭示 Modifier（接收 State，避免参数变化重组）
// ═══════════════════════════════════════════════

/**
 * 读取 State<Float> 仅在 draw 阶段 → 值变化时只触发重绘，不触发重组。
 */
fun Modifier.multilineGradientReveal(
    layout: TextLayoutResult?,
    revealOffsetCharsState: State<Float>,
    textLength: Int,
    fadeWidth: Dp
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        val revealOffsetChars = revealOffsetCharsState.value
        if (layout == null || textLength == 0) {
            drawContent()
            return@drawWithContent
        }
        if (revealOffsetChars >= textLength) {
            drawContent()
            return@drawWithContent
        }

        val safeChars = revealOffsetChars.coerceIn(0f, textLength.toFloat())
        val totalLines = layout.lineCount

        for (lineIndex in 0 until totalLines) {
            val lineStartIdx = layout.getLineStart(lineIndex)
            val lineEndIdx = layout.getLineEnd(lineIndex, true)
            val left = layout.getLineLeft(lineIndex)
            val right = layout.getLineRight(lineIndex)
            val top = layout.getLineTop(lineIndex)
            val bottom = layout.getLineBottom(lineIndex)

            when {
                safeChars >= lineEndIdx -> {
                    clipRect(left = left, top = top, right = right, bottom = bottom) {
                        this@drawWithContent.drawContent()
                    }
                }
                safeChars >= lineStartIdx -> {
                    val currentIdxInLine = (safeChars - lineStartIdx).coerceAtLeast(0f)
                    val currentCharIdx = lineStartIdx + floor(currentIdxInLine).toInt()
                    val frac = (currentIdxInLine - floor(currentIdxInLine)).coerceIn(0f, 1f)

                    val x0 = runCatching { layout.getBoundingBox(currentCharIdx).left }
                        .getOrDefault(layout.getHorizontalPosition(currentCharIdx, true))
                    val nextCharIdx = if (currentCharIdx >= lineEndIdx - 1) lineEndIdx else currentCharIdx + 1
                    val x1 = if (currentCharIdx >= lineEndIdx - 1) {
                        right
                    } else {
                        runCatching { layout.getBoundingBox(nextCharIdx).left }
                            .getOrDefault(layout.getHorizontalPosition(nextCharIdx, true))
                    }

                    val x = (x0 + (x1 - x0) * frac).coerceIn(left, right)
                    val fadePx = fadeWidth.toPx()
                    val start = (x - fadePx).coerceAtLeast(left)

                    clipRect(left = left, top = top, right = right, bottom = bottom) {
                        this@drawWithContent.drawContent()

                        val brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.White,
                                ((start - left) / (right - left)) to Color.White,
                                ((x - left) / (right - left)) to Color.Transparent,
                                1f to Color.Transparent
                            ),
                            startX = left,
                            endX = right
                        )
                        drawRect(
                            brush = brush,
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
            }
        }
    }

// ═══════════════════════════════════════════════
// 当前活动行（底版 + 高亮 + 光晕）
// - 光晕：Animatable 动画，.value 仅在 drawBehind 内部读 → 不触发重组
// - 逐字揭示：withFrameNanos 帧级插值，写入 mutableFloatStateOf，仅在 drawWithContent 读 → 不触发重组
// ═══════════════════════════════════════════════

@Composable
fun AppleMusicActiveLine(
    line: LyricEntry,
    currentTimeMs: Long,
    activeColor: Color,
    inactiveColor: Color,
    fontSize: TextUnit,
    fadeWidth: Dp = 12.dp,
    spec: LyricVisualSpec = LyricVisualSpec()
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val isLayoutReady by remember { derivedStateOf { layout != null } }

    // ── 帧级插值的逐字揭示偏移（mutableFloatStateOf → draw 阶段读取，不触发重组）──
    val interpolatedRevealChars = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentTimeMs, isLayoutReady, line) {
        if (!isLayoutReady) return@LaunchedEffect

        // VM 更新到达时：重置锚点
        val progress = calculateLineProgress(line, currentTimeMs).coerceIn(0f, 1f)
        interpolatedRevealChars.floatValue = line.text.length * progress

        // 当前行已结束 → 不必再插值
        if (currentTimeMs >= line.endTimeMs) return@LaunchedEffect

        var anchorMs = currentTimeMs
        var anchorNanos = System.nanoTime()

        while (isActive) {
            val frameNanos = withFrameNanos { it }
            val elapsedMs = (frameNanos - anchorNanos) / 1_000_000.0
            val predictedMs = (anchorMs + elapsedMs).toLong()
            val p = calculateLineProgress(line, predictedMs).coerceIn(0f, 1f)
            interpolatedRevealChars.floatValue = line.text.length * p

            if (predictedMs >= line.endTimeMs) break
        }
    }

    // ── 光晕动画 ──
    val mergedWords = remember(line.words) { mergeWordTimings(line.words) }
    val isWordCurrentlyActive = remember(mergedWords, currentTimeMs) {
        findActiveWord(mergedWords, currentTimeMs) != null
    }

    val headGlowRadiusAnim = remember { Animatable(0f) }
    val headGlowAlphaAnim = remember { Animatable(0f) }

    LaunchedEffect(isWordCurrentlyActive) {
        if (isWordCurrentlyActive) {
            launch {
                headGlowRadiusAnim.animateTo(
                    spec.glowRadiusExpanded.value,
                    animationSpec = spring(spec.glowPulseStiffness, spec.glowPulseDamping)
                )
            }
            launch {
                headGlowAlphaAnim.animateTo(
                    spec.glowAlpha,
                    animationSpec = tween(spec.glowMoveSmoothingMs)
                )
            }
        } else {
            launch { headGlowRadiusAnim.animateTo(0f, spring(spec.glowPulseStiffness, spec.glowPulseDamping)) }
            launch { headGlowAlphaAnim.animateTo(0f, tween(spec.glowMoveSmoothingMs)) }
        }
    }

    val density = LocalDensity.current
    val textStyle = TextStyle(
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        letterSpacing = 0.sp
    )

    Box(
        modifier = Modifier.drawBehind {
            val radiusPx = with(density) { headGlowRadiusAnim.value.toDp().toPx() }
            val alpha = headGlowAlphaAnim.value
            val charset = interpolatedRevealChars.floatValue
            if (layout != null && radiusPx > 0f && alpha > 0.01f) {
                drawRadialHeadGlow(
                    layout = layout!!,
                    charOffset = charset,
                    radiusPx = radiusPx,
                    color = spec.glowColor,
                    alpha = alpha
                )
            }
        }
    ) {
        // 底版文本
        Text(
            text = line.text,
            style = textStyle.copy(color = inactiveColor),
            maxLines = Int.MAX_VALUE,
            softWrap = true,
            onTextLayout = { newLayout ->
                if (layout?.layoutInput != newLayout.layoutInput) {
                    layout = newLayout
                }
            }
        )

        // 高亮文本 —— interpolatedRevealChars 以 State 传入，draw 阶段读取
        if (isLayoutReady) {
            Text(
                text = line.text,
                style = textStyle.copy(color = activeColor),
                maxLines = Int.MAX_VALUE,
                softWrap = true,
                modifier = Modifier.multilineGradientReveal(
                    layout = layout,
                    revealOffsetCharsState = interpolatedRevealChars,
                    textLength = line.text.length,
                    fadeWidth = fadeWidth
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════
// 径向头部光晕
// ═══════════════════════════════════════════════

private fun DrawScope.drawRadialHeadGlow(
    layout: TextLayoutResult,
    charOffset: Float,
    radiusPx: Float,
    color: Color,
    alpha: Float
) {
    if (radiusPx <= 0f || alpha <= 0.01f) return

    val textLength = layout.layoutInput.text.length.coerceAtLeast(1)
    val safeOffset = charOffset.coerceIn(0f, textLength.toFloat())

    val currentIndex = floor(safeOffset).toInt().coerceIn(0, textLength - 1)
    val nextIndex = (currentIndex + 1).coerceAtMost(textLength - 1)
    val fraction = (safeOffset - currentIndex).coerceIn(0f, 1f)

    val currentLine = layout.getLineForOffset(currentIndex)
    val y0 = (layout.getLineTop(currentLine) + layout.getLineBottom(currentLine)) * 0.5f
    val x0 = runCatching { layout.getBoundingBox(currentIndex).left }
        .getOrDefault(layout.getHorizontalPosition(currentIndex, true))

    val nextLine = layout.getLineForOffset(nextIndex)
    val nextCharLeft = if (nextLine == currentLine && nextIndex >= layout.getLineEnd(currentLine, true) - 1) {
        layout.getLineRight(currentLine)
    } else {
        runCatching { layout.getBoundingBox(nextIndex).left }
            .getOrDefault(layout.getHorizontalPosition(nextIndex, true))
    }

    val target = resolveHeadGlowTarget(
        currentLine = currentLine,
        nextLine = nextLine,
        currentLineRight = layout.getLineRight(currentLine),
        currentLineCenterY = y0,
        nextCharLeft = nextCharLeft,
        nextLineCenterY = (layout.getLineTop(nextLine) + layout.getLineBottom(nextLine)) * 0.5f
    )

    val cx = x0 + (target.x - x0) * fraction
    val cy = y0 + (target.y - y0) * fraction

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = Offset(cx, cy),
            radius = radiusPx
        ),
        radius = radiusPx,
        center = Offset(cx, cy)
    )
}

private data class HeadGlowTarget(val x: Float, val y: Float)

private fun resolveHeadGlowTarget(
    currentLine: Int,
    nextLine: Int,
    currentLineRight: Float,
    currentLineCenterY: Float,
    nextCharLeft: Float,
    nextLineCenterY: Float
): HeadGlowTarget {
    return if (nextLine != currentLine) {
        HeadGlowTarget(x = currentLineRight, y = currentLineCenterY)
    } else {
        HeadGlowTarget(x = nextCharLeft, y = nextLineCenterY)
    }
}

// ═══════════════════════════════════════════════
// 翻译匹配
// ═══════════════════════════════════════════════

private const val TranslationAlignmentToleranceMs = 450L

internal fun matchTranslationsToLineIndices(
    lines: List<LyricEntry>,
    translations: List<LyricEntry>,
    toleranceMs: Long = TranslationAlignmentToleranceMs
): Map<Int, LyricEntry> {
    if (lines.isEmpty() || translations.isEmpty()) return emptyMap()

    val matchesByIndex = linkedMapOf<Int, LyricEntry>()
    var translationIndex = 0

    lines.forEachIndexed { lineIndex, line ->
        while (translationIndex < translations.size) {
            val translation = translations[translationIndex]
            val normalizedLineEnd = if (line.endTimeMs > line.startTimeMs) line.endTimeMs else line.startTimeMs + 1
            val currentDistanceMs = when {
                translation.startTimeMs < line.startTimeMs -> line.startTimeMs - translation.startTimeMs
                translation.startTimeMs >= normalizedLineEnd -> translation.startTimeMs - normalizedLineEnd + 1
                else -> 0L
            }
            val nextLine = lines.getOrNull(lineIndex + 1)
            val nextDistanceMs = nextLine?.let { next ->
                val normEnd = if (next.endTimeMs > next.startTimeMs) next.endTimeMs else next.startTimeMs + 1
                when {
                    translation.startTimeMs < next.startTimeMs -> next.startTimeMs - translation.startTimeMs
                    translation.startTimeMs >= normEnd -> translation.startTimeMs - normEnd + 1
                    else -> 0L
                }
            } ?: Long.MAX_VALUE

            if (translation.startTimeMs < line.startTimeMs && currentDistanceMs > toleranceMs) {
                translationIndex++
                continue
            }

            if (currentDistanceMs <= toleranceMs && currentDistanceMs <= nextDistanceMs) {
                matchesByIndex[lineIndex] = translation
                translationIndex++
            }
            break
        }
    }

    return matchesByIndex
}

internal fun findBestMatchingTranslation(
    translations: List<LyricEntry>,
    lineStartMs: Long,
    lineEndMs: Long,
    toleranceMs: Long = 1_500L
): LyricEntry? {
    if (translations.isEmpty()) return null

    val normalizedLineEnd = if (lineEndMs > lineStartMs) lineEndMs else lineStartMs + 1
    var bestOverlappingTranslation: LyricEntry? = null
    var bestOverlapMs = 0L
    var bestStartDeltaMs = Long.MAX_VALUE

    for (candidate in translations) {
        val candidateEndMs = if (candidate.endTimeMs > candidate.startTimeMs) candidate.endTimeMs else candidate.startTimeMs + 1
        val overlapMs = min(normalizedLineEnd, candidateEndMs) - max(lineStartMs, candidate.startTimeMs)
        if (overlapMs <= 0L) continue

        val startDeltaMs = abs(candidate.startTimeMs - lineStartMs)
        val shouldReplaceBest = overlapMs > bestOverlapMs ||
            (overlapMs == bestOverlapMs && startDeltaMs < bestStartDeltaMs) ||
            (overlapMs == bestOverlapMs && startDeltaMs == bestStartDeltaMs &&
                candidate.startTimeMs < (bestOverlappingTranslation?.startTimeMs ?: Long.MAX_VALUE))
        if (shouldReplaceBest) {
            bestOverlappingTranslation = candidate
            bestOverlapMs = overlapMs
            bestStartDeltaMs = startDeltaMs
        }
    }

    if (bestOverlappingTranslation != null) return bestOverlappingTranslation

    val nearestTranslation = translations.minWithOrNull(
        compareBy<LyricEntry> { abs(it.startTimeMs - lineStartMs) }.thenBy { it.startTimeMs }
    )
    return nearestTranslation?.takeIf { abs(it.startTimeMs - lineStartMs) <= toleranceMs }
}
