package com.qian.jianyin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * 导入进度弹窗
 * 显示导入进度和状态，阻止用户进行其他操作
 */
@Composable
fun ImportProgressDialog(
    isVisible: Boolean,
    onDismissRequest: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = "正在导入",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${ImportStateManager.currentSongIndex + 1}/${ImportStateManager.totalSongs}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = ImportStateManager.currentSongName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                    
                    LinearProgressIndicator(
                        progress = { ImportStateManager.currentProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    
                    if (ImportStateManager.errorMessage.isNotEmpty()) {
                        Text(
                            text = ImportStateManager.errorMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismissRequest,
                    enabled = !ImportStateManager.isImporting,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "关闭")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        )
    }
}