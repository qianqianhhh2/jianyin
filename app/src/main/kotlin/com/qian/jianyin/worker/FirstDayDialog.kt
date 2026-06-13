package com.qian.jianyin

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Spread
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import java.util.concurrent.TimeUnit

/**
 * 里程碑弹窗组件
 * 弹窗样式与版本更新弹窗相同，并展示彩纸特效
 * @param title 弹窗标题
 * @param content 弹窗内容
 */
@Composable
fun FirstDayDialog(
    isVisible: Boolean,
    title: String = "第一天！",
    content: String = "这是你使用的第一天，继续努力",
    onDismissRequest: () -> Unit
) {
    val confettiColors = remember {
        listOf(
            Color(0xfffce18a),
            Color(0xFF009688),
            Color(0xfff4306d),
            Color(0xffb48def),
            Color(0xFF95FF82),
            Color(0xFF82ECFF),
            Color(0xFFFF9800),
            Color(0xFF0E008A)
        ).map { it.toArgb() }
    }

    val parties = remember(confettiColors) {
        listOf(
            Party(
                speed = 0f,
                maxSpeed = 15f,
                damping = 0.9f,
                angle = Angle.BOTTOM,
                spread = Spread.ROUND,
                colors = confettiColors,
                shapes = listOf(Shape.Square, Shape.Circle, Shape.Rectangle(0.2f)),
                emitter = Emitter(duration = 2, TimeUnit.SECONDS).perSecond(100),
                position = Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0))
            ),
            Party(
                speed = 10f,
                maxSpeed = 30f,
                damping = 0.9f,
                angle = Angle.RIGHT - 45,
                spread = 60,
                colors = confettiColors,
                shapes = listOf(Shape.Square, Shape.Circle, Shape.Rectangle(0.2f)),
                emitter = Emitter(duration = 2, TimeUnit.SECONDS).perSecond(100),
                position = Position.Relative(0.0, 1.0)
            ),
            Party(
                speed = 10f,
                maxSpeed = 30f,
                damping = 0.9f,
                angle = Angle.RIGHT - 135,
                spread = 60,
                colors = confettiColors,
                shapes = listOf(Shape.Square, Shape.Circle, Shape.Rectangle(0.2f)),
                emitter = Emitter(duration = 2, TimeUnit.SECONDS).perSecond(100),
                position = Position.Relative(1.0, 1.0)
            )
        )
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(
            initialScale = 0.9f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ),
        exit = scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismissRequest() }
        ) {
            // 彩纸特效层
            KonfettiView(
                modifier = Modifier.fillMaxSize(),
                parties = parties
            )

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.85f)
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp))
                    .padding(24.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                    Spacer(Modifier.height(24.dp))

                    Text(
                        content,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { onDismissRequest() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("好的", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}
