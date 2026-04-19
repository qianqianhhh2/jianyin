package com.qian.jianyin

import android.os.Bundle
import android.net.Uri
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.GifDecoder
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.qian.jianyin.ProgressBarStyle
import kotlin.math.cos
import kotlin.math.sin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.compose.BackHandler
import android.os.Build
import android.util.Log
import android.app.Activity
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.Color as AndroidColor
import android.widget.Toast
import kotlinx.coroutines.launch
import android.content.Intent
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
import androidx.media3.common.util.UnstableApi
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

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
    // 用于存储 ViewModel 引用，以便在 onActivityResult 中使用
    private var viewModel: MusicViewModel? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 初始化 Coil ImageLoader 以支持 GIF
        val imageLoader = coil.ImageLoader.Builder(this)
            .components {
                add(coil.decode.GifDecoder.Factory())
            }
            .build()
        coil.Coil.setImageLoader(imageLoader)
        
        val mediaSessionManager = MediaSessionManager.getInstance(this)
        mediaSessionManager.initialize()
        
        startKeepAliveServices(this)
        
        mediaSessionManager.controlCallback = object : MediaSessionManager.MediaControlCallback {
            override fun onPlay() {
                // 这里需要获取 ViewModel 实例
                // 由于在 onCreate 中无法直接获取 Compose ViewModel，
                // 我们将在 setContent 内部设置回调
            }
            override fun onPause() {
                // 同上
            }
            override fun onNext() {
                // 同上
            }
            override fun onPrevious() {
                // 同上
            }
            override fun onStop() {
                // 同上
            }
            override fun onSeekTo(position: Long) {
                // 同上
            }
        }
        
        // 启动时获取一言
        lifecycleScope.launch {
            HitokotoManager.getHitokotoAndShow(this@MainActivity)
        }
        
        setContent {
            val context = LocalContext.current
            val onboardingManager = remember { OnboardingManager(context) }
            val isFirstLaunch = remember { mutableStateOf(onboardingManager.isFirstLaunch()) }
            
            // 版本更新相关状态
            val showVersionUpdateDialog = remember { mutableStateOf(false) }
            val versionUpdate = remember { mutableStateOf<VersionUpdate?>(null) }
            
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (darkTheme) darkColorScheme(
                primary = Color(0xFFA7C2F7),
                primaryContainer = Color(0xFF004187),
                secondary = Color(0xFFBAC4D8),
                secondaryContainer = Color(0xFF3B4758),
                background = Color(0xFF121212),
                surface = Color(0xFF1E1E1E)
            ) else lightColorScheme(
                primary = Color(0xFF0B57D0),
                primaryContainer = Color(0xFFD3E3FD),
                secondary = Color(0xFF535F73),
                secondaryContainer = Color(0xFFD6E0F0),
                background = Color(0xFFF0F4F9),
                surface = Color(0xFFF0F4F9)
            )
            MaterialTheme(colorScheme = colorScheme) {
                if (isFirstLaunch.value) {
                    OnboardingScreen(onComplete = {
                        onboardingManager.markAsCompleted()
                        isFirstLaunch.value = false
                    })
                } else {
                    val vm: MusicViewModel = viewModel() // 获取 ViewModel 实例
                    // 保存 ViewModel 引用到成员变量
                    viewModel = vm
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
    var refreshPlaylistTrigger by remember { mutableStateOf(0) }
    
    // 添加返回键处理
    BackHandler(vm.isPlayerSheetVisible.value) {
        vm.isPlayerSheetVisible.value = false
    }
    
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
            // 移除外层 Column 的 navigationBarsPadding，让背景沉浸
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
                    // 关键：NavigationBar 默认就会处理 navigationBars 并在内部预留 Padding
                    // 这样背景颜色会充满底部小白条区域，但图标会被推上去
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
                // 移除了之前的 Spacer(Modifier.windowInsetsBottomHeight...)
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
                    contentScale = ContentScale.Crop
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
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(vm: MusicViewModel, refreshPlaylistTrigger: (() -> Unit)? = null) {
    val song = vm.currentSong.value ?: return
    var showLrc by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showProgressBarStyleDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var sleepTimerTime by remember { mutableStateOf("23:00") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 处理返回键，优先关闭弹出的菜单
    BackHandler(showMoreMenu || showQueue) {
        if (showMoreMenu) {
            showMoreMenu = false
        } else if (showQueue) {
            showQueue = false
        }
    }

    // 只隐藏传统的三大金刚键，不隐藏小白条
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val window = (context as Activity).window
        val insetsController = window.insetsController
        if (insetsController != null) {
            // 检查系统导航模式
            val hasBackKey = ViewConfiguration.get(context).hasPermanentMenuKey()
            
            // 只有在传统的三大金刚键模式下才隐藏导航栏
            if (hasBackKey) {
                insetsController.hide(WindowInsets.Type.navigationBars())
                insetsController.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
            AsyncImage(
                model = song.pic, 
                contentDescription = null, 
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale, 
                        scaleY = scale, 
                        translationX = offsetX, 
                        translationY = offsetY, 
                        rotationZ = rotation
                    )
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(radius = 20.dp)
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer(alpha = 0.6f)
            )
        }

        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(
                MaterialTheme.colorScheme.surface.copy(0.5f), 
                MaterialTheme.colorScheme.surface.copy(0.1f), 
                MaterialTheme.colorScheme.surface.copy(0.9f)
            ))
        ))

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    vm.isPlayerSheetVisible.value = false
                }) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !showLrc,
                    enter = fadeIn(animationSpec = tween(durationMillis = 500)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 500))
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showLrc = !showLrc },
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        AsyncImage(
                            song.pic, 
                            null, 
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showLrc,
                    enter = fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 200)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 500))
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { showLrc = !showLrc },
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LyricList(vm)
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 32.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(song.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                }
                
                Spacer(Modifier.height(16.dp))
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
                    FloatingActionButton(onClick = { vm.togglePlay() }, shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary) {
                        Icon(
                            imageVector = if (vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, 
                            contentDescription = null, 
                            tint = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onPrimary, 
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.width(32.dp))
                    IconButton(onClick = { vm.nextSong() }) { 
                        Icon(Icons.Default.SkipNext, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(44.dp)) 
                    }
                }
                Spacer(Modifier.height(20.dp))
                
                // 底部操作按钮行：将播放模式聚合按钮、收藏按钮和播放列表按钮放在一行
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 播放模式聚合按钮（顺序/随机/单曲循环）
                    IconButton(
                        onClick = { vm.togglePlayMode() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = vm.playMode.value.getIcon(), // 使用 PlaybackMode 的 getIcon 方法
                            contentDescription = vm.playMode.value.label,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                Color(0xFFFF4444)  // 固定使用危险红，与浅色模式一致
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant  // 未收藏时使用变体颜色
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
                                            val customPath = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomPath(context) else null
                                            DownloadManager.downloadSong(
                                                context, 
                                                song, 
                                                customPath
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
    
    LaunchedEffect(currentSong, showQueue) {
        if (showQueue && currentSong != null) {
            val index = vm.playQueue.indexOfFirst { it == currentSong }
            if (index != -1) {
                queueState.animateScrollToItem(index, scrollOffset = 30)
            }
        }
    }
    
    // 使用Box和动画效果替代ModalBottomSheet
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
                    .heightIn(max = 400.dp)
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
                        Text("播放列表", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${vm.playQueue.size} 首",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                    LazyColumn(state = queueState) {
                        itemsIndexed(vm.playQueue) { index, s ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        // 显示排名序号
                                        Text(
                                            "${index + 1}",
                                            color = if (s == currentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                                        )
                                        
                                        // 为当前播放的歌曲添加音频律动效果
                                        if (s == currentSong) {
                                            Box(modifier = Modifier
                                                .width(32.dp)
                                                .height(24.dp)
                                                .padding(4.dp)
                                            ) {
                                                // 三条垂直圆柱，模拟音频律动
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
                                                    
                                                    // 根据播放状态控制显示
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
                                        } else {
                                            // 非当前播放歌曲的占位空间
                                            Spacer(modifier = Modifier.width(32.dp))
                                        }
                                        
                                        // 显示歌曲封面
                                        AsyncImage(
                                            model = s.pic, 
                                            contentDescription = null, 
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSystemInDarkTheme()) 
                                                        Color(0xFF4A5568)
                                                    else 
                                                        Color(0xFFFFFEFE)
                                                ), 
                                            contentScale = ContentScale.Crop
                                        )
                                        
                                        Column(modifier = Modifier.clickable { vm.playSong(s) }.padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)) {
                                            Text(
                                                s.name,
                                                color = if (s == currentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                fontSize = 16.sp,
                                                maxLines = 1
                                            )
                                            Text(
                                                s.artist,
                                                color = if (s == currentSong) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 13.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    // 移除按钮
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
                                // 添加分割线，除了最后一首歌曲
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
}

/**
 * 歌词列表组件
 * 显示当前歌曲的歌词，并支持自动滚动到当前播放行
 * @param vm 音乐视图模型
 */
@Composable
fun LyricList(vm: MusicViewModel) {
    val state = rememberLazyListState()
    val currentIndex by vm.currentLineIndex
    LaunchedEffect(currentIndex) { 
        if (vm.currentLrc.isNotEmpty()) {
            state.animateScrollToItem(currentIndex, scrollOffset = 0)
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(32.dp))
        LazyColumn(
            state = state, 
            modifier = Modifier.weight(1f), 
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            itemsIndexed(vm.currentLrc) { index, line ->
                Text(
                        text = line.text, 
                        color = if (index == currentIndex) MaterialTheme.colorScheme.primary else Color.White.copy(0.4f),
                        fontSize = if (index == currentIndex) 22.sp else 18.sp, 
                        textAlign = TextAlign.Center, 
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .clickable { vm.seekTo(line.time) }
                    )
            }
        }
        Spacer(Modifier.height(32.dp))
    }
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
