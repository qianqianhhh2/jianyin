package com.qian.jianyin

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qian.jianyin.ui.ThemeColorUtil
import com.qian.jianyin.util.VibrationManager
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BackupAudioDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    backupAudioApiUrl: String,
    onBackupAudioApiUrlChange: (String) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("备用音源设置（实验性功能）") },
            text = {
                Column {
                    Text("当官方音源无法播放时，将使用备用音源。", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = backupAudioApiUrl,
                        onValueChange = onBackupAudioApiUrlChange,
                        label = { Text("输入API 地址") },
                        placeholder = { Text("请自行获取") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "依照meting-api格式解析，实验性功能，可能不稳定",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (backupAudioApiUrl.isNotBlank()) {
                        TextButton(onClick = {
                            onBackupAudioApiUrlChange("")
                            DownloadSettingsStore.setBackupAudioApiUrl(context, "")
                            onDismiss()
                            Toast.makeText(context, "已清除备用音源", Toast.LENGTH_SHORT).show()
                        }) {
                            Text("清除")
                        }
                    }
                    TextButton(onClick = {
                        DownloadSettingsStore.setBackupAudioApiUrl(context, backupAudioApiUrl)
                        onDismiss()
                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("保存")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun DownloadPathDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var useCustomPath by remember { mutableStateOf(false) }
    var customUri by remember { mutableStateOf<Uri?>(null) }

    if (show) {
        LaunchedEffect(Unit) {
            useCustomPath = DownloadSettingsStore.isUsingCustomPath(context)
            customUri = DownloadSettingsStore.getCustomUri(context)
        }

        fun getUriPath(uri: Uri?): String {
            if (uri == null) return "未选择"
            return uri.path ?: uri.toString()
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("下载位置设置") },
            text = {
                Column {
                    // 使用默认路径选项
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            useCustomPath = false
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !useCustomPath,
                            onClick = { useCustomPath = false },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorScheme.primary
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("使用默认路径")
                            Text(
                                DownloadManager.getDownloadDirectory(context).absolutePath,
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 使用自定义路径选项（SAF授权）
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            useCustomPath = true
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = useCustomPath,
                            onClick = { useCustomPath = true },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorScheme.primary
                            )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("使用自定义路径")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    getUriPath(customUri),
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                if (useCustomPath) {
                                    IconButton(onClick = {
                                        // 打开SAF文件选择器
                                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                        (context as? MainActivity)?.downloadPathCallback = { uri ->
                                            customUri = uri
                                        }
                                        (context as? Activity)?.startActivityForResult(intent, 1004)
                                    }) {
                                        Icon(Icons.Default.FolderOpen, null, tint = colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    if (useCustomPath) {
                        Text(
                            "提示：选择的目录需要授权写入权限",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (useCustomPath) {
                        DownloadSettingsStore.setCustomUri(context, customUri)
                    } else {
                        DownloadSettingsStore.setCustomUri(context, null)
                    }
                    onDismiss()
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQualityScreen(
    visible: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var selectedDownloadQuality by remember { mutableStateOf(DownloadSettingsStore.getDownloadQuality(context)) }
    var selectedPlayQuality by remember { mutableStateOf(DownloadSettingsStore.getPlayQuality(context)) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CenterAlignedTopAppBar(
                    title = {
                        Text("音质设置", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null)
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            DownloadSettingsStore.setDownloadQuality(context, selectedDownloadQuality)
                            DownloadSettingsStore.setPlayQuality(context, selectedPlayQuality)
                            onBack()
                        }) {
                            Text("完成", color = colorScheme.primary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = colorScheme.background,
                        titleContentColor = colorScheme.onBackground,
                        navigationIconContentColor = colorScheme.primary
                    )
                )

                HorizontalDivider(color = colorScheme.surfaceVariant.copy(alpha = 0.3f))

                val qualityOptions = DownloadSettingsStore.qualityOptions

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 下载音质分组
                    ListItem(
                        headlineContent = {
                            Text("下载音质", fontWeight = FontWeight.Bold, color = colorScheme.primary)
                        },
                        supportingContent = {
                            Text("需登录网易云账号", color = colorScheme.onSurfaceVariant)
                        }
                    )

                    qualityOptions.forEach { quality ->
                        ListItem(
                            headlineContent = {
                                Text(DownloadSettingsStore.netEaseQualityLabel(quality))
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedDownloadQuality == quality,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                                )
                            },
                            modifier = Modifier.clickable { selectedDownloadQuality = quality }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // 播放音质分组
                    ListItem(
                        headlineContent = {
                            Text("播放音质", fontWeight = FontWeight.Bold, color = colorScheme.primary)
                        }
                    )

                    qualityOptions.forEach { quality ->
                        ListItem(
                            headlineContent = {
                                Text(DownloadSettingsStore.netEaseQualityLabel(quality))
                            },
                            leadingContent = {
                                RadioButton(
                                    selected = selectedPlayQuality == quality,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                                )
                            },
                            modifier = Modifier.clickable { selectedPlayQuality = quality }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ListItem(
                        headlineContent = {
                            Text(
                                "高音质需登录网易云账号，部分歌曲可能因版权或会员限制而降级",
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    )

                    Spacer(Modifier.navigationBarsPadding().height(160.dp))
                }
            }
        }
    }
}

@Composable
fun LyricSourceDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    selectedLyricSource: Int,
    onSelectedLyricSourceChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("本地音乐歌词来源") },
            text = {
                Column {
                    // 内嵌歌词选项
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSelectedLyricSourceChange(0)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLyricSource == 0,
                            onClick = { onSelectedLyricSourceChange(0) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorScheme.primary
                            )
                        )
                        Text("内嵌", modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))

                    // 网络歌词选项
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSelectedLyricSourceChange(1)
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLyricSource == 1,
                            onClick = { onSelectedLyricSourceChange(1) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = colorScheme.primary
                            )
                        )
                        Text("网络", modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))

                    // 提示信息
                    Text(
                        "提示：请保证歌曲名字和歌手正确，以获得最佳歌词匹配效果",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    DownloadSettingsStore.setLyricSource(context, selectedLyricSource)
                    onDismiss()
                }) {
                    Text("确定")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun LyricFontSizeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    lyricFontSize: Float,
    onLyricFontSizeChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    if (show) {
        val tempFontSize = remember { mutableStateOf(lyricFontSize) }
        val lastFontSizeValue = remember { mutableStateOf(tempFontSize.value) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("歌词字体大小") },
            text = {
                Column {
                    // 预览区域
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .clip(RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "预览歌词文字",
                            fontSize = tempFontSize.value.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground
                        )
                    }
                    Spacer(Modifier.height(16.dp))

                    // 拖动条
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("12sp", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        Slider(
                            value = tempFontSize.value,
                            onValueChange = { newValue ->
                                // 计算最近的档位值（每1sp一个档位）
                                val steppedValue = newValue.roundToInt().toFloat()
                                tempFontSize.value = steppedValue.coerceIn(12f, 32f)

                                // 只在档位变化时触发微振动（模拟齿轮感）
                                if (tempFontSize.value != lastFontSizeValue.value) {
                                    lastFontSizeValue.value = tempFontSize.value
                                    VibrationManager.tick(context)
                                }
                            },
                            valueRange = 12f..32f,
                            steps = 19, // 12到32共21个值，步长1，steps=19
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            colors = SliderDefaults.colors(
                                activeTrackColor = colorScheme.primary,
                                thumbColor = colorScheme.primary
                            )
                        )
                        Text("32sp", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    }

                    // 当前值显示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "当前: ${tempFontSize.value.toInt()}sp",
                            fontSize = 14.sp,
                            color = colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 恢复默认按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = {
                            tempFontSize.value = 18f
                        }) {
                            Text(
                                "恢复默认",
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onLyricFontSizeChange(tempFontSize.value)
                    PlaybackSettingsStore.setLyricFontSize(context, tempFontSize.value)
                    onDismiss()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun GradientBrightnessDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    gradientBrightnessMultiplier: Float,
    onGradientBrightnessMultiplierChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    if (show) {
        val tempMultiplier = remember { mutableStateOf(gradientBrightnessMultiplier) }
        val lastMultiplierValue = remember { mutableStateOf(tempMultiplier.value) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("渐变层亮度") },
            text = {
                Column {
                    Text(
                        text = "调整全屏播放器背景渐变层亮度",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("暗", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = tempMultiplier.value,
                            onValueChange = { newValue ->
                                // 计算最近的档位值（每5%一个档位）
                                val steppedValue = ((newValue * 100).roundToInt() / 5 * 5) / 100f
                                tempMultiplier.value = steppedValue.coerceIn(0.1f, 2.0f)

                                // 只在档位变化时触发微振动（模拟齿轮感）
                                if (tempMultiplier.value != lastMultiplierValue.value) {
                                    lastMultiplierValue.value = tempMultiplier.value
                                    VibrationManager.tick(context)
                                }
                            },
                            valueRange = 0.1f..2.0f,
                            steps = 37, // 10%到200%，每5%一个档位，共38档（37个间隔）
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("亮", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "当前亮度: ${(tempMultiplier.value * 100).toInt()}%",
                        color = colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "提示：过高亮度可能会影响可视度！",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onGradientBrightnessMultiplierChange(tempMultiplier.value)
                    PlaybackSettingsStore.setGradientBrightnessMultiplier(context, tempMultiplier.value)
                    onDismiss()
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
            containerColor = colorScheme.surface
        )
    }
}

@Composable
fun DarkModeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    selectedDarkMode: Int,
    onSelectedDarkModeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("深色模式") },
            text = {
                Column {
                    val options = listOf(
                        0 to "跟随系统",
                        1 to "浅色",
                        2 to "深色"
                    )
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSelectedDarkModeChange(mode)
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDarkMode == mode,
                                onClick = { onSelectedDarkModeChange(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colorScheme.primary
                                )
                            )
                            Text(label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    DownloadSettingsStore.setDarkMode(context, selectedDarkMode)
                    onDismiss()
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun ThemeDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    selectedThemeSource: Int,
    onSelectedThemeSourceChange: (Int) -> Unit,
    selectedSeedColor: Long,
    onSelectedSeedColorChange: (Long) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    if (show) {
        var isExtracting by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        // 切换到壁纸取色时自动提取
        LaunchedEffect(selectedThemeSource) {
            if (selectedThemeSource == 1 && selectedSeedColor == 0L) {
                isExtracting = true
                scope.launch {
                    val result = ThemeColorUtil.extractFromWallpaper(context)
                    if (result != null) {
                        onSelectedSeedColorChange(result)
                    } else {
                        Toast.makeText(context, "无法读取系统壁纸", Toast.LENGTH_SHORT).show()
                    }
                    isExtracting = false
                }
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("主题色") },
            text = {
                Column {
                    val options = listOf(
                        0 to "内置配色",
                        1 to "壁纸取色",
                        2 to "专辑封面",
                        3 to "自定义"
                    )
                    options.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSelectedThemeSourceChange(mode)
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedThemeSource == mode,
                                onClick = { onSelectedThemeSourceChange(mode) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colorScheme.primary
                                )
                            )
                            Text(label, modifier = Modifier.weight(1f))
                        }
                    }

                    // 专辑封面说明
                    if (selectedThemeSource == 2) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "自动从专辑封面提取颜色",
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }

                    // 种子色预览色块（壁纸取色）
                    if (selectedThemeSource == 1 && selectedSeedColor != 0L) {
                        Spacer(Modifier.height(16.dp))
                        Text("取色预览", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        val seedColor = ThemeColorUtil.seedLongToColor(selectedSeedColor)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ColorSwatch(seedColor, "主色")
                            ColorSwatch(
                                seedColor.let { c ->
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(c.toArgb(), hsv)
                                    Color(android.graphics.Color.HSVToColor(floatArrayOf((hsv[0] + 30f) % 360f, 0.35f, 0.75f)))
                                },
                                "辅色"
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                isExtracting = true
                                scope.launch {
                                    val result = ThemeColorUtil.extractFromWallpaper(context)
                                    if (result != null) {
                                        onSelectedSeedColorChange(result)
                                    } else {
                                        Toast.makeText(context, "无法读取系统壁纸", Toast.LENGTH_SHORT).show()
                                    }
                                    isExtracting = false
                                }
                            },
                            enabled = !isExtracting,
                            colors = ButtonDefaults.buttonColors(containerColor = seedColor)
                        ) {
                            if (isExtracting) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("重新取色")
                        }
                    } else if (selectedThemeSource == 1 && isExtracting) {
                        Spacer(Modifier.height(24.dp))
                        CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        Text("正在从系统壁纸取色...", fontSize = 13.sp, color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                    }

                    // 用户自定义颜色选择器
                    if (selectedThemeSource == 3) {
                        Spacer(Modifier.height(12.dp))
                        val currentColor = ThemeColorUtil.seedLongToColor(
                            if (selectedSeedColor != 0L) selectedSeedColor
                            else colorScheme.primary.toArgb().toLong() and 0xFFFFFFFFL
                        )
                        val currentHsv = remember(currentColor) {
                            FloatArray(3).also { android.graphics.Color.colorToHSV(currentColor.toArgb(), it) }
                        }
                        var hue by remember(currentHsv[0]) { mutableFloatStateOf(currentHsv[0]) }
                        var saturation by remember(currentHsv[1]) { mutableFloatStateOf(currentHsv[1]) }
                        var brightness by remember(currentHsv[2]) { mutableFloatStateOf(currentHsv[2]) }

                        val previewColor = remember(hue, saturation, brightness) {
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
                        }
                        onSelectedSeedColorChange(previewColor.toArgb().toLong() and 0xFFFFFFFFL)

                        // 颜色预览
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(48.dp).background(previewColor, RoundedCornerShape(12.dp)).border(2.dp, colorScheme.outline, RoundedCornerShape(12.dp)))
                            Column {
                                Text("选择颜色", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onSurface)
                                Text(
                                    "#%02X%02X%02X".format(
                                        (previewColor.toArgb() shr 16) and 0xFF,
                                        (previewColor.toArgb() shr 8) and 0xFF,
                                        previewColor.toArgb() and 0xFF
                                    ),
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // 预设色块
                        Text("快速选择", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        val presets = listOf(
                            0xFFFF5252.toInt(), 0xFFFF7043.toInt(), 0xFFFFA726.toInt(), 0xFF66BB6A.toInt(),
                            0xFF26C6DA.toInt(), 0xFF42A5F5.toInt(), 0xFF5C6BC0.toInt(), 0xFFAB47BC.toInt(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            presets.forEach { colorInt ->
                                val col = Color(colorInt)
                                val presetHsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(colorInt, presetHsv)
                                Box(
                                    Modifier.size(32.dp).background(col, CircleShape).border(2.dp,
                                        if (abs(hue - presetHsv[0]) < 2f) colorScheme.primary else Color.Transparent,
                                        CircleShape
                                    )
                                        .clickable {
                                            hue = presetHsv[0]
                                            saturation = 1f
                                            brightness = 1f
                                        }
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // 色调滑块
                        Text("色调", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = hue,
                            onValueChange = { hue = it },
                            valueRange = 0f..360f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))),
                                activeTrackColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                            )
                        )

                        // 饱和度滑块
                        Text("饱和度", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = saturation,
                            onValueChange = { saturation = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = previewColor,
                                activeTrackColor = previewColor
                            )
                        )

                        // 亮度滑块
                        Text("亮度", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = brightness,
                            onValueChange = { brightness = it },
                            valueRange = 0.1f..1f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = previewColor,
                                activeTrackColor = previewColor
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (selectedThemeSource) {
                        2 -> DownloadSettingsStore.setThemeSource(context, 2)
                        3, 1 -> {
                            DownloadSettingsStore.setThemeSource(context, selectedThemeSource)
                            DownloadSettingsStore.setSeedColor(context, selectedSeedColor)
                        }
                        else -> {
                            DownloadSettingsStore.setThemeSource(context, 0)
                            DownloadSettingsStore.setSeedColor(context, 0L)
                        }
                    }
                    onDismiss()
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun StartupSettingsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    keepPlaylistOnExitEnabled: Boolean,
    onKeepPlaylistOnExitEnabledChange: (Boolean) -> Unit,
    autoPlayOnStartEnabled: Boolean,
    onAutoPlayOnStartEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("启动设置") },
            text = {
                Column {
                    // 离开后保留列表
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("离开后保留列表")
                            Text(
                                "保留当前播放队列，下次打开应用时恢复",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = keepPlaylistOnExitEnabled,
                            onCheckedChange = { enabled ->
                                onKeepPlaylistOnExitEnabledChange(enabled)
                                DownloadSettingsStore.setKeepPlaylistOnExitEnabled(context, enabled)
                                // 如果关闭保留列表，同时关闭启动时播放
                                if (!enabled && autoPlayOnStartEnabled) {
                                    onAutoPlayOnStartEnabledChange(false)
                                    DownloadSettingsStore.setAutoPlayOnStartEnabled(context, false)
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = colorScheme.surfaceVariant.copy(alpha = 0.3f))

                    // 启动时播放
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启动时播放")
                            Text(
                                "打开应用后自动播放上次的歌曲",
                                fontSize = 12.sp,
                                color = if (keepPlaylistOnExitEnabled) colorScheme.onSurfaceVariant else Color.Gray
                            )
                        }
                        Switch(
                            checked = autoPlayOnStartEnabled,
                            onCheckedChange = { enabled ->
                                if (keepPlaylistOnExitEnabled) {
                                    onAutoPlayOnStartEnabledChange(enabled)
                                    DownloadSettingsStore.setAutoPlayOnStartEnabled(context, enabled)
                                }
                            },
                            enabled = keepPlaylistOnExitEnabled
                        )
                    }

                    // 提示信息
                    if (!keepPlaylistOnExitEnabled) {
                        Text(
                            "提示：需要先开启「离开后保留列表」才能使用此功能",
                            fontSize = 12.sp,
                            color = colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("确定")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
