package com.qian.jianyin

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import kotlin.math.max

/**
 * 引导页管理器
 * 
 * 管理应用的首次启动状态，使用 SharedPreferences 存储标记。
 * 
 * @property context 应用上下文
 */
class OnboardingManager(private val context: Context) {
    /**
     * SharedPreferences 实例
     */
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    }
    
    /**
     * 检查是否首次启动
     * 
     * @return 是否首次启动
     */
    fun isFirstLaunch(): Boolean {
        return sharedPreferences.getBoolean("first_launch", true)
    }
    
    /**
     * 标记引导页为已完成
     * 
     * 将首次启动标记设置为 false，下次启动不再显示引导页。
     */
    fun markAsCompleted() {
        sharedPreferences.edit().putBoolean("first_launch", false).apply()
    }
}

/**
 * 引导页屏幕
 * 
 * 显示应用的引导页面，包括欢迎、权限设置、下载设置、备份说明和完成页面。
 * 
 * @param onComplete 引导完成后的回调函数
 */
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val onboardingManager = OnboardingManager(context)
    
    var currentPage by remember { mutableStateOf(0) }
    var isExiting by remember { mutableStateOf(false) }
    val totalPages = 5
    
    val scale by animateFloatAsState(
        targetValue = if (isExiting) 0.8f else 1f,
        animationSpec = tween(300, easing = EaseOutQuad)
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isExiting) 0f else 1f,
        animationSpec = tween(300, easing = EaseOutQuad)
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 页面内容
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally { width -> -width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> width } + fadeOut()
                }
            },
            label = "pageTransition"
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale)
                    .alpha(alpha)
            ) {
                when (it) {
                    0 -> WelcomePage()
                    1 -> PermissionsPage()
                    2 -> DownloadPage()
                    3 -> BackupPage()
                    4 -> CompletePage()
                }
            }
        }
        
        // 页面指示器
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(totalPages) { page ->
                AnimatedVisibility(
                    visible = page == currentPage,
                    enter = scaleIn(animationSpec = tween(300, easing = EaseOutQuad)) +
                            fadeIn(animationSpec = tween(300, easing = EaseOutQuad)),
                    exit = scaleOut(animationSpec = tween(300, easing = EaseInQuad)) +
                            fadeOut(animationSpec = tween(300, easing = EaseInQuad))
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(8.dp)
                            )
                    )
                }
                if (page != currentPage) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
        
        // 导航按钮
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentPage > 0) {
                FilledTonalButton(
                    onClick = { currentPage-- },
                    modifier = Modifier
                        .height(48.dp)
                        .width(120.dp)
                ) {
                    Text(text = "上一步")
                }
            } else {
                Spacer(modifier = Modifier.width(120.dp))
            }
            
            Button(
                onClick = {
                    if (currentPage < totalPages - 1) {
                        currentPage++
                    } else {
                        // 开始退出动画
                        isExiting = true
                        // 延迟执行完成回调
                        CoroutineScope(Dispatchers.Main).launch {
                            delay(300)
                            onboardingManager.markAsCompleted()
                            onComplete()
                        }
                    }
                },
                modifier = Modifier
                    .height(48.dp)
                    .width(120.dp)
            ) {
                Text(text = if (currentPage < totalPages - 1) "下一步" else "开始")
            }
        }
    }
}

/**
 * 欢迎页面
 * 
 * 显示应用的欢迎信息，包括应用图标、欢迎文本和使用说明。
 */
@Composable
fun WelcomePage() {
    val isDarkTheme = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 应用图标
        Image(
            painter = painterResource(id = R.drawable.icon),
            contentDescription = "简音",
            modifier = Modifier
                .size(120.dp),
            colorFilter = if (isDarkTheme) {
                androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary)
            } else null
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 标题：欢迎
        Text(
            text = "欢迎",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 打字效果：欢迎使用简音
        TypingText(text = "欢迎使用简音")
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 欢迎信息
        Text(
            text = "这是一个简单的音乐软件，使用网易云作为在线歌曲来源，目的是让音乐回归本质",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 感谢信息
        Text(
            text = "软件内歌曲免费，感谢祈杰のMeting-API以及Meting-API开源项目",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 引导信息
        Text(
            text = "接下来我们需要进行一些设置和认识，方便您使用此app",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/**
 * 权限设置页面
 * 
 * 显示应用所需的权限列表，并提供权限设置的入口。
 */
@Composable
fun PermissionsPage() {
    val context = LocalContext.current
    
    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        }
    }
    
    fun requestNotificationPermission() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        }
        context.startActivity(intent)
    }
    
    fun requestBackgroundPermission() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        context.startActivity(intent)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "权限设置",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 权限设置说明
        Text(
            text = "我们接下来进行权限设置，这可以帮助您更好的使用app",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 权限列表
        PermissionItem(
            icon = Icons.Filled.MusicNote,
            title = "存储权限",
            description = "用于扫描和管理本地音乐文件",
            onClick = ::requestStoragePermission
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PermissionItem(
            icon = Icons.Filled.Notifications,
            title = "通知权限",
            description = "用于显示播放控制通知",
            onClick = ::requestNotificationPermission
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PermissionItem(
            icon = Icons.Filled.BatteryChargingFull,
            title = "后台权限",
            description = "请在系统设置中允许简音在后台运行，以确保音乐持续播放",
            onClick = ::requestBackgroundPermission
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "重要提示",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "为了确保音乐能够在后台正常播放，特别是在 vivo、OPPO、小米等手机上，请将简音添加到系统白名单中。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/**
 * 下载设置页面
 * 
 * 显示应用的默认下载目录和自定义设置说明。
 */
@Composable
fun DownloadPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "下载设置",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 下载目录说明
        Text(
            text = "您默认下载的歌曲文件在根目录的download文件夹下的jianyin文件夹内",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 自定义设置说明
        Text(
            text = "如果您想，您可以在设置中找到下载目录设置进行设置，请确保目录存在",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "提示",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "默认路径",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Text(
                    text = "/storage/emulated/0/Download/jianyin",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
        }
    }
}

/**
 * 备份页面
 * 
 * 显示应用的数据备份功能说明和备份内容列表。
 */
@Composable
fun BackupPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "备份",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 备份说明
        Text(
            text = "如果您不想丢失您的数据，您可以对数据进行备份，此项可以设置中找到",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Backup,
                        contentDescription = "备份",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "备份内容",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Text(
                    text = "• 播放历史\n• 收藏歌曲\n• 播放列表\n• 下载设置",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
        }
    }
}

/**
 * 完成页面
 * 
 * 显示引导完成的提示信息，引导用户开始使用应用。
 */
@Composable
fun CompletePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "完成",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 完成说明
        Text(
            text = "设置完成，让我们开始吧",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

/**
 * 权限项组件
 * 
 * 显示单个权限的图标、标题和描述，并提供点击事件处理。
 * 
 * @param icon 权限图标
 * @param title 权限标题
 * @param description 权限描述
 * @param onClick 点击事件回调
 */
@Composable
fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 打字效果文本组件
 * 
 * 实现文本的打字动画效果，包括文本的逐字显示和删除效果。
 * 
 * @param text 要显示的文本
 */
@Composable
fun TypingText(text: String) {
    var displayText by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }
    var currentIndex by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            if (!isDeleting) {
                if (currentIndex < text.length) {
                    displayText = text.substring(0, currentIndex + 1)
                    currentIndex++
                    delay(200)
                } else {
                    delay(1000)
                    isDeleting = true
                }
            } else {
                if (currentIndex > 0) {
                    displayText = text.substring(0, currentIndex - 1)
                    currentIndex--
                    delay(100)
                } else {
                    delay(500)
                    isDeleting = false
                }
            }
        }
    }
    
    Text(
        text = displayText,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center
    )
}

