package com.qian.jianyin

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    visible: Boolean,
    onBack: () -> Unit,
    appVersion: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showVersionUpdateDialog by remember { mutableStateOf(false) }
    var versionUpdateInfo by remember { mutableStateOf<VersionUpdate?>(null) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部导航栏
                CenterAlignedTopAppBar(
                    title = {
                        Text("关于", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, null)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = colorScheme.background,
                        titleContentColor = colorScheme.onBackground,
                        navigationIconContentColor = colorScheme.primary
                    )
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // 应用图标和名称
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp)
                        ) {
                            // 应用图标 - 使用 drawable 图片
                            Image(
                                painter = painterResource(id = R.drawable.icon),  // 注意：使用您实际的应用图标资源名称
                                contentDescription = "简音图标",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(24.dp))
                            )

                            Spacer(Modifier.height(24.dp))

                            Text(
                                "简音",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onBackground
                            )

                            Text(
                                "版本 $appVersion",
                                color = colorScheme.primary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            // 检查更新按钮
                            Button(
                                onClick = {
                                    scope.launch {
                                        isCheckingUpdate = true
                                        val versionChecker = VersionChecker(context)
                                        val updateInfo = versionChecker.checkForUpdates()
                                        if (updateInfo != null) {
                                            versionUpdateInfo = updateInfo
                                            showVersionUpdateDialog = true
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "当前已是最新版本",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        isCheckingUpdate = false
                                    }
                                },
                                modifier = Modifier
                                    .padding(top = 20.dp)
                                    .height(48.dp)
                                    .width(200.dp),
                                enabled = !isCheckingUpdate
                            ) {
                                if (isCheckingUpdate) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text("检查更新")
                                }
                            }

                            // 应用简介
                            Text(
                                "一个简洁、优雅的音乐播放应用",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 16.dp)
                            )

                            Text(
                                "享受纯净的音乐体验",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .padding(top = 4.dp)
                            )

                            }
                    }

                    // 开发者列表
                    item {
                        Text(
                            "开发团队",
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // 可左右滑动的开发者列表
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // 开发者1：谦谦TWT
                            item {
                                DeveloperCard(
                                    avatarRes = R.drawable.dev_icon,
                                    name = "谦谦TWT",
                                    role = "主要开发者",
                                    description = "miku到底是蓝的还是绿的呢",
                                    githubUrl = "https://github.com/qianqianhhh2"
                                )
                            }

                            // 开发者2
                            item {
                                DeveloperCard(
                                    avatarRes = R.drawable.fairy,
                                    name = "Fairy",
                                    role = "主要编码",
                                    description = "中二病犯了用LLM做的智能体",
                                    githubUrl = null
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }

                    // GitHub链接
                    item {
                        Text(
                            "项目信息",
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Surface(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/qianqianhhh2/jianyin")
                                )
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            color = colorScheme.surfaceColorAtElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.github), // 您的图片名称
                                        contentDescription = "GitHub",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp)
                                ) {
                                    Text(
                                        "GitHub 项目",
                                        color = colorScheme.onBackground,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "查看源代码、报告问题和参与贡献",
                                        color = colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }

                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                    }

                    // 赞助一杯咖啡
                    item {
                        Surface(
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://ifdian.net/a/qianqiantwt")
                                )
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.19f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Coffee,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp)
                                ) {
                                    Text(
                                        "赞助一杯咖啡",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "支持项目的持续开发与维护",
                                        color = colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }

                                // 爱心图标
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.19f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // 赞助说明
                        Text(
                            "您的支持将帮助我们持续改进应用，添加新功能",
                            color = colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, start = 4.dp, end = 4.dp)
                        )

                        Spacer(Modifier.height(40.dp))
                    }

                    // 版权信息
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        ) {
                            Divider(
                                color = colorScheme.outline.copy(alpha = 0.1f),
                                thickness = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp)
                            )

                            Text(
                                "© 2026 简音",
                                color = colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                "Made with ❤️ for music lovers",
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    // 底部留白，与我的音乐界面保持一致
                    item {
                        Spacer(Modifier.navigationBarsPadding().height(160.dp))
                    }
                }
            }
        }
    }

    // 版本更新弹窗
    VersionUpdateDialog(
        isVisible = showVersionUpdateDialog,
        versionUpdate = versionUpdateInfo,
        onDismissRequest = { showVersionUpdateDialog = false }
    )
}
