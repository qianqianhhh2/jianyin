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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri

/**
 * 版本更新弹窗组件
 * @param isVisible 是否显示弹窗
 * @param versionUpdate 版本更新信息
 * @param onDismissRequest 关闭弹窗的回调
 */
@Composable
fun VersionUpdateDialog(
    isVisible: Boolean,
    versionUpdate: VersionUpdate?,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    
    AnimatedVisibility(
        visible = isVisible && versionUpdate != null,
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
        versionUpdate?.let {update ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismissRequest() }
            ) {
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
                            "版本更新",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(16.dp))
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                        Spacer(Modifier.height(24.dp))
                        
                        // 版本信息
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "新版本",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp
                            )
                            Text(
                                update.versionName,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // 更新日志
                        Text(
                            "更新内容",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            update.updateLog,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(Modifier.height(32.dp))
                        
                        // 按钮行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onDismissRequest() }) {
                                Text("暂不更新", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    // 打开下载链接
                                    val intent = Intent(Intent.ACTION_VIEW)
                                    intent.data = Uri.parse(update.downloadUrl)
                                    context.startActivity(intent)
                                    onDismissRequest()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("立即更新", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
