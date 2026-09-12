package com.qian.jianyin

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.qian.jianyin.ui.ThemeColorUtil
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
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
import kotlin.math.abs
import kotlin.math.max
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.core.Angle
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Spread
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import java.util.concurrent.TimeUnit
import androidx.compose.ui.graphics.toArgb

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
    val totalPages = 7
    
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
                    4 -> LoginPage()
                    5 -> StylesPage()
                    6 -> CompletePage()
                }
            }
        }
        
        // 页面指示器（发光胶囊进度条 + 页数）
        OnboardingProgressIndicator(
            progress = (currentPage + 1).toFloat() / totalPages,
            page = currentPage + 1,
            total = totalPages,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        )
        
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
 * 发光胶囊进度条 + 页数指示：轨道背景 + 主色渐变填充 + 骑在进度端点上的发光滑块圆点，右侧显示当前页/总页数。
 */
@Composable
private fun OnboardingProgressIndicator(
    progress: Float,
    page: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = EaseOutQuad),
        label = "glowProgress"
    )
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
        ) {
            // 轨道背景
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(cs.surfaceVariant)
            )
            // 已填充（主色）
            Box(
                Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(cs.primary)
            )
            // 发光滑块（骑在进度端点）
            Box(
                Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(10.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(Modifier.size(26.dp).background(cs.primary.copy(alpha = 0.3f), CircleShape))
                Box(Modifier.size(14.dp).background(cs.primary, CircleShape))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "$page / $total",
            color = cs.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 欢迎页面
 * 
 * 显示应用的欢迎信息，包括应用图标、欢迎文本和使用说明。
 */
@Composable
fun WelcomePage() {

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
                .size(120.dp)
                .clip(RoundedCornerShape(28.dp))
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 标题：欢迎
        Text(
            text = "欢迎",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 打字效果：欢迎使用简音
        TypingText(text = "欢迎使用简音")
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 欢迎信息
        Text(
            text = "这是一个简单的音乐软件，使用网易云作为在线歌曲来源，目的是让音乐回归本质",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 感谢信息
        Text(
            text = "软件不提供任免费歌曲服务，只提供播放功能",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // 引导信息
        Text(
            text = "接下来我们需要进行一些设置和认识，方便您使用此app",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/**
 * 主视觉占位插画：主题色渐变块 + 居中图标 + 装饰圆点。
 * 随主题（含动态配色）自动变色，后续有真实插画素材时可整体替换本组件。
 */
@Composable
private fun OnboardingHero(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    height: Dp = 128.dp
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(height)
            .background(
                Brush.linearGradient(
                    listOf(cs.primaryContainer, cs.tertiaryContainer.copy(alpha = 0.5f))
                ),
                RoundedCornerShape(32.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // 左上装饰圆
        Box(
            modifier = Modifier
                .size(100.dp)
                .align(Alignment.TopStart)
                .offset(x = (-24).dp, y = (-24).dp)
                .background(cs.primary.copy(alpha = 0.16f), RoundedCornerShape(50.dp))
        )
        // 右下装饰圆
        Box(
            modifier = Modifier
                .size(150.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 40.dp, y = 36.dp)
                .background(cs.secondaryContainer.copy(alpha = 0.4f), RoundedCornerShape(75.dp))
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = cs.onPrimaryContainer.copy(alpha = 0.92f)
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
    
    // 存储权限请求
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "存储权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "存储权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }
    
    // 通知权限请求
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "通知权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "通知权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 请求管理所有文件权限
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
            Toast.makeText(context, "请授予简音管理所有文件的权限", Toast.LENGTH_SHORT).show()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+ 请求运行时权限
            storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    
    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 请求通知权限
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+ 请求运行时权限
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    fun requestBackgroundPermission() {
        // 后台权限需要跳转到设置页面
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OnboardingHero(icon = Icons.Filled.Notifications, height = 130.dp)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 权限设置说明
        Text(
            text = "我们接下来进行权限设置，这可以帮助您更好的使用app",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OnboardingHero(icon = Icons.Filled.Download, height = 150.dp)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 下载目录说明
        Text(
            text = "您默认下载的歌曲文件在根目录的download文件夹下的jianyin文件夹内",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 自定义设置说明
        Text(
            text = "如果您想，您可以在设置中找到下载目录设置进行设置，请确保目录存在",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
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
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "默认路径",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
                Text(
                    text = "/storage/emulated/0/Download/jianyin",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
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
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OnboardingHero(icon = Icons.Filled.Backup, height = 150.dp)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 备份说明
        Text(
            text = "如果您不想丢失您的数据，您可以对数据进行备份，此项可以设置中找到",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
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
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "备份内容",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Text(
                    text = "• 播放历史\n• 收藏歌曲\n• 播放列表\n• 下载设置",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
        }
    }
}

/**
 * 登录账号页面
 * 
 * 显示账号登录相关信息，引导用户在设置中登录账号。
 */
@Composable
fun LoginPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "登录账号",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        OnboardingHero(icon = Icons.Filled.AccountCircle, height = 150.dp)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 登录说明
        Text(
            text = "你可以在设置里登录账号，以获取个性化功能以及平台的部分资源",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
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
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "登录",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "登录优势",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Text(
                    text = "• 同步个人收藏和播放列表\n• 获取平台会员专属资源\n• 个性化推荐内容",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
        }
    }
}

/**
 * 样式设置页面
 * 
 * 让用户在引导阶段选择喜欢的主题色和播放器进度条样式，主题色与设置页保持一致（HSV 取色）。
 */
@Composable
fun StylesPage() {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var showColorDialog by remember { mutableStateOf(false) }

    // 播放器进度条样式
    var progressStyle by remember { mutableStateOf(readPlayerProgressStyle(context)) }

    // 当前主题色（实时跟随存储）
    val seedColorState by DownloadSettingsStore.seedColorFlow.collectAsState()
    val themeSourceState by DownloadSettingsStore.themeSourceFlow.collectAsState()

    val appliedColor = if (seedColorState != 0L) {
        ThemeColorUtil.seedLongToColor(seedColorState)
    } else {
        colorScheme.primary
    }
    val modeLabel = when (themeSourceState) {
        2 -> "跟随专辑封面"
        1 -> "壁纸取色"
        3 -> "自定义"
        else -> "内置配色"
    }

    fun applyStyle(bgCtx: android.content.Context, style: ProgressBarStyle) {
        bgCtx.getSharedPreferences("music_player_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString("progress_bar_style", style.name).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "样式设置",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "选择你喜欢的主题色和播放器进度条样式，之后可以在设置中再次修改",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colorScheme.onBackground
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── 主题色（点击弹出 HSV 取色弹窗）──
        Text(
            text = "主题色",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            ),
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colorScheme.surfaceVariant)
                .clickable { showColorDialog = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(appliedColor, RoundedCornerShape(12.dp))
                    .border(2.dp, colorScheme.outline, RoundedCornerShape(12.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("当前颜色", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colorScheme.onBackground)
                Text(
                    "#%02X%02X%02X".format(
                        (appliedColor.toArgb() shr 16) and 0xFF,
                        (appliedColor.toArgb() shr 8) and 0xFF,
                        appliedColor.toArgb() and 0xFF
                    ),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Text(
                modeLabel,
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Icon(Icons.Filled.Colorize, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Text(
            "点击上方区域自定义颜色",
            fontSize = 12.sp,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 壁纸取色 / 专辑封面取色
        Row {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val res = ThemeColorUtil.extractFromWallpaper(context)
                        if (res != null) {
                            DownloadSettingsStore.setSeedColor(context, res)
                            DownloadSettingsStore.setThemeSource(context, 1)
                            Toast.makeText(context, "已从壁纸取色", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "无法读取系统壁纸", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("壁纸取色")
            }
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(
                onClick = {
                    DownloadSettingsStore.setThemeSource(context, 2)
                    Toast.makeText(context, "已切换为跟随专辑封面", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Album, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("专辑取色")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── 播放器进度条样式选择 ──
        Text(
            text = "播放器进度条样式",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            ),
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row {
            PlayerStyleCard(
                label = "默认",
                style = ProgressBarStyle.DEFAULT,
                selected = progressStyle == ProgressBarStyle.DEFAULT
            ) { style -> progressStyle = style; applyStyle(context, style) }
            Spacer(modifier = Modifier.width(10.dp))
            PlayerStyleCard(
                label = "圆条",
                style = ProgressBarStyle.ROUND,
                selected = progressStyle == ProgressBarStyle.ROUND
            ) { style -> progressStyle = style; applyStyle(context, style) }
            Spacer(modifier = Modifier.width(10.dp))
            PlayerStyleCard(
                label = "波形",
                style = ProgressBarStyle.AUDIO,
                selected = progressStyle == ProgressBarStyle.AUDIO
            ) { style -> progressStyle = style; applyStyle(context, style) }
        }
    }

    ColorPickerDialog(
        show = showColorDialog,
        initialSeed = if (seedColorState != 0L) seedColorState else appliedColor.toArgb().toLong() and 0xFFFFFFFFL,
        onApply = { argbLong ->
            DownloadSettingsStore.setSeedColor(context, argbLong)
            DownloadSettingsStore.setThemeSource(context, 3)
        },
        onDismiss = { showColorDialog = false }
    )
}

/**
 * 主题色 HSV 选择弹窗（与设置页取色 UI 一致）
 */
@Composable
private fun ColorPickerDialog(
    show: Boolean,
    initialSeed: Long,
    onApply: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    val initHsv = FloatArray(3).also {
        android.graphics.Color.colorToHSV(initialSeed.toInt(), it)
    }
    var hue by remember(initialSeed) { mutableFloatStateOf(initHsv[0]) }
    var saturation by remember(initialSeed) { mutableFloatStateOf(initHsv[1]) }
    var brightness by remember(initialSeed) { mutableFloatStateOf(initHsv[2]) }

    val previewColor = remember(hue, saturation, brightness) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    }

    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("自定义主题色") },
            text = {
                Column {
                    // 颜色预览
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(previewColor, RoundedCornerShape(12.dp))
                                .border(2.dp, colorScheme.outline, RoundedCornerShape(12.dp))
                        )
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // 快速选择
                    Text("快速选择", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            0xFFFF5252.toInt(), 0xFFFF7043.toInt(), 0xFFFFA726.toInt(), 0xFF66BB6A.toInt(),
                            0xFF26C6DA.toInt(), 0xFF42A5F5.toInt(), 0xFF5C6BC0.toInt(), 0xFFAB47BC.toInt(),
                        ).forEach { colorInt ->
                            val presetHsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(colorInt, presetHsv)
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .background(Color(colorInt), CircleShape)
                                    .border(
                                        2.dp,
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // 色调
                    Text("色调", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))),
                            activeTrackColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                        )
                    )
                    // 饱和度
                    Text("饱和度", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    Slider(
                        value = saturation,
                        onValueChange = { saturation = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = previewColor, activeTrackColor = previewColor)
                    )
                    // 亮度
                    Text("亮度", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(thumbColor = previewColor, activeTrackColor = previewColor)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onApply(previewColor.toArgb().toLong() and 0xFFFFFFFFL)
                    onDismiss()
                    Toast.makeText(context, "已应用主题色", Toast.LENGTH_SHORT).show()
                }) {
                    Text("保存")
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

private fun readPlayerProgressStyle(context: android.content.Context): ProgressBarStyle {
    val saved = context.getSharedPreferences("music_player_prefs", android.content.Context.MODE_PRIVATE)
        .getString("progress_bar_style", ProgressBarStyle.AUDIO.name)
    return try {
        ProgressBarStyle.valueOf(saved ?: ProgressBarStyle.AUDIO.name)
    } catch (_: Exception) {
        ProgressBarStyle.AUDIO
    }
}

@Composable
private fun RowScope.PlayerStyleCard(
    label: String,
    style: ProgressBarStyle,
    selected: Boolean,
    onSelect: (ProgressBarStyle) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant)
            .clickable(onClick = { onSelect(style) })
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 样式小预览（统一高度保持一致）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            when (style) {
                ProgressBarStyle.DEFAULT -> {
                    Slider(
                        value = 0.6f,
                        onValueChange = {},
                        colors = SliderDefaults.colors(
                            thumbColor = colorScheme.primary,
                            activeTrackColor = colorScheme.primary,
                            inactiveTrackColor = colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ProgressBarStyle.ROUND -> {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(colorScheme.outlineVariant))
                        Box(Modifier.fillMaxWidth(0.6f).height(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(colorScheme.primary))
                    }
                }
                ProgressBarStyle.AUDIO -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(7) { i ->
                            val h = if (i / 7f < 0.6f) (7 + (i % 3) * 4).dp else 3.dp
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(h)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(if (i / 7f < 0.6f) colorScheme.primary else colorScheme.outlineVariant)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(
                color = if (selected) colorScheme.primary else colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/**
 * 完成页面
 * 
 * 显示引导完成的提示信息，引导用户开始使用应用。
 */
@Composable
fun CompletePage() {
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary
    
    // 纸屑颜色列表（基于主色调调和）
    val confettiColors = remember(primaryColor) {
        listOf(
            androidx.compose.ui.graphics.Color(0xfffce18a),
            androidx.compose.ui.graphics.Color(0xFF009688),
            androidx.compose.ui.graphics.Color(0xfff4306d),
            androidx.compose.ui.graphics.Color(0xffb48def),
            androidx.compose.ui.graphics.Color(0xFF95FF82),
            androidx.compose.ui.graphics.Color(0xFF82ECFF),
            androidx.compose.ui.graphics.Color(0xFFFF9800),
            androidx.compose.ui.graphics.Color(0xFF0E008A)
        ).map { it.toArgb() }
    }
    
    // Default 粒子效果（参考 ImageToolbox）
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
    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 纸屑效果
        KonfettiView(
            modifier = Modifier.fillMaxSize(),
            parties = parties
        )
        
        // 内容
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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 完成说明
            Text(
                text = "设置完成，让我们开始吧",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}



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
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    }
}

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
        style = MaterialTheme.typography.headlineSmall.copy(
            color = MaterialTheme.colorScheme.onBackground
        ),
        textAlign = TextAlign.Center
    )
}

