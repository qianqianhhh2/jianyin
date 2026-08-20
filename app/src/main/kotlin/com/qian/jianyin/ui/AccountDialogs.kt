package com.qian.jianyin

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qian.jianyin.netease.api.NeteaseApiService
import kotlinx.coroutines.launch

@Composable
fun NeteaseLogoutDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colorScheme.surface,
            title = { Text("网易云账号") },
            text = { Text("确定要退出网易云登录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    NeteaseApiService.logout()
                    onDismiss()
                    Toast.makeText(context, "已退出网易云登录", Toast.LENGTH_SHORT).show()
                }) { Text("退出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }
}

@Composable
fun BiliLogoutDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    vm: MusicViewModel
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colorScheme.surface,
            title = { Text("B站账号") },
            text = { Text("确定要退出B站登录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    val biliApi = com.qian.jianyin.bili.BiliApi.getInstance(context)
                    biliApi.clearCookies()
                    vm.biliLoginState.value = MusicViewModel.BiliLoginState.NotLoggedIn
                    onDismiss()
                    Toast.makeText(context, "已退出B站登录", Toast.LENGTH_SHORT).show()
                }) { Text("退出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }
}

@Composable
fun BackupDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    if (show) {
        val backupManager = remember { BackupManager(context) }
        val backupFiles = remember { backupManager.getBackupFiles() }
        var isBackingUp by remember { mutableStateOf(false) }
        var backupResult by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("备份与恢复") },
            text = {
                Column {
                    Text("备份内容包括：同步的歌单、收藏的歌曲、听歌次数、历史记录")
                    Spacer(modifier = Modifier.height(16.dp))

                    // 备份按钮
                    Button(
                        onClick = {
                            scope.launch {
                                isBackingUp = true
                                try {
                                    val backupPath = backupManager.backupData()
                                    backupResult = "备份成功：$backupPath"
                                    Toast.makeText(context, "备份成功", Toast.LENGTH_SHORT)
                                        .show()
                                } catch (e: Exception) {
                                    backupResult = "备份失败：${e.message}"
                                    Toast.makeText(context, "备份失败", Toast.LENGTH_SHORT)
                                        .show()
                                } finally {
                                    isBackingUp = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBackingUp
                    ) {
                        if (isBackingUp) {
                            Text("备份中...")
                        } else {
                            Text("创建备份")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 恢复选项
                    if (backupFiles.isNotEmpty()) {
                        Text("恢复备份：")
                        Spacer(modifier = Modifier.height(8.dp))

                        backupFiles.forEach { file ->
                            val fileName = file.name
                            val lastModified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                .format(java.util.Date(file.lastModified()))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val restored = backupManager.restoreData(file)
                                        if (restored) {
                                            Toast.makeText(
                                                context,
                                                "恢复成功，重启应用生效！",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "恢复失败",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.FileOpen,
                                    null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fileName, fontSize = 14.sp)
                                    Text(
                                        lastModified,
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else {
                        Text("没有找到备份文件")
                    }

                    if (backupResult != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            backupResult!!,
                            fontSize = 14.sp,
                            color = if (backupResult!!.contains("成功")) colorScheme.primary else colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
