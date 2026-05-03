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
 * 下载进度弹窗
 * 显示下载进度和状态，阻止用户进行其他操作
 */
@Composable
fun DownloadProgressDialog(
    isVisible: Boolean,
    onDismissRequest: () -> Unit
) {
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = "正在下载",
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
                        text = "${DownloadStateManager.currentSongIndex + 1}/${DownloadStateManager.totalSongs}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = DownloadStateManager.currentSongName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                    
                    LinearProgressIndicator(
                        progress = { DownloadStateManager.currentProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                    
                    if (DownloadStateManager.errorMessage.isNotEmpty()) {
                        Text(
                            text = DownloadStateManager.errorMessage,
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
                    enabled = !DownloadStateManager.isDownloading,
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