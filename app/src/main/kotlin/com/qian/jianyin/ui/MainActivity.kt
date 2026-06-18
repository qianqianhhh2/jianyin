package com.qian.jianyin

import android.os.Bundle
import android.net.Uri
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import com.qian.jianyin.ui.shapes.MaterialStarShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.ImageLoader
import coil.request.SuccessResult
import coil.decode.GifDecoder
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import com.qian.jianyin.ProgressBarStyle
import com.qian.jianyin.PlaylistQueueItem
import com.qian.jianyin.PlaylistDataStore
import com.qian.jianyin.UserSyncedPlaylist
import com.qian.jianyin.PlaylistSyncManager
import com.qian.jianyin.HomePlaylistInfo
import kotlin.math.cos
import kotlin.math.sin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.app.Activity
import android.view.Window
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.content.Intent
import android.provider.Settings
import android.content.Context
import java.io.File
import com.qian.jianyin.OnboardingManager
import com.qian.jianyin.OnboardingScreen
import com.qian.jianyin.HitokotoManager
import com.qian.jianyin.PermissionManager
import com.qian.jianyin.PermissionCheck
import com.qian.jianyin.VersionChecker
import com.qian.jianyin.VersionUpdate
import com.qian.jianyin.VersionUpdateDialog
import com.qian.jianyin.FirstDayDialog
import com.qian.jianyin.DownloadSettingsStore
import com.qian.jianyin.PlaybackSettingsStore
import com.qian.jianyin.ui.JianYinTheme
import com.qian.jianyin.playback.DesktopLyricService
import com.qian.jianyin.playback.DesktopLyricSettings
import com.qian.jianyin.bili.BiliWebLoginHelper
import androidx.media3.common.util.UnstableApi
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import com.qian.jianyin.R
import androidx.compose.ui.res.painterResource

private fun getRandomPlaceholderId(): Int {
    val ids = listOf(R.drawable.miku_1, R.drawable.miku_2, R.drawable.miku_3, R.drawable.miku_4, R.drawable.miku_5, R.drawable.miku_6, R.drawable.miku_7, R.drawable.miku_8, R.drawable.miku_9)
    return ids.random()
}

@UnstableApi

/**
 * 导航项数据类
 * 用于底部导航栏的导航项
 * @property label 导航项标签
 * @property selectedIcon 选中状态的图标
 * @property unselectedIcon 未选中状态的图标
 */
data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * 启动保活服务
 * 使用 WorkManager 调度保活任务
 * @param context 上下文
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
private fun startKeepAliveServices(context: Context) {
    try {
        KeepAliveWorker.schedule(context)
        Log.d("MainActivity", "保活服务已启动")
    } catch (e: Exception) {
        Log.e("MainActivity", "启动保活服务失败", e)
    }
}

/**
 * 主活动类
 * 应用的入口点，负责初始化和 UI 渲染
 */
class MainActivity : ComponentActivity() {
    // 用于存储文件夹选择的结果
    var folderUriCallback: ((Uri) -> Unit)? = null
    // 用于存储下载路径选择的结果（SAF授权）
    var downloadPathCallback: ((Uri) -> Unit)? = null
    // 用于存储 ViewModel 引用，以便在 onActivityResult 中使用
    private var viewModel: MusicViewModel? = null
    // 待处理的音频 URI（viewModel 初始化前收到）
    private var pendingAudioUri: Uri? = null
    // 用于 B 站登录的 ActivityResultLauncher
    private lateinit var biliLoginLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
    // 用于网易云登录的 ActivityResultLauncher
    private lateinit var neteaseLoginLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent) {
        if (intent.action == android.content.Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                Log.d("MainActivity", "handleIntent: 收到音频文件 intent: $uri")
                // 无论是否设置为默认播放器，都处理外部打开请求
                // 用户从文件管理器选择用本应用打开，就应该播放
                if (viewModel != null) {
                    viewModel?.playExternalAudio(uri)
                } else {
                    pendingAudioUri = uri
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 记录应用启动（用户统计）
        val userStats = UserStatsManager(this)
        userStats.recordAppOpen()
        val isFirstDay = userStats.isFirstDay() && !userStats.isMilestoneShown("first_day")
        val consecutiveDays = userStats.getConsecutiveDays()
        val isWeekMilestone = consecutiveDays == 7 && !userStats.isMilestoneShown("week")
        val isMonthMilestone = consecutiveDays == 30 && !userStats.isMilestoneShown("month")
        val isYearMilestone = consecutiveDays == 365 && !userStats.isMilestoneShown("year")
        
        // 初始化网易云 API
        com.qian.jianyin.netease.api.NeteaseApiService.init(this)
        
        // 初始化 B 站登录的 ActivityResultLauncher
        biliLoginLauncher = registerForActivityResult(
            object : androidx.activity.result.contract.ActivityResultContract<android.content.Intent, String?>() {
                override fun createIntent(context: android.content.Context, input: android.content.Intent): android.content.Intent {
                    return input
                }

                override fun parseResult(resultCode: Int, intent: android.content.Intent?): String? {
                    if (resultCode == android.app.Activity.RESULT_OK) {
                        val cookieJson = intent?.getStringExtra(com.qian.jianyin.bili.BiliWebLoginHelper.Companion.RESULT_COOKIE)
                        Log.d("BiliLogin", "parseResult: 收到Cookie JSON: $cookieJson")
                        return cookieJson
                    }
                    Log.d("BiliLogin", "parseResult: 登录失败，resultCode: $resultCode")
                    return null
                }
            }
        ) { json ->
            Log.d("BiliLogin", "回调: 收到JSON: $json")
            if (json != null) {
                try {
                    val biliApi = com.qian.jianyin.bili.BiliApi.getInstance(this)
                    Log.d("BiliLogin", "回调: 调用saveCookiesFromJson")
                    val saved = biliApi.saveCookiesFromJson(json)
                    Log.d("BiliLogin", "回调: saveCookiesFromJson结果: $saved")
                    if (saved) {
                            android.widget.Toast.makeText(
                                this,
                                "登录成功",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            lifecycleScope.launch {
                                Log.d("BiliLogin", "回调: 验证登录状态")
                                val loginValid = viewModel?.validateBiliLogin()
                                if (loginValid == true) {
                                    Log.d("BiliLogin", "回调: 开始同步 B 站歌单")
                                    val playlists = viewModel?.syncBiliPlaylists()
                                    if (playlists != null && playlists.isNotEmpty()) {
                                        Log.d("BiliLogin", "回调: 同步到 ${playlists.size} 个 B 站收藏")
                                        android.widget.Toast.makeText(
                                            this@MainActivity,
                                            "已获取到 ${playlists.size} 个收藏",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                        // 触发歌单更新，通知 UI 刷新
                                        viewModel?.playlistUpdateTrigger?.intValue = (viewModel?.playlistUpdateTrigger?.intValue ?: 0) + 1
                                    } else {
                                        Log.d("BiliLogin", "回调: 未同步到 B 站收藏")
                                        android.widget.Toast.makeText(
                                            this@MainActivity,
                                            "未获取到收藏",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        } else {
                            android.widget.Toast.makeText(
                                this,
                                "保存登录信息失败",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                } catch (e: Exception) {
                    Log.e("BiliLogin", "回调: 保存Cookie时发生异常", e)
                    android.widget.Toast.makeText(
                        this,
                        "保存登录信息失败: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Log.d("BiliLogin", "回调: 收到null JSON")
            }
        }

        // 初始化网易云登录的 ActivityResultLauncher
        neteaseLoginLauncher = registerForActivityResult(
            object : androidx.activity.result.contract.ActivityResultContract<android.content.Intent, String?>() {
                override fun createIntent(context: android.content.Context, input: android.content.Intent): android.content.Intent {
                    return input
                }

                override fun parseResult(resultCode: Int, intent: android.content.Intent?): String? {
                    if (resultCode == android.app.Activity.RESULT_OK) {
                        val cookieJson = intent?.getStringExtra(com.qian.jianyin.netease.NeteaseWebLoginActivity.RESULT_COOKIE_MAP_JSON)
                        Log.d("NeteaseLogin", "parseResult: 收到Cookie JSON: $cookieJson")
                        return cookieJson
                    }
                    Log.d("NeteaseLogin", "parseResult: 登录失败，resultCode: $resultCode")
                    return null
                }
            }
        ) { json ->
            Log.d("NeteaseLogin", "回调: 收到JSON: $json")
            if (json != null) {
                try {
                    val cookieMap = org.json.JSONObject(json)
                    val cookies = mutableMapOf<String, String>()
                    val keys = cookieMap.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        cookies[key] = cookieMap.getString(key)
                    }
                    val ok = com.qian.jianyin.netease.api.NeteaseApiService.setCookies(cookies)
                    if (ok) {
                        android.widget.Toast.makeText(this, "网易云登录成功", android.widget.Toast.LENGTH_SHORT).show()
                        lifecycleScope.launch {
                            Log.d("NeteaseLogin", "开始同步网易云歌单")
                            val synced = viewModel?.syncNeteaseUserPlaylists()
                            if (synced != null && synced.isNotEmpty()) {
                                android.widget.Toast.makeText(this@MainActivity, "已同步 ${synced.size} 个歌单", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(this@MainActivity, "未发现歌单", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        android.widget.Toast.makeText(this, "Cookie 验证失败，请重新登录", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("NeteaseLogin", "回调: 保存Cookie时发生异常", e)
                    android.widget.Toast.makeText(
                        this,
                        "保存登录信息失败: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Log.d("NeteaseLogin", "回调: 收到null JSON")
            }
        }
        
        // 初始化 Coil ImageLoader 以支持 GIF
        val imageLoader = coil.ImageLoader.Builder(this)
            .components {
                add(coil.decode.GifDecoder.Factory())
            }
            .build()
        coil.Coil.setImageLoader(imageLoader)
        
        // MediaSessionManager 由 MusicViewModel 初始化，回调在 setContent 内部设置
        startKeepAliveServices(this)
        
        // 启动时获取一言
        lifecycleScope.launch {
            HitokotoManager.getHitokotoAndShow(this@MainActivity)
        }

        handleIntent(intent)

        setContent {
            val context = LocalContext.current
            DownloadSettingsStore.initDarkMode(context)
            DownloadSettingsStore.initFadeEnabled(context)
            DownloadSettingsStore.initThemeSource(context)
            DownloadSettingsStore.initSeedColor(context)
            val onboardingManager = remember { OnboardingManager(context) }
            val isFirstLaunch = remember { mutableStateOf(onboardingManager.isFirstLaunch()) }
            
            // 版本更新相关状态
            val showVersionUpdateDialog = remember { mutableStateOf(false) }
            val versionUpdate = remember { mutableStateOf<VersionUpdate?>(null) }
            
            // 第一天弹窗状态
            val showFirstDayDialog = remember { mutableStateOf(isFirstDay) }
            
            // 连续一周弹窗状态
            val showWeekDialog = remember { mutableStateOf(isWeekMilestone) }
            
            // 连续一月弹窗状态
            val showMonthDialog = remember { mutableStateOf(isMonthMilestone) }
            
            // 连续一年弹窗状态
            val showYearDialog = remember { mutableStateOf(isYearMilestone) }
            
            JianYinTheme {
                if (isFirstLaunch.value) {
                    OnboardingScreen(onComplete = {
                        onboardingManager.markAsCompleted()
                        isFirstLaunch.value = false
                    })
                } else {
                    val vm: MusicViewModel = viewModel() // 获取 ViewModel 实例
                    // 保存 ViewModel 引用到成员变量
                    viewModel = vm

                    // 处理待播放的音频 URI
                    LaunchedEffect(Unit) {
                        pendingAudioUri?.let { uri ->
                            Log.d("MainActivity", "处理待播放音频: $uri")
                            vm.playExternalAudio(uri)
                            pendingAudioUri = null
                        }
                    }

                    // 在这里设置 MediaSessionManager 的回调
                    val mediaSessionManager = remember { MediaSessionManager.getInstance(context) }
                    
                    LaunchedEffect(vm) {
                        mediaSessionManager.controlCallback = object : MediaSessionManager.MediaControlCallback {
                            override fun onPlay() {
                                Log.d("MediaSession", "回调: 播放")
                                vm.togglePlay()
                            }
                            override fun onPause() {
                                Log.d("MediaSession", "回调: 暂停")
                                vm.togglePlay()
                            }
                            override fun onNext() {
                                Log.d("MediaSession", "回调: 下一首")
                                vm.nextSong()
                            }
                            override fun onPrevious() {
                                Log.d("MediaSession", "回调: 上一首")
                                vm.previousSong()
                            }
                            override fun onStop() {
                                Log.d("MediaSession", "回调: 停止")
                                // 可以根据需要实现停止功能
                            }
                            override fun onSeekTo(position: Long) {
                                Log.d("MediaSession", "回调: 跳转到 $position")
                                vm.seekTo(position)
                            }
                        }
                    }
                    
                    // 版本检查
                    LaunchedEffect(Unit) {
                        val versionChecker = VersionChecker(context)
                        val update = versionChecker.checkForUpdates()
                        if (update != null) {
                            versionUpdate.value = update
                            showVersionUpdateDialog.value = true
                        }
                    }
                    
                    MainScreenFramework(vm)
                    
                    // 版本更新弹窗
                    VersionUpdateDialog(
                        isVisible = showVersionUpdateDialog.value,
                        versionUpdate = versionUpdate.value,
                        onDismissRequest = {
                            showVersionUpdateDialog.value = false
                        }
                    )
                    
                    // 第一天弹窗
                    FirstDayDialog(
                        isVisible = showFirstDayDialog.value,
                        onDismissRequest = {
                            showFirstDayDialog.value = false
                            userStats.markMilestoneShown("first_day")
                        }
                    )
                    
                    // 连续一周弹窗
                    FirstDayDialog(
                        isVisible = showWeekDialog.value,
                        title = "连续启动一周",
                        content = "你已经连续启动一周了！good",
                        onDismissRequest = {
                            showWeekDialog.value = false
                            userStats.markMilestoneShown("week")
                        }
                    )
                    
                    // 连续一月弹窗
                    FirstDayDialog(
                        isVisible = showMonthDialog.value,
                        title = "连续启动一个月",
                        content = "你已经连续启动一个月了！坚持就是胜利",
                        onDismissRequest = {
                            showMonthDialog.value = false
                            userStats.markMilestoneShown("month")
                        }
                    )
                    
                    // 连续一年弹窗
                    FirstDayDialog(
                        isVisible = showYearDialog.value,
                        title = "连续启动一年",
                        content = "你已经连续启动一年了！太强了",
                        onDismissRequest = {
                            showYearDialog.value = false
                            userStats.markMilestoneShown("year")
                        }
                    )
                }
            }
        }
    }
    
    // 处理活动结果，特别是文件夹选择器的结果
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            data?.data?.let {
                folderUriCallback?.invoke(it)
            }
        } else if (requestCode == 1002 && resultCode == RESULT_OK) {
            data?.data?.let {lrcUri ->
                // 读取 LRC 文件内容
                contentResolver.openInputStream(lrcUri)?.use {inputStream ->
                    val lrcContent = inputStream.bufferedReader().use { it.readText() }
                    // 将 LRC 内容传递给音乐播放器
                    viewModel?.setLyrics(lrcContent)
                    // 持久化保存歌词
                    viewModel?.currentSong?.value?.url?.let { songUrl ->
                        SongCustomDataStore.saveLyrics(this, songUrl, lrcContent)
                    }
                    Toast.makeText(this, "已加载歌词文件", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (requestCode == 1003 && resultCode == RESULT_OK) {
            data?.data?.let {coverUri ->
                // 方案B：拷贝文件到App私有目录（更稳妥）
                val contentResolver = applicationContext.contentResolver
                val inputStream = contentResolver.openInputStream(coverUri)
                if (inputStream != null) {
                    try {
                        // 创建封面存储目录
                        val coverDir = File(filesDir, "custom_covers")
                        if (!coverDir.exists()) {
                            coverDir.mkdirs()
                        }
                        // 生成唯一的文件名
                        val songUrl = viewModel?.currentSong?.value?.url ?: ""
                        val fileName = "cover_${songUrl.hashCode()}_${System.currentTimeMillis()}.jpg"
                        val coverFile = File(coverDir, fileName)
                        // 拷贝文件
                        inputStream.use { input ->
                            coverFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        val finalPath = coverFile.absolutePath
                        // 保存并更新
                        viewModel?.currentSong?.value?.url?.let { songUrl ->
                            SongCustomDataStore.saveCover(this, songUrl, finalPath)
                        }
                        viewModel?.setCover(finalPath)
                        Toast.makeText(this, "已加载封面", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("MainActivity", "保存封面失败", e)
                        Toast.makeText(this, "保存封面失败", Toast.LENGTH_SHORT).show()
                    } finally {
                        inputStream.close()
                    }
                }
            }
        } else if (requestCode == 1004 && resultCode == RESULT_OK) {
            // SAF下载路径选择
            data?.data?.let {uri ->
                // 持久化授权
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                downloadPathCallback?.invoke(uri)
            }
        }
    }
    
    // 启动 B 站登录
    fun startBiliLogin() {
        BiliWebLoginHelper.startLoginWithExistingLauncher(this, biliLoginLauncher)
    }

    // 启动网易云登录
    fun startNeteaseLogin() {
        val intent = android.content.Intent(this, com.qian.jianyin.netease.NeteaseWebLoginActivity::class.java)
        neteaseLoginLauncher.launch(intent)
    }
    
    private val requestOverlayPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = checkOverlayPermission()
        Log.d("DesktopLyric", "requestOverlayPermission result: isGranted=$granted")
        if (granted) {
            Log.d("DesktopLyric", "Permission granted, starting lyric service")
            startDesktopLyricService()
            overlayPermissionCallback?.invoke(true)
        } else {
            Log.d("DesktopLyric", "Permission denied")
            overlayPermissionCallback?.invoke(false)
        }
    }
    
    // 悬浮窗权限请求回调
    private var overlayPermissionCallback: ((Boolean) -> Unit)? = null
    
    /**
     * 请求悬浮窗权限（带回调）
     */
    fun requestOverlayPermission(callback: (Boolean) -> Unit) {
        overlayPermissionCallback = callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!checkOverlayPermission()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                requestOverlayPermissionLauncher.launch(intent)
            } else {
                callback(true)
            }
        } else {
            callback(true)
        }
    }
    
    /**
     * 启动桌面歌词服务
     */
    fun startDesktopLyricService() {
        Log.d("DesktopLyric", "startDesktopLyricService called")
        try {
            startService(Intent(this, DesktopLyricService::class.java))
            Log.d("DesktopLyric", "Service started successfully")
            
            // 立即更新歌词
            val lyrics = viewModel?.currentLrc
            val index = viewModel?.currentLineIndex?.intValue ?: 0
            Log.d("DesktopLyric", "Updating lyric: lyrics.size=${lyrics?.size ?: 0}, index=$index")
            lyrics?.let {
                DesktopLyricService.updateLyric(it, index)
            }
        } catch (e: Exception) {
            Log.e("DesktopLyric", "启动桌面歌词服务失败", e)
            Toast.makeText(this, "启动桌面歌词失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 停止桌面歌词服务
     */
    fun stopDesktopLyricService() {
        try {
            stopService(Intent(this, DesktopLyricService::class.java))
        } catch (e: Exception) {
            Log.e("MainActivity", "停止桌面歌词服务失败", e)
        }
    }
    
    /**
     * 检查悬浮窗权限
     */
    fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    /**
     * 返回键：不拦截，让系统播放回家的预见式返回动画后回到后台
     * 前提：无其他 OnBackPressedCallback 启用时才会走到这里
     */
    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}

/**
 * 切换桌面歌词开关
 */
fun toggleDesktopLyric(
    context: Context,
    vm: MusicViewModel,
    isEnabled: androidx.compose.runtime.MutableState<Boolean>
) {
    Log.d("DesktopLyric", "toggleDesktopLyric called, current state: ${isEnabled.value}")
    
    if (isEnabled.value) {
        // 关闭桌面歌词
        Log.d("DesktopLyric", "Closing desktop lyric")
        (context as? MainActivity)?.stopDesktopLyricService()
        isEnabled.value = false
        Toast.makeText(context, "已关闭桌面歌词", Toast.LENGTH_SHORT).show()
        Log.d("DesktopLyric", "Desktop lyric closed")
    } else {
        // 开启桌面歌词
        Log.d("DesktopLyric", "Opening desktop lyric")
        if (context is MainActivity) {
            Log.d("DesktopLyric", "Checking overlay permission...")
            context.requestOverlayPermission { granted ->
                Log.d("DesktopLyric", "Permission result: $granted")
                if (granted) {
                    context.startDesktopLyricService()
                    isEnabled.value = true
                    // 立即更新歌词
                    DesktopLyricService.updateLyric(vm.currentLrc, vm.currentLineIndex.intValue)
                    Toast.makeText(context, "已开启桌面歌词", Toast.LENGTH_SHORT).show()
                    Log.d("DesktopLyric", "Desktop lyric opened successfully")
                } else {
                    Log.d("DesktopLyric", "Permission denied, cannot open lyric")
                }
            }
        } else {
            Log.d("DesktopLyric", "Context is not MainActivity")
        }
    }
}

/**
 * 音乐进度滑块
 * 用于显示和控制音乐播放进度
 * @param vm 音乐视图模型
 * @param style 样式参数，默认为 0
 */
@Composable
fun MusicSlider(vm: MusicViewModel) {
    val pos = vm.currentPosition.longValue.toFloat()
    val total = vm.totalDuration.longValue.coerceAtLeast(1L).toFloat()
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary
    val progress = (pos / total).coerceIn(0f, 1f)
    
    when (vm.progressBarStyle.value) {
        ProgressBarStyle.DEFAULT -> {
            // 默认样式
            Slider(
                value = pos.coerceIn(0f, total),
                onValueChange = { vm.seekTo(it.toLong()) },
                valueRange = 0f..total,
                colors = SliderDefaults.colors(
                    thumbColor = primaryColor,
                    activeTrackColor = primaryColor,
                    inactiveTrackColor = primaryColor.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        ProgressBarStyle.ROUND -> {
            // 圆条样式
            Slider(
                value = pos.coerceIn(0f, total),
                onValueChange = { vm.seekTo(it.toLong()) },
                valueRange = 0f..total,
                colors = SliderDefaults.colors(
                    thumbColor = primaryColor,
                    activeTrackColor = primaryColor,
                    inactiveTrackColor = primaryColor.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
        }
        ProgressBarStyle.AUDIO -> {
            // 音频波形图样式 - 固定波形高度
            Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                // 生成固定的波形高度数组
                val waveHeights = remember {
                    List(31) { (4 + (Math.random() * 16)).dp }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    for (i in 0..30) {
                        val barHeight = if (i / 30f < progress) {
                            // 使用固定的波形高度
                            waveHeights[i]
                        } else {
                            4.dp
                        }
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (i / 30f < progress) primaryColor else primaryColor.copy(alpha = 0.3f))
                                .align(Alignment.CenterVertically)
                        )
                    }
                }
                // 优化点击和拖动事件，实现点击和拖动跳转进度
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                val width = size.width
                                val clickProgress = (it.x / width).coerceIn(0f, 1f)
                                val newPosition = (clickProgress * total).toLong()
                                vm.seekTo(newPosition)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures {change, _ ->
                                val width = size.width
                                val dragProgress = (change.position.x / width).coerceIn(0f, 1f)
                                val newPosition = (dragProgress * total).toLong()
                                vm.seekTo(newPosition)
                            }
                        }
                )
            }
        }
    }
}

/**
 * 直线波浪进度条组件
 * 基于默认样式，播放后的部分显示波浪效果
 * @param progress 进度值 (0f-1f)
 * @param modifier 修饰符
 * @param waveColor 波浪颜色
 * @param trackColor 轨道颜色
 * @param waveStrokeWidth 波浪笔触宽度
 * @param trackStrokeWidth 轨道笔触宽度
 */
@Composable
fun LinearWavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    waveStrokeWidth: Dp = 4.dp,
    trackStrokeWidth: Dp = 4.dp
) {
    // 复用 Path 对象，避免重组时的重建导致内存抖动
    val wavePath = remember { Path() }

    val density = LocalDensity.current
    // 将 Dp 转为 Px
    val waveStrokeWidthPx = with(density) { waveStrokeWidth.toPx() }

    Box(modifier = modifier) {
        // 基础进度条（轨道）
        LinearProgressIndicator(
            progress = { 1f },
            color = trackColor,
            trackColor = trackColor,
            modifier = Modifier.fillMaxWidth().height(4.dp)
        )
        
        // 波浪效果（播放后的部分）
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            
            // 波浪参数
            val waveHeight = 3.dp.toPx()
            val waveLength = 15.dp.toPx()
            
            // --- 绘制波浪 --- 
            wavePath.rewind()
            val waveEndX = width * progress
            
            // 生成波浪路径
            for (x in 0..waveEndX.toInt() step 2) {
                val y = centerY + waveHeight * sin((x / waveLength) * 2 * Math.PI).toFloat()
                if (x == 0) {
                    wavePath.moveTo(x.toFloat(), y)
                } else {
                    wavePath.lineTo(x.toFloat(), y)
                }
            }
            drawPath(
                path = wavePath,
                color = waveColor,
                style = Stroke(width = waveStrokeWidthPx, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * 单选按钮行组件
 * 用于在弹窗中显示单选选项
 * @param text 选项文本
 * @param selected 是否选中
 * @param onSelect 选中回调
 */
@Composable
fun RadioButtonRow(text: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 主界面框架
 * 包含底部导航栏和页面切换逻辑
 * @param vm 音乐视图模型
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun MainScreenFramework(vm: MusicViewModel = viewModel()) {
    var selectedItem by remember { mutableIntStateOf(0) }
    var refreshPlaylistTrigger by remember { mutableIntStateOf(0) }
    
    // 返回键：播放器面板打开时拦截，关闭面板
    BackHandler(vm.isPlayerSheetVisible.value) {
        vm.isPlayerSheetVisible.value = false
    }
    // 根级不拦截返回 — 动画由覆写 onBackPressed() 配合系统自然播回家动画
    
    val navItems = listOf(
        NavItem("首页", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("搜索", Icons.Filled.Search, Icons.Outlined.Search),
        NavItem("我的", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
    )

    // 创建 HazeState
    val hazeState = remember { HazeState() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = !vm.isPlayerSheetVisible.value,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Column(modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .then(
                        @OptIn(ExperimentalHazeApi::class)
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = MaterialTheme.colorScheme.surface,
                                tint = HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            )
                        )
                    )
                ) {
                    val isPlaying by remember { vm.isPlaying }
                    val currentSong by remember { vm.currentSong }
                    AnimatedVisibility(
                        visible = currentSong != null,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        MiniPlayerBar(vm)
                    }
                    
                    NavigationBar(
                        windowInsets = NavigationBarDefaults.windowInsets,
                        containerColor = Color.Transparent
                    ) {
                        navItems.forEachIndexed { index, item ->
                            val isSelected = selectedItem == index
                            NavigationBarItem(
                                icon = { 
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon, 
                                        contentDescription = item.label
                                    )
                                },
                                label = { 
                                    Text(item.label)
                                },
                                selected = isSelected,
                                onClick = { selectedItem = index },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                alwaysShowLabel = false
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // 关键：这里不再使用 .padding(innerPadding)，让内容可以穿透到导航栏下方
        Box(Modifier
            .fillMaxSize()
            .then(
                @OptIn(ExperimentalHazeApi::class)
                Modifier.hazeSource(state = hazeState)
            )
        ) {
            // 将页面切换放入 AnimatedContent
            AnimatedContent(
                targetState = selectedItem,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally(initialOffsetX = { it }) + fadeIn(animationSpec = tween(durationMillis = 300)) with
                            slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(animationSpec = tween(durationMillis = 200))
                    } else {
                        slideInHorizontally(initialOffsetX = { -it }) + fadeIn(animationSpec = tween(durationMillis = 300)) with
                            slideOutHorizontally(targetOffsetX = { it }) + fadeOut(animationSpec = tween(durationMillis = 200))
                    }
                }
            ) {
                when (it) {
                    // 关键：把 innerPadding 传给子页面
                    0 -> HomeScreen(vm, innerPadding)
                    1 -> SearchScreen(vm, innerPadding)
                    2 -> MyMusicScreenV2(vm, innerPadding, refreshPlaylistTrigger)
                }
            }
        }
    }

    // 使用AnimatedVisibility实现大播放器的滑入滑出动画
    AnimatedVisibility(
        visible = vm.isPlayerSheetVisible.value,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { /* 拦截点击事件 */ }) {
            FullPlayerScreen(vm) {
                // 触发歌单数据刷新
                refreshPlaylistTrigger++
            }
        }
    }
    
    // 下载进度弹窗
    DownloadProgressDialog(
        isVisible = DownloadStateManager.isDownloading || DownloadStateManager.errorMessage.isNotEmpty(),
        onDismissRequest = {
            DownloadStateManager.resetState()
        }
    )
    
    // 权限检查
    PermissionCheck()
}

/**
 * 迷你播放器栏
 * 显示在底部导航栏上方的小型播放器控件
 * @param vm 音乐视图模型
 */
@Composable
fun MiniPlayerBar(vm: MusicViewModel) {
    val song = vm.currentSong.value ?: return
    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { vm.isPlayerSheetVisible.value = true }
    ) {
        Column {
            LinearProgressIndicator(
                progress = { (vm.currentPosition.longValue.toFloat() / vm.totalDuration.longValue.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
            Row(Modifier.padding(8.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = song.pic, 
                    contentDescription = null, 
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), 
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = getRandomPlaceholderId())
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(song.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, fontWeight = FontWeight.Bold)
                    Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { vm.previousSong() }) { Icon(Icons.Default.SkipPrevious, null) }
                IconButton(onClick = { vm.togglePlay() }) {
                    Icon(imageVector = if (vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                }
                IconButton(onClick = { vm.nextSong() }) { Icon(Icons.Default.SkipNext, null) }
            }
        }
    }
}

/**
 * 全屏播放器界面
 * 显示完整播放器界面，包含封面、歌词、控制按钮等
 * @param vm 音乐视图模型
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FullPlayerScreen(vm: MusicViewModel, refreshPlaylistTrigger: (() -> Unit)? = null) {
    val song = vm.currentSong.value ?: return
    var showLrc by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showProgressBarStyleDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showPlaybackSpeedDialog by remember { mutableStateOf(false) }
    var showPlayModeDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var sleepTimerTime by remember { mutableStateOf("23:00") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 屏幕常亮控制
    val keepScreenOn = remember { PlaybackSettingsStore.isKeepScreenOnEnabled(context) }
    DisposableEffect(keepScreenOn) {
        val window = (context as Activity).window
        if (keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (keepScreenOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    
    // 更多菜单和播放队列：PredictiveBackHandler，手势滑动时展示动画预览
    PredictiveBackHandler(enabled = showMoreMenu || showQueue) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { /* system preview */ }
            if (showMoreMenu) {
                showMoreMenu = false
            } else if (showQueue) {
                showQueue = false
            }
        } catch (_: CancellationException) { }
    }
    // 倍速弹窗：普通 BackHandler，不需要动画预览
    BackHandler(showPlaybackSpeedDialog) {
        showPlaybackSpeedDialog = false
    }
    // 播放模式弹窗
    BackHandler(showPlayModeDialog) {
        showPlayModeDialog = false
    }

    val window = (context as Activity).window
    DisposableEffect(Unit) {
        val originalNavColor = window.navigationBarColor
        val originalContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else false
        window.navigationBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        onDispose {
            window.navigationBarColor = originalNavColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = originalContrastEnforced
            }
        }
    }

    // --- 背景动画逻辑 ---
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidBackground")
    
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -160f, targetValue = 120f, 
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Reverse), label = "x"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 100f, targetValue = -100f, 
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Reverse), label = "y"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 3.2f, targetValue = 4.0f, 
        animationSpec = infiniteRepeatable(tween(20000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f, 
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "r"
    )

    Box(Modifier.fillMaxSize()) {
        // 背景渲染层（延伸到系统栏区域）
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            // 智能自动压暗封面：分析封面亮度，过亮时自动压暗
            val autoDarkenEnabled = remember { PlaybackSettingsStore.isAutoDarkenCoverEnabled(context) }
            val darkenAlpha = remember { mutableStateOf(0f) }
            
            // 使用 rememberAsyncImagePainter 来获取图片加载状态
            val painter = rememberAsyncImagePainter(
                model = song.pic,
                error = painterResource(id = getRandomPlaceholderId())
            )
            
            // 使用 LaunchedEffect 在协程中获取图片并分析亮度
            LaunchedEffect(song.pic, autoDarkenEnabled) {
                if (autoDarkenEnabled && song.pic.isNotEmpty()) {
                    try {
                        // 使用 ImageLoader 获取图片
                        val request = ImageRequest.Builder(context)
                            .data(song.pic)
                            .allowHardware(false)
                            .build()
                        
                        val result = ImageLoader(context).execute(request)
                        if (result is coil.request.SuccessResult) {
                            val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                // 根据图片亮度计算压暗程度
                                val alpha = ImageBrightnessAnalyzer.calculateDarkenAlpha(bitmap)
                                darkenAlpha.value = alpha
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略加载失败的情况
                    }
                }
            }
            
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale, 
                        scaleY = scale, 
                        translationX = offsetX, 
                        translationY = offsetY, 
                        rotationZ = rotation,
                        alpha = 0.6f
                    )
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(radius = 20.dp)
                        } else {
                            Modifier
                        }
                    )
            )
            
            // 根据分析结果动态添加压暗覆盖层
            if (autoDarkenEnabled && darkenAlpha.value > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = darkenAlpha.value))
                )
            }
        }

        // 读取渐变层亮度调整系数（0.1-2.0，1.0为原始亮度）
        val gradientBrightnessMultiplier = remember { 
            PlaybackSettingsStore.getGradientBrightnessMultiplier(context) 
        }
        
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(
                MaterialTheme.colorScheme.surface.copy(PlaybackSettingsStore.DEFAULT_GRADIENT_TOP_ALPHA * gradientBrightnessMultiplier), 
                MaterialTheme.colorScheme.surface.copy(PlaybackSettingsStore.DEFAULT_GRADIENT_MIDDLE_ALPHA * gradientBrightnessMultiplier), 
                MaterialTheme.colorScheme.surface.copy(PlaybackSettingsStore.DEFAULT_GRADIENT_BOTTOM_ALPHA * gradientBrightnessMultiplier)
            ))
        ))

        // --- 标题/歌手淡出动画进度（歌词模式下渐隐） ---
        val titleAlpha by animateFloatAsState(
            targetValue = if (showLrc) 0f else 1f,
            animationSpec = tween(350, easing = FastOutSlowInEasing)
        )
        val titleOffsetY by animateFloatAsState(
            targetValue = if (showLrc) -60f else 0f,
            animationSpec = tween(350, easing = FastOutSlowInEasing)
        )

        SharedTransitionLayout {
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // 顶部栏 + 中间区域：包裹在 AnimatedContent 中实现共享元素切换
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = showLrc,
                        transitionSpec = {
                            fadeIn(tween(350, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(tween(350, easing = FastOutSlowInEasing))
                        },
                        label = "playerMode",
                        modifier = Modifier.fillMaxSize()
                    ) { isLrc ->
                        Column(Modifier.fillMaxSize()) {
                            // 顶部栏
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isLrc) {
                                    // 封面模式：左隐藏按钮
                                    IconButton(onClick = { vm.isPlayerSheetVisible.value = false }) {
                                        Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                                    }
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    // 歌词模式：小封面（共享元素）+ 标题
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Card(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .sharedElement(
                                                    rememberSharedContentState(key = "fullscreen_cover"),
                                                    animatedVisibilityScope = this@AnimatedContent
                                                )
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showLrc = !showLrc },
                                            shape = RoundedCornerShape(8.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                        ) {
                                            AsyncImage(
                                                model = song.pic,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(id = getRandomPlaceholderId())
                                            )
                                        }
                                        Column(modifier = Modifier.padding(start = 8.dp)) {
                                            Text(
                                                song.name,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                song.artist,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                // 右区：歌词模式下显示隐藏按钮
                                if (isLrc) {
                                    IconButton(onClick = { vm.isPlayerSheetVisible.value = false }) {
                                        Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                                    }
                                }
                                // 三点菜单始终可见
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                                }
                            }

                            // 中间区域
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showLrc = !showLrc },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isLrc) {
                                    // 封面模式：大封面（共享元素）
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth(0.85f)
                                            .aspectRatio(1f)
                                            .sharedElement(
                                                rememberSharedContentState(key = "fullscreen_cover"),
                                                animatedVisibilityScope = this@AnimatedContent
                                            )
                                            .clip(RoundedCornerShape(24.dp)),
                                        shape = RoundedCornerShape(24.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                song.pic,
                                                null,
                                                Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(id = getRandomPlaceholderId())
                                            )
                                            // 分p视频胶囊标识
                                            if (song.isPartOfMultiPage && song.pageCount > 1) {
                                                MultiPageTaijiBadge(
                                                    pageIndex = song.pageIndex,
                                                    pageCount = song.pageCount,
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // 歌词模式：歌词列表
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.95f)
                                            .align(Alignment.TopStart),
                                        shape = RoundedCornerShape(0.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            LyricList(vm)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = (12f * (0.5f + titleAlpha * 0.833f)).dp)
            ) {
                // 歌曲标题：歌词模式下淡出
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .graphicsLayer {
                            alpha = titleAlpha
                            translationY = titleOffsetY
                        }
                ) {
                    Text(song.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height((16f * titleAlpha).dp))
                MusicSlider(vm)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(formatTime(vm.currentPosition.longValue), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(formatTime(vm.totalDuration.longValue), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                
                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.previousSong() }) { 
                        Icon(Icons.Default.SkipPrevious, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(44.dp)) 
                    }
                    Spacer(Modifier.width(32.dp))
                    
                    // 播放状态用星形，暂停用圆形，带平滑过渡动画
                    // 加载状态在星形和圆形之间不停切换
                    val isPlaying by remember { vm.isPlaying }
                    val isLoading by remember { vm.isLoading }
                    
                    // 加载状态时交替切换形状
                    val infiniteTransition = rememberInfiniteTransition()
                    val isStarDuringLoading by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(durationMillis = 500)
                        )
                    )
                    
                    val displayAsStar = if (isLoading) {
                        isStarDuringLoading > 0.5f
                    } else {
                        isPlaying
                    }
                    
                    // 旋转动画：从星形变圆形时旋转360度
                    val rotation by animateFloatAsState(
                        targetValue = if (displayAsStar) 0f else 360f,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioMediumBouncy
                        ),
                        label = "ButtonRotation"
                    )
                    
                    // 使用自定义按钮去掉水波纹效果
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .rotate(rotation)
                            .clip(if (displayAsStar) MaterialStarShape else CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { 
                                if (!isLoading) {
                                    vm.togglePlay() 
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = isLoading to isPlaying,
                            transitionSpec = {
                                fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleIn(initialScale = 0.9f) with
                                fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleOut(targetScale = 0.9f)
                            }
                        ) { (loading, playing) ->
                            if (loading) {
                                // 加载状态显示进度指示器
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.width(32.dp))
                    IconButton(onClick = { vm.nextSong() }) { 
                        Icon(Icons.Default.SkipNext, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(44.dp)) 
                    }
                }
                Spacer(Modifier.height(20.dp))
                
                // 底部操作按钮行：将播放模式聚合按钮、收藏按钮、桌面歌词和播放列表按钮放在一行
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 播放模式聚合按钮（顺序/随机/单曲循环/心动模式）
                    IconButton(
                        onClick = { showPlayModeDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        val customIconRes = vm.playMode.value.getCustomIconRes()
                        if (customIconRes != null) {
                            // 心动模式使用自定义图标（红色）
                            Icon(
                                painter = painterResource(id = customIconRes),
                                contentDescription = vm.playMode.value.label,
                                modifier = Modifier.size(32.dp),
                                tint = Color.Red
                            )
                        } else {
                            Icon(
                                imageVector = vm.playMode.value.getIcon(), // 使用 PlaybackMode 的 getIcon 方法
                                contentDescription = vm.playMode.value.label,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 收藏按钮
                    IconButton(
                        onClick = { 
                            vm.toggleFavoriteCurrentSong()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (vm.isCurrentSongFavorited.value) {
                                Icons.Default.Favorite  // 实心爱心，表示已收藏
                            } else {
                                Icons.Default.FavoriteBorder  // 空心爱心，表示未收藏
                            },
                            contentDescription = if (vm.isCurrentSongFavorited.value) "取消收藏" else "收藏",
                            tint = if (vm.isCurrentSongFavorited.value) {
                                MaterialTheme.colorScheme.error  // 使用主题错误色
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant  // 未收藏时使用变体颜色
                            }
                        )
                    }
                    
                    // 桌面歌词按钮
                    val isLyricEnabled = remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            toggleDesktopLyric(context, vm, isLyricEnabled)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = if (isLyricEnabled.value) "关闭桌面歌词" else "开启桌面歌词",
                            tint = if (isLyricEnabled.value) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    
                    // 播放列表按钮（原有的）
                    IconButton(onClick = { showQueue = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    }
    
    // 定时关闭对话框
    val currentTime = java.util.Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(java.util.Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(java.util.Calendar.MINUTE),
        is24Hour = true,
    )
    
    // 使用与更多选项窗口相同的样式和动画
    AnimatedVisibility(
        visible = showSleepTimerDialog,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showSleepTimerDialog = false }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(24.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text("定时关闭", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                    Spacer(Modifier.height(24.dp))
                    
                    // 官方TimePicker组件
                    Column {
                        Text("选择关闭时间", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                        Spacer(Modifier.height(24.dp))
                        TimePicker(
                            state = timePickerState,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TimePickerDefaults.colors(
                                clockDialColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                                clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                                clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                selectorColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                                timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                                timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                                timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    
                    // 按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showSleepTimerDialog = false }) {
                            Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(16.dp))
                        TextButton(onClick = {
                            val hour = timePickerState.hour
                            val minute = timePickerState.minute
                            val timeString = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                            
                            // 计算当前时间和目标时间的差值
                            val calendar = java.util.Calendar.getInstance()
                            val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                            val currentMinute = calendar.get(java.util.Calendar.MINUTE)
                            
                            var delayInMillis = (hour - currentHour) * 3600000L + (minute - currentMinute) * 60000L
                            
                            // 如果目标时间已过，则设置为第二天
                            if (delayInMillis < 0) {
                                delayInMillis += 24 * 3600000L
                            }
                            
                            // 设置定时关闭
                            scope.launch {
                                kotlinx.coroutines.delay(delayInMillis)
                                if (vm.isPlaying.value) {
                                    vm.togglePlay()
                                }
                                Toast.makeText(context, "定时关闭已执行", Toast.LENGTH_SHORT).show()
                            }
                            
                            Toast.makeText(context, "已设置定时关闭：$timeString", Toast.LENGTH_SHORT).show()
                            showSleepTimerDialog = false
                        }) {
                            Text("确定", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    
    // 使用Box和动画效果替代ModalBottomSheet
    AnimatedVisibility(
        visible = showMoreMenu,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showMoreMenu = false }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(16.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text("更多选项", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                    if (!song.isLocal) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isDownloading) {
                                        isDownloading = true
                                        DownloadStateManager.startDownload(1)
                                        DownloadStateManager.updateCurrentSong(0, song.name)
                                        scope.launch {
                                            val customUri = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomUri(context) else null
                                            DownloadManager.downloadSong(
                                                context, 
                                                song, 
                                                customUri
                                            ) {
                                                DownloadStateManager.updateProgress(it)
                                            }
                                                .onSuccess { message ->
                                                    Toast.makeText(context, "下载完成: ${song.name}", Toast.LENGTH_LONG).show()
                                                    DownloadStateManager.downloadComplete()
                                                }
                                                .onFailure { e ->
                                                    Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    DownloadStateManager.downloadFailed(e.message ?: "未知错误")
                                                }
                                            isDownloading = false
                                        }
                                    }
                                    showMoreMenu = false
                                }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Download, 
                                    null, 
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = if (isDownloading) "下载中..." else "下载歌曲",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                        Spacer(Modifier.height(8.dp))
                    }
                    if (song.isLocal) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 打开文件选择器选择 LRC 文件
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    intent.type = "text/plain"
                                    intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/lrc", "text/lrc"))
                                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                                    (context as? Activity)?.startActivityForResult(intent, 1002)
                                    showMoreMenu = false
                                }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.FileOpen,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = "选择 LRC 文件",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ArrowForwardIos,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 打开文件选择器选择封面文件
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    intent.type = "image/*"
                                    intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/gif", "image/webp"))
                                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                                    (context as? Activity)?.startActivityForResult(intent, 1003)
                                    showMoreMenu = false
                                }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Image,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = "选择封面",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ArrowForwardIos,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 恢复默认设置
                                    SongCustomDataStore.clearCustomData(context, song.url)
                                    Toast.makeText(context, "已恢复默认设置", Toast.LENGTH_SHORT).show()
                                    // 重新加载歌曲，获取更新后的歌曲对象
                                    val localMusicManager = LocalMusicManager(context)
                                    val songFile = java.io.File(song.url)
                                    val updatedSong = localMusicManager.parseSongFromFile(songFile) ?: song
                                    // 更新队列中的歌曲对象
                                    val currentQueue = vm.playQueue.toList()
                                    val updatedQueue = currentQueue.map { if (it.url == song.url) updatedSong else it }
                                    // 播放更新后的歌曲，使用更新后的队列
                                    vm.playSong(updatedSong, updatedQueue)
                                    // 触发歌单数据刷新，确保歌单详情页能立即更新
                                    refreshPlaylistTrigger?.invoke()
                                    showMoreMenu = false
                                }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Restore,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = "恢复默认设置",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                    }
                    
                    // 定时关闭功能（对所有歌曲适用）
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 定时关闭功能
                                showSleepTimerDialog = true
                                showMoreMenu = false
                            }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Schedule,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "定时关闭",
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ArrowForwardIos,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                    
                    // 播放速度选项
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPlaybackSpeedDialog = true
                                showMoreMenu = false
                            }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Speed,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "播放速度",
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${String.format("%.1f", vm.playbackSpeed.value)}x",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                                fontSize = 14.sp
                            )
                            Icon(
                                Icons.Default.ArrowForwardIos,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showProgressBarStyleDialog = true
                                showMoreMenu = false
                            }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "进度条样式",
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ArrowForwardIos,
                                null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                }
            }
        }
    }
    
    val queueState = rememberLazyListState()
    val currentSong = vm.currentSong.value
    var isPlaylistReorderMode by remember { mutableStateOf(false) }
    var isSongReorderMode by remember { mutableStateOf(false) }
    var draggedSongIndex by remember { mutableIntStateOf(-1) }
    var originalDraggedIndex by remember { mutableIntStateOf(-1) }
    var accumulatedDragOffsetY by remember { mutableFloatStateOf(0f) }
    var accumulatedForThreshold by remember { mutableFloatStateOf(0f) }
    var pendingDragSongIndex by remember { mutableIntStateOf(-1) }
    var showAddPlaylistDialog by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<String>()) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var createPlaylistName by remember { mutableStateOf("") }
    
    LaunchedEffect(currentSong, showQueue) {
        if (showQueue && currentSong != null) {
            val index = vm.playQueue.indexOfFirst { it == currentSong }
            if (index != -1) {
                queueState.animateScrollToItem(index, scrollOffset = 30)
            }
        }
    }
    
    if (showAddPlaylistDialog) {
        AddPlaylistToQueueDialog(
            vm = vm,
            onDismiss = { showAddPlaylistDialog = false },
            onPlaylistSelected = { playlist ->
                vm.addPlaylistToQueue(playlist)
                showAddPlaylistDialog = false
            }
        )
    }

    if (showAddToPlaylistDialog) {
        val otherPlaylists = PlaylistDataStore.getAll(context).filter {
            !it.id.startsWith("local_") || it.isLocalPlaylist
        }
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("添加到歌单") },
            text = {
                Column {
                    if (otherPlaylists.isEmpty()) {
                        Text("没有其他歌单可添加", modifier = Modifier.padding(vertical = 16.dp))
                    } else {
                        otherPlaylists.take(5).forEach { targetPlaylist ->
                            ListItem(
                                headlineContent = { Text(targetPlaylist.name) },
                                supportingContent = { Text("${targetPlaylist.songs.size} 首歌曲") },
                                leadingContent = {
                                    val cover = targetPlaylist.coverPic.ifBlank { null }
                                    if (cover != null) {
                                        AsyncImage(
                                            model = cover,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = getRandomPlaceholderId())
                                        )
                                    } else {
                                        Image(
                                            painter = painterResource(id = getRandomPlaceholderId()),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    val songsToAdd = selectedSongs.mapNotNull { key ->
                                        vm.playQueue.find { (it.id.isNotBlank() && it.id == key) || it.url == key }
                                    }
                                    var addedCount = 0
                                    songsToAdd.forEach { s ->
                                        if (PlaylistDataStore.addSongToPlaylist(context, targetPlaylist.id, s)) {
                                            addedCount++
                                        }
                                    }
                                    showAddToPlaylistDialog = false
                                    isSelectionMode = false
                                    selectedSongs = emptySet()
                                    Toast.makeText(context, "已添加 $addedCount 首歌曲到 ${targetPlaylist.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    ListItem(
                        headlineContent = { Text("创建新歌单", color = MaterialTheme.colorScheme.primary) },
                        leadingContent = {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            showAddToPlaylistDialog = false
                            createPlaylistName = ""
                            showCreatePlaylistDialog = true
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("创建新歌单") },
            text = {
                OutlinedTextField(
                    value = createPlaylistName,
                    onValueChange = { createPlaylistName = it },
                    label = { Text("歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (createPlaylistName.isNotBlank()) {
                            val newPlaylist = PlaylistDataStore.createPlaylist(context, createPlaylistName.trim())
                            val songsToAdd = selectedSongs.mapNotNull { key ->
                                vm.playQueue.find { (it.id.isNotBlank() && it.id == key) || it.url == key }
                            }
                            var addedCount = 0
                            songsToAdd.forEach { s ->
                                if (PlaylistDataStore.addSongToPlaylist(context, newPlaylist.id, s)) {
                                    addedCount++
                                }
                            }
                            showCreatePlaylistDialog = false
                            isSelectionMode = false
                            selectedSongs = emptySet()
                            Toast.makeText(context, "已创建歌单并添加 $addedCount 首歌曲", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = createPlaylistName.isNotBlank()
                ) {
                    Text("创建并添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
    
    AnimatedVisibility(
        visible = showQueue,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ) + fadeIn(
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showQueue = false }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(16.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionMode || (isSongReorderMode && selectedSongs.isNotEmpty())) {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedSongs = emptySet()
                            }) {
                                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text("已选择 ${selectedSongs.size} 首", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
                        } else {
                            Text("播放列表", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelectionMode || (isSongReorderMode && selectedSongs.isNotEmpty())) {
                                IconButton(onClick = { showAddToPlaylistDialog = true }) {
                                    Icon(Icons.Default.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            } else if (isPlaylistReorderMode) {
                                TextButton(onClick = { isPlaylistReorderMode = false }) {
                                    Text("完成", color = MaterialTheme.colorScheme.primary)
                                }
                            } else if (isSongReorderMode) {
                                TextButton(onClick = { isSongReorderMode = false }) {
                                    Text("完成", color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                if (vm.playlistQueue.isNotEmpty()) {
                                    TextButton(onClick = { isPlaylistReorderMode = true }) {
                                        Text("歌单排序", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = { showAddPlaylistDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "添加歌单",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    if (vm.playlistQueue.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            vm.playlistQueue.forEachIndexed { idx, playlist ->
                                val isCurrentPlaylist = idx == vm.currentPlaylistIndex.intValue
                                Box(
                                    modifier = Modifier
                                        .then(
                                            if (isPlaylistReorderMode) {
                                                Modifier.padding(horizontal = 4.dp)
                                            } else {
                                                Modifier.clickable {
                                                    if (!isCurrentPlaylist) {
                                                        vm.playPlaylist(playlist)
                                                    }
                                                }
                                            }
                                        )
                                        .background(
                                            if (isCurrentPlaylist) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isPlaylistReorderMode) {
                                            IconButton(
                                                onClick = {
                                                    if (idx > 0) {
                                                        vm.movePlaylistQueueItem(idx, idx - 1)
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "上移",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        val cover = playlist.coverPic.ifBlank { null }
                                        if (cover != null) {
                                            AsyncImage(
                                                model = cover,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(6.dp)),
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(id = getRandomPlaceholderId())
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(id = getRandomPlaceholderId()),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(RoundedCornerShape(6.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = playlist.name,
                                                color = if (isCurrentPlaylist) MaterialTheme.colorScheme.onPrimaryContainer
                                                       else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${playlist.songs.size} 首",
                                                color = if (isCurrentPlaylist) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (isPlaylistReorderMode) {
                                            IconButton(
                                                onClick = {
                                                    if (idx < vm.playlistQueue.size - 1) {
                                                        vm.movePlaylistQueueItem(idx, idx + 1)
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "下移",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.2f))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Text(
                        "${vm.playQueue.size} 首",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    val context = LocalContext.current
                    val density = LocalDensity.current
                    val itemHeight = 72.dp
                    LazyColumn(state = queueState, modifier = Modifier.weight(1f, fill = false)) {
                        itemsIndexed(vm.playQueue, key = { idx, song -> "${idx}${song.id.ifBlank { song.url }}" }) { index, s ->
                            val isDragging = draggedSongIndex == index
                            val songKey = s.id.ifBlank { s.url }
                            val isSelected = selectedSongs.contains(songKey)
                            
                            // 微小的弹簧缩放动画
                            val scale by animateFloatAsState(
                                targetValue = if (isDragging) 1.02f else 1f,
                                animationSpec = spring(
                                    dampingRatio = 0.6f,
                                    stiffness = 400f
                                ),
                                label = "dragScale"
                            )
                            
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .then(
                                            if (isSelectionMode) Modifier else Modifier.pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = {
                                                        if (isSelectionMode) {
                                                            selectedSongs = if (selectedSongs.contains(songKey)) selectedSongs - songKey else selectedSongs + songKey
                                                        } else {
                                                            if (s.source == SongSource.NETEASE) {
                                                                vm.playNeteaseSong(s)
                                                            } else {
                                                                vm.playSong(s)
                                                            }
                                                        }
                                                    },
                                                    onLongPress = {
                                                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                                            vibratorManager.defaultVibrator
                                                        } else {
                                                            @Suppress("DEPRECATION")
                                                            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
                                                        }
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                                        } else {
                                                            @Suppress("DEPRECATION")
                                                            vibrator.vibrate(50)
                                                        }
                                                        if (!isSelectionMode) {
                                                            isSelectionMode = true
                                                            selectedSongs = setOf(songKey)
                                                        }
                                                    }
                                                )
                                            }
                                        ),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        if (isSelectionMode) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "拖动",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .padding(end = 8.dp)
                                                    .pointerInput(Unit) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = { offset ->
                                                                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                                    val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                                                    vibratorManager.defaultVibrator
                                                                } else {
                                                                    @Suppress("DEPRECATION")
                                                                    context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
                                                                }
                                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                                                } else {
                                                                    @Suppress("DEPRECATION")
                                                                    vibrator.vibrate(50)
                                                                }
                                                                draggedSongIndex = index
                                                                originalDraggedIndex = index
                                                                accumulatedForThreshold = 0f
                                                            },
                                                            onDragEnd = {
                                                                draggedSongIndex = -1
                                                                originalDraggedIndex = -1
                                                                accumulatedForThreshold = 0f
                                                            },
                                                            onDragCancel = {
                                                                draggedSongIndex = -1
                                                                originalDraggedIndex = -1
                                                                accumulatedForThreshold = 0f
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                accumulatedForThreshold += dragAmount.y
                                                                val itemHeightPx = with(density) { itemHeight.toPx() }
                                                                val threshold = itemHeightPx * 0.5f
                                                                
                                                                val movedItems = (accumulatedForThreshold / threshold).toInt()
                                                                
                                                                if (movedItems != 0) {
                                                                    val selectedKeys = selectedSongs.toList()
                                                                    val currentIndices = selectedKeys.mapNotNull { key ->
                                                                        vm.playQueue.indexOfFirst { (it.id.ifBlank { it.url }) == key }
                                                                    }.sorted()
                                                                    
                                                                    if (currentIndices.isNotEmpty()) {
                                                                        val minSelected = currentIndices.first()
                                                                        val maxSelected = currentIndices.last()
                                                                        val rangeSize = currentIndices.size
                                                                        
                                                                        val targetStartIndex = (minSelected + movedItems)
                                                                            .coerceIn(0, vm.playQueue.size - rangeSize)
                                                                        
                                                                        if (targetStartIndex != minSelected) {
                                                                            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                                                val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                                                                vibratorManager.defaultVibrator
                                                                            } else {
                                                                                @Suppress("DEPRECATION")
                                                                                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
                                                                            }
                                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                                                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                                                                            } else {
                                                                                @Suppress("DEPRECATION")
                                                                                vibrator.vibrate(20)
                                                                            }
                                                                            
                                                                            vm.moveQueueItems(currentIndices, targetStartIndex)
                                                                            
                                                                            draggedSongIndex = targetStartIndex + (index - minSelected)
                                                                            accumulatedForThreshold = 0f
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        )
                                                    }
                                            )
                                        } else {
                                            Text(
                                                "${index + 1}",
                                                color = if (s == currentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                                            )
                                        }
                                        
                                        if (s == currentSong && !isSelectionMode) {
                                            Box(modifier = Modifier
                                                .width(32.dp)
                                                .height(24.dp)
                                                .padding(4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxSize(),
                                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                                    verticalAlignment = Alignment.Bottom
                                                ) {
                                                    val infiniteTransition = rememberInfiniteTransition()
                                                    
                                                    val height1 by infiniteTransition.animateFloat(
                                                        initialValue = 0.4f, 
                                                        targetValue = 1.0f,
                                                        animationSpec = infiniteRepeatable(
                                                            animation = tween<Float>(400, delayMillis = 0, easing = FastOutSlowInEasing),
                                                            repeatMode = RepeatMode.Reverse
                                                        )
                                                    )
                                                    val height2 by infiniteTransition.animateFloat(
                                                        initialValue = 0.6f, 
                                                        targetValue = 0.9f,
                                                        animationSpec = infiniteRepeatable(
                                                            animation = tween<Float>(500, delayMillis = 100, easing = FastOutSlowInEasing),
                                                            repeatMode = RepeatMode.Reverse
                                                        )
                                                    )
                                                    val height3 by infiniteTransition.animateFloat(
                                                        initialValue = 0.3f, 
                                                        targetValue = 1.0f,
                                                        animationSpec = infiniteRepeatable(
                                                            animation = tween<Float>(600, delayMillis = 200, easing = FastOutSlowInEasing),
                                                            repeatMode = RepeatMode.Reverse
                                                        )
                                                    )
                                                    
                                                    val displayHeight1 = if (vm.isPlaying.value) height1 else 0.1f
                                                    val displayHeight2 = if (vm.isPlaying.value) height2 else 0.1f
                                                    val displayHeight3 = if (vm.isPlaying.value) height3 else 0.1f
                                                    
                                                    Box(
                                                        modifier = Modifier
                                                            .width(6.dp)
                                                            .fillMaxHeight(displayHeight1)
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .width(6.dp)
                                                            .fillMaxHeight(displayHeight2)
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .width(6.dp)
                                                            .fillMaxHeight(displayHeight3)
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                            }
                                        } else if (!isSelectionMode) {
                                            Spacer(modifier = Modifier.width(32.dp))
                                        }
                                        
                                        AsyncImage(
                                            model = s.pic,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .then(if (isSelectionMode) Modifier.clickable { selectedSongs = if (selectedSongs.contains(songKey)) selectedSongs - songKey else selectedSongs + songKey } else Modifier),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = getRandomPlaceholderId())
                                        )
                                        
                                        Column(modifier = Modifier
                                            .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                                            .then(if (isSelectionMode) Modifier.clickable { selectedSongs = if (selectedSongs.contains(songKey)) selectedSongs - songKey else selectedSongs + songKey } else Modifier)) {
                                            Text(
                                                s.name,
                                                color = if (s == currentSong && !isSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 16.sp,
                                                maxLines = 1
                                            )
                                            Text(
                                                s.artist,
                                                color = if (s == currentSong && !isSelectionMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 13.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    if (!isSongReorderMode) {
                                        IconButton(
                                            onClick = { vm.removeFromQueue(s) },
                                            modifier = Modifier
                                                .size(24.dp)
                                                .padding(end = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "移除",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                                if (index < vm.playQueue.size - 1) {
                                    Divider(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Gray.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
    
    // 进度条样式选择弹窗
    if (showProgressBarStyleDialog) {
        AlertDialog(
            onDismissRequest = { showProgressBarStyleDialog = false },
            title = { Text("选择进度条样式") },
            text = {
                Column {
                    RadioButtonRow(
                        text = "默认样式",
                        selected = vm.progressBarStyle.value == ProgressBarStyle.DEFAULT,
                        onSelect = { vm.setProgressBarStyle(ProgressBarStyle.DEFAULT) }
                    )
                    RadioButtonRow(
                        text = "圆条样式",
                        selected = vm.progressBarStyle.value == ProgressBarStyle.ROUND,
                        onSelect = { vm.setProgressBarStyle(ProgressBarStyle.ROUND) }
                    )
                    RadioButtonRow(
                        text = "音频波形图样式",
                        selected = vm.progressBarStyle.value == ProgressBarStyle.AUDIO,
                        onSelect = { vm.setProgressBarStyle(ProgressBarStyle.AUDIO) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showProgressBarStyleDialog = false }) {
                    Text("确定")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
    
    // 播放速度调节弹窗
    if (showPlaybackSpeedDialog) {
        val currentSpeed = remember { mutableStateOf(vm.playbackSpeed.value) }
        AlertDialog(
            onDismissRequest = { showPlaybackSpeedDialog = false },
            title = { Text("播放速度") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 速度显示和滑块
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${String.format("%.1f", currentSpeed.value)}x",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    
                    // 滑块
                    val context = LocalContext.current
                    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
                    Slider(
                        value = currentSpeed.value,
                        onValueChange = { 
                            currentSpeed.value = (it * 10).toInt().toFloat() / 10.0f
                            // 微小震动反馈
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                @Suppress("DEPRECATION")
                                vibrator.vibrate(5)
                            }
                        },
                        valueRange = 0.25f..4.0f,
                        steps = 37, // 0.25 到 4.0，每0.1一个刻度，共38个点，steps=37
                        onValueChangeFinished = {
                            vm.setPlaybackSpeed(currentSpeed.value)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(0.3f)
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    // 速度范围提示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0.25x", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        Text("4.0x", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                    }
                    Spacer(Modifier.height(24.dp))
                    
                    // 快捷速度按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(0.5f, 1.0f, 1.5f).forEach { speed ->
                            Surface(
                                onClick = {
                                    currentSpeed.value = speed
                                    vm.setPlaybackSpeed(speed)
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (currentSpeed.value == speed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${speed}x",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    fontSize = 13.sp,
                                    color = if (currentSpeed.value == speed) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaybackSpeedDialog = false }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    // 恢复默认速度
                    currentSpeed.value = 1.0f
                    vm.setPlaybackSpeed(1.0f)
                    showPlaybackSpeedDialog = false
                }) {
                    Text("重置")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 播放模式选择弹窗
    if (showPlayModeDialog) {
        AlertDialog(
            onDismissRequest = { showPlayModeDialog = false },
            title = {
                Text(
                    "播放模式",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PlaybackMode.entries.forEach { mode ->
                        val isSelected = vm.playMode.value == mode
                        Surface(
                            onClick = {
                                vm.setPlayMode(mode)
                                showPlayModeDialog = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(0.12f)
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 图标
                                val customIconRes = mode.getCustomIconRes()
                                if (customIconRes != null) {
                                    Icon(
                                        painter = painterResource(id = customIconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Icon(
                                        imageVector = mode.getIcon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                // 标签
                                Text(
                                    text = mode.label,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                // 选中勾选
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        // 分隔线（除了最后一项）
                        if (mode != PlaybackMode.entries.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(0.06f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlayModeDialog = false }) {
                    Text("关闭")
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        )
    }
}

/**
 * 分p视频胶囊标识，与歌单详情页样式一致
 */
@Composable
fun MultiPageTaijiBadge(
    pageIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Badge(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
    ) {
        Text("P$pageIndex/$pageCount", fontSize = 11.sp)
    }
}

/**
 * 逐字歌词列表（仿 Neri AppleMusicLyric）。
 * 外层仅依赖 currentIndex（VM 每 500ms 更新一次），避免逐帧重组。
 * 当前行内的逐字动画由 AppleMusicActiveLine 内部自行驱动。
 */
@Composable
fun LyricList(vm: MusicViewModel) {
    val listState = rememberLazyListState()
    val currentIndex by vm.currentLineIndex
    // currentPosition 读取仅用于传递给当前行做内部动画，不用于外层重组判断
    val currentPositionMs by remember { derivedStateOf { vm.currentPosition.longValue } }

    LaunchedEffect(currentIndex) {
        if (vm.currentLrc.isNotEmpty())
            listState.animateScrollToItem(currentIndex)
    }

    val transByIndex = remember(vm.currentLrc.size, vm.currentTranslatedLrc.size) {
        matchTranslationsToLineIndices(vm.currentLrc, vm.currentTranslatedLrc)
    }

    val activeColor = MaterialTheme.colorScheme.primary
    val playedColor = Color.White.copy(alpha = 0.55f)
    val inactiveColor = Color.White.copy(alpha = 0.30f)

    // 读取歌词字体大小设置
    val context = LocalContext.current
    val lyricFontSize = remember {
        mutableStateOf(PlaybackSettingsStore.getLyricFontSize(context))
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalEdgeFade(fadeHeight = 72.dp),
        contentAlignment = Alignment.Center
    ) {
        val centerPad = maxHeight / 5f
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            contentPadding = PaddingValues(top = centerPad, bottom = centerPad + navBarPadding)
        ) {
            itemsIndexed(vm.currentLrc, key = { _, line -> "${line.startTimeMs}:${line.endTimeMs}" }) { index, line ->
                val isCurrent by remember {
                    derivedStateOf { index == currentIndex }
                }
                val isPlayed by remember {
                    derivedStateOf { index < currentIndex }
                }

                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.seekTo(line.startTimeMs) }
                        .padding(vertical = 8.dp)
                ) {
                    if (isCurrent) {
                        AppleMusicActiveLine(
                            line = line,
                            currentTimeMs = currentPositionMs,
                            activeColor = activeColor,
                            inactiveColor = activeColor.copy(alpha = 0.30f),
                            fontSize = lyricFontSize.value.sp,
                            fadeWidth = 12.dp
                        )
                    } else {
                        Text(
                            text = line.text,
                            color = if (isPlayed) playedColor else inactiveColor,
                            fontSize = lyricFontSize.value.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    transByIndex[index]?.let { t ->
                        Text(
                            text = t.text,
                            color = if (isCurrent) activeColor.copy(alpha = 0.85f)
                                    else Color.White.copy(alpha = if (isPlayed) 0.55f else 0.30f),
                            fontSize = (lyricFontSize.value * 0.78f).sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * 歌词控件上下边界渐变模糊
 * 参考 NeriPlayer 实现，使用 DstIn 混合模式绘制垂直渐变遮罩
 */
private fun Modifier.verticalEdgeFade(fadeHeight: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = (fadeHeight.toPx() / size.height).coerceIn(0f, 0.5f)
        val brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f       to Color.Transparent,
                edge       to Color.Black,
                (1f - edge) to Color.Black,
                1.0f       to Color.Transparent
            )
        )
        drawRect(brush = brush, size = size, blendMode = BlendMode.DstIn)
    }

/**
 * 格式化时间
 * 将毫秒转换为 mm:ss 格式
 * @param ms 毫秒数
 * @return 格式化后的时间字符串
 */
fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}

@Composable
fun AddPlaylistToQueueDialog(
    vm: MusicViewModel,
    onDismiss: () -> Unit,
    onPlaylistSelected: (PlaylistQueueItem) -> Unit
) {
    val context = LocalContext.current
    var userPlaylists by remember { mutableStateOf<List<UserSyncedPlaylist>>(emptyList()) }
    var homePlaylists by remember { mutableStateOf<List<HomePlaylistInfo>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        isLoading = true
        userPlaylists = PlaylistDataStore.getAll(context)
        homePlaylists = PlaylistSyncManager.getAllHomePlaylists()
        isLoading = false
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到播放队列") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("我的歌单") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("推荐歌单") }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    when (selectedTab) {
                        0 -> {
                            if (userPlaylists.isEmpty()) {
                                Text(
                                    "暂无歌单",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 32.dp)
                                )
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(userPlaylists) { playlist ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onPlaylistSelected(
                                                        PlaylistQueueItem(
                                                            id = playlist.id,
                                                            name = playlist.name,
                                                            coverPic = playlist.coverPic,
                                                            songs = playlist.songs
                                                        )
                                                    )
                                                }
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AsyncImage(
                                                model = playlist.coverPic,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(id = getRandomPlaceholderId())
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = playlist.name,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 15.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${playlist.songs.size} 首",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "添加",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f))
                                    }
                                }
                            }
                        }
                        1 -> {
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(homePlaylists) { playlist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    val songs = PlaylistSyncManager.fetchPlaylist(playlist.playlistId, context)
                                                    if (!songs.isNullOrEmpty()) {
                                                        onPlaylistSelected(
                                                            PlaylistQueueItem(
                                                                id = playlist.playlistId,
                                                                name = playlist.name,
                                                                coverPic = playlist.coverUrl,
                                                                songs = songs
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = playlist.coverUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = getRandomPlaceholderId())
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlist.name,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = playlist.subTitle,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "添加",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(0.1f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
