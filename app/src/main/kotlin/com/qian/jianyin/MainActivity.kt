package com.qian.jianyin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
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
import android.os.Build
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.launch
import android.content.Intent
import android.content.Context
import com.qian.jianyin.OnboardingManager
import com.qian.jianyin.OnboardingScreen
import androidx.media3.common.util.UnstableApi

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
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
        setContent {
            val context = LocalContext.current
            val onboardingManager = remember { OnboardingManager(context) }
            val isFirstLaunch = remember { mutableStateOf(onboardingManager.isFirstLaunch()) }
            
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
                    MainScreenFramework(vm)
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
fun MusicSlider(vm: MusicViewModel, style: Int = 0) {
    val pos = vm.currentPosition.longValue.toFloat()
    val total = vm.totalDuration.longValue.coerceAtLeast(1L).toFloat()

    Slider(
        value = pos.coerceIn(0f, total),
        onValueChange = { vm.seekTo(it.toLong()) },
        valueRange = 0f..total,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.Gray
        ),
        modifier = Modifier.fillMaxWidth()
    )
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
    
    val navItems = listOf(
        NavItem("首页", Icons.Filled.Home, Icons.Outlined.Home),
        NavItem("搜索", Icons.Filled.Search, Icons.Outlined.Search),
        NavItem("我的", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // 移除外层 Column 的 navigationBarsPadding，让背景沉浸
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
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
                    windowInsets = NavigationBarDefaults.windowInsets
                ) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    imageVector = if (selectedItem == index) item.selectedIcon else item.unselectedIcon, 
                                    contentDescription = item.label 
                                ) 
                            },
                            label = { Text(item.label) },
                            selected = selectedItem == index,
                            onClick = { selectedItem = index }
                        )
                    }
                }
                // 移除了之前的 Spacer(Modifier.windowInsetsBottomHeight...)
            }
        }
    ) { innerPadding ->
        // innerPadding 会自动包含底部所有组件（包括小白条避让区）的总高度
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            AnimatedContent(
                targetState = selectedItem,
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
                    0 -> HomeScreen(vm)
                    1 -> SearchScreen(vm)
                    2 -> MyMusicScreenV2(vm)
                }
            }
        }
    }

    if (vm.isPlayerSheetVisible.value) {
        FullPlayerScreen(vm)
    }
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(vm: MusicViewModel) {
    val song = vm.currentSong.value ?: return
    var showLrc by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noIndication = remember { MutableInteractionSource() }

    // --- 优化后的背景动画逻辑 ---
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidBackground")
    
    // 1. 稍微减小位移范围 (从180降到120)，防止边缘露出
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -160f, targetValue = 120f, 
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Reverse), label = "x"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 100f, targetValue = -100f, 
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Reverse), label = "y"
    )
    
    // 2. 显著增加缩放倍数 (从2.0起步改为3.0起步)，确保旋转时四个角都能覆盖屏幕
    val scale by infiniteTransition.animateFloat(
        initialValue = 3.2f, targetValue = 4.0f, 
        animationSpec = infiniteRepeatable(tween(20000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f, 
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)), label = "r"
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { vm.isPlayerSheetVisible.value = false },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Black,
        windowInsets = WindowInsets(0),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize()) {
            // 背景渲染层
            Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                            // 兼容性模糊处理
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                // Android 12+ 使用原生模糊
                                Modifier.blur(radius = 20.dp)
                            } else {
                                // Android 12 以下不使用实时模糊，只降低透明度
                                Modifier
                            }
                        )
                        .graphicsLayer(alpha = 0.6f)
                )
            }
            // 压黑渐变层
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(
                    Color.Black.copy(0.5f), 
                    Color.Transparent, 
                    Color.Black.copy(0.9f)
                ))
            ))

            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { 
                        scope.launch {
                            sheetState.hide()
                            vm.isPlayerSheetVisible.value = false
                        }
                    }, Modifier.padding(top = 12.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { showMoreMenu = true }, Modifier.padding(top = 12.dp)) {
                        Icon(Icons.Default.MoreVert, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth().clickable(noIndication, null) { showLrc = !showLrc }, Alignment.Center) {
                    if (!showLrc) {
                        AsyncImage(
                            song.pic, 
                            null, 
                            Modifier.fillMaxWidth(0.85f).aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                        )
                    } else {
                        LyricList(vm)
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!showLrc) {
                        Text(song.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(song.artist, color = Color.White.copy(0.6f), fontSize = 16.sp)
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    MusicSlider(vm)
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(formatTime(vm.currentPosition.longValue), color = Color.White.copy(0.5f), fontSize = 12.sp)
                        Text(formatTime(vm.totalDuration.longValue), color = Color.White.copy(0.5f), fontSize = 12.sp)
                    }
                    
                    Spacer(Modifier.height(20.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.previousSong() }) { 
                            Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(44.dp)) 
                        }
                        Spacer(Modifier.width(32.dp))
                        FloatingActionButton(onClick = { vm.togglePlay() }, shape = CircleShape, containerColor = Color.White) {
                            Icon(if (vm.isPlaying.value) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(40.dp))
                        }
                        Spacer(Modifier.width(32.dp))
                        IconButton(onClick = { vm.nextSong() }) { 
                            Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(44.dp)) 
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    
                    // 底部操作按钮行：将播放模式聚合按钮、收藏按钮和播放列表按钮放在一行
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 40.dp),
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
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        
                        // 收藏按钮（预留，功能待实现）
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
                        Color(0xFFFF4081)  // 已收藏时使用粉色
                    } else {
                        Color.White.copy(alpha = 0.8f)  // 未收藏时使用半透明白色
                    }
                )
            }

                        
                        // 播放列表按钮（原有的）
                        IconButton(onClick = { showQueue = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.QueueMusic, null, tint = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
        
        if (showMoreMenu) {
            ModalBottomSheet(onDismissRequest = { showMoreMenu = false }, containerColor = Color(0xFF151515)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("更多选项", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Divider(color = Color.White.copy(0.2f))
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isDownloading) {
                                    isDownloading = true
                                    scope.launch {
                                        val customPath = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomPath(context) else null
                                        DownloadManager.downloadSong(context, song, customPath)
                                            .onSuccess { message ->
                                                Toast.makeText(context, "下载完成: ${song.name}", Toast.LENGTH_LONG).show()
                                            }
                                            .onFailure { e ->
                                                Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = if (isDownloading) "下载中..." else "下载歌曲",
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Divider(color = Color.White.copy(0.2f))
                }
            }
        }
        
        if (showQueue) {
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
            
            ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = Color(0xFF151515)) {
                Column(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 400.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("播放列表", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${vm.playQueue.size} 首",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                    LazyColumn(state = queueState) {
                        itemsIndexed(vm.playQueue) { _, s ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    s.name,
                                    color = if (s == currentSong) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.weight(1f).clickable { vm.playSong(s) }.padding(12.dp),
                                    maxLines = 1
                                )
                                IconButton(
                                    onClick = { vm.removeFromQueue(s) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "移除",
                                        tint = Color.White.copy(alpha = 0.6f)
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
    LaunchedEffect(currentIndex) { if (vm.currentLrc.isNotEmpty()) state.animateScrollToItem(currentIndex, scrollOffset = -500) }
    
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Spacer(Modifier.height(320.dp)) }
        itemsIndexed(vm.currentLrc) { index, line ->
            Text(
                text = line.text, 
                color = if (index == currentIndex) Color.White else Color.White.copy(0.4f),
                fontSize = if (index == currentIndex) 22.sp else 18.sp, 
                textAlign = TextAlign.Center, 
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { vm.seekTo(line.time) }
            )
        }
        item { Spacer(Modifier.height(320.dp)) }
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
