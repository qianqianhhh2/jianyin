package com.qian.jianyin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.roundToInt
import com.qian.jianyin.bili.BiliWebLoginHelper
import com.qian.jianyin.bili.BiliApi
import com.qian.jianyin.netease.api.NeteaseApiService
import com.qian.jianyin.R
import com.qian.jianyin.MainActivity
import com.qian.jianyin.BuildConfig
import com.qian.jianyin.ui.ThemeColorUtil
import com.qian.jianyin.util.VibrationManager
import android.app.Activity


data class SettingsItem(
    val title: String,
    val icon: ImageVector,
    val subtitle: String,
    val id: String
)

@Composable
fun SectionHeaderV6(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (() -> Unit)? = null, actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = cs.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = cs.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (onClick != null) { IconButton(onClick = onClick) { Icon(actionIcon ?: icon, null, tint = cs.primary) } }
    }
}

private fun getRandomPlaceholderId(): Int {
    val ids = listOf(R.drawable.miku_1, R.drawable.miku_2, R.drawable.miku_3, R.drawable.miku_4, R.drawable.miku_5, R.drawable.miku_6, R.drawable.miku_7, R.drawable.miku_8, R.drawable.miku_9)
    return ids.random()
}

@Composable
fun SongItemV6(song: Song, cs: ColorScheme, onClick: () -> Unit) {
    val mikuPainter = painterResource(id = getRandomPlaceholderId())
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = song.pic, contentDescription = null, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant), contentScale = ContentScale.Crop, error = mikuPainter)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(song.name, color = cs.onBackground, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(song.artist, color = cs.onSurfaceVariant, fontSize = 13.sp)
                // 分p视频标识
                if (song.isPartOfMultiPage) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = cs.primaryContainer,
                        contentColor = cs.onPrimaryContainer
                    ) {
                        Text("P${song.pageIndex}/${song.pageCount}", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistItemV6(playlist: UserSyncedPlaylist, colorScheme: ColorScheme, onClick: () -> Unit, onLongClick: () -> Unit) {
    val mikuPainter = painterResource(id = getRandomPlaceholderId())
    val coverModel = playlist.coverPic.ifBlank { null }
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (coverModel != null) {
            AsyncImage(model = coverModel, contentDescription = null, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(colorScheme.surfaceVariant), contentScale = ContentScale.Crop, error = mikuPainter)
        } else {
            Image(painter = mikuPainter, contentDescription = null, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(playlist.name, color = colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.songs.size} 首歌曲", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalAnimationApi::class, ExperimentalHazeApi::class)
@Composable
fun MyMusicScreenV2(
    vm: MusicViewModel,
    innerPadding: PaddingValues,
    refreshTrigger: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    // 歌单数据状态
    val syncedPlaylists = remember { mutableStateListOf<UserSyncedPlaylist>() }
    var activePlaylist by remember { mutableStateOf<UserSyncedPlaylist?>(null) }
    var isLoadingSongs by remember { mutableStateOf(false) }

    // 最近播放详情页状态
    var activeRecentPlaylist by remember { mutableStateOf<List<Song>?>(null) }

    // 弹窗与菜单状态
    var showAddDialog by remember { mutableStateOf(false) }
    var playlistIdInput by remember { mutableStateOf("") }
    var selectedPlaylistForMenu by remember { mutableStateOf<UserSyncedPlaylist?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newNameInput by remember { mutableStateOf("") }

    // 新增：设置相关状态
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var showDownloadPathDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var useCustomPath by remember { mutableStateOf(false) }
    var customUri by remember { mutableStateOf<Uri?>(null) }
    // 音质设置相关状态
    var showAudioQualityScreen by remember { mutableStateOf(false) }
    var selectedDownloadQuality by remember { mutableStateOf("exhigh") }
    var selectedPlayQuality by remember { mutableStateOf("exhigh") }

    // 歌词来源设置相关状态
    var showLyricSourceDialog by remember { mutableStateOf(false) }
    var selectedLyricSource by remember {
        mutableStateOf(
            DownloadSettingsStore.getLyricSource(
                context
            )
        )
    } // 0: 内嵌, 1: 网络

    // 歌词字体大小设置相关状态
    var showLyricFontSizeDialog by remember { mutableStateOf(false) }
    var lyricFontSize by remember {
        mutableStateOf(
            PlaybackSettingsStore.getLyricFontSize(context)
        )
    }

    // 屏幕常亮设置相关状态
    var keepScreenOnEnabled by remember {
        mutableStateOf(
            PlaybackSettingsStore.isKeepScreenOnEnabled(context)
        )
    }


    // 渐变层亮度设置相关状态
    var showGradientBrightnessDialog by remember { mutableStateOf(false) }
    var gradientBrightnessMultiplier by remember {
        mutableStateOf(
            PlaybackSettingsStore.getGradientBrightnessMultiplier(context)
        )
    }

    // 歌曲淡入淡出设置
    var fadeEnabled by remember {
        mutableStateOf(
            DownloadSettingsStore.isFadeEnabled(context)
        )
    }


    // 自动缓存设置
    var autoCacheEnabled by remember {
        mutableStateOf(
            DownloadSettingsStore.isAutoCacheEnabled(context)
        )
    }

    // 震动设置
    var vibrationEnabled by remember {
        mutableStateOf(
            DownloadSettingsStore.isVibrationEnabled(context)
        )
    }

    // 默认音乐打开方式设置
    var defaultOpenerEnabled by remember {
        mutableStateOf(
            DownloadSettingsStore.isDefaultMusicOpenerEnabled(context)
        )
    }

    // 备用音源设置相关状态
    var showBackupAudioDialog by remember { mutableStateOf(false) }
    var backupAudioApiUrl by remember {
        mutableStateOf(
            DownloadSettingsStore.getBackupAudioApiUrl(context)
        )
    }

    // 深色模式设置相关状态
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var selectedDarkMode by remember {
        mutableStateOf(
            DownloadSettingsStore.getDarkMode(context)
        )
    } // 0: 跟随系统, 1: 浅色, 2: 深色

    // 主题色设置相关状态
    var showThemeDialog by remember { mutableStateOf(false) }
    var selectedThemeSource by remember { mutableStateOf(DownloadSettingsStore.getThemeSource(context)) }
    var selectedSeedColor by remember { mutableStateOf(DownloadSettingsStore.getSeedColor(context)) }

    // 启动设置相关状态
    var showStartupSettingsDialog by remember { mutableStateOf(false) }
    var showNeteaseLogoutDialog by remember { mutableStateOf(false) }
    var showBiliLogoutDialog by remember { mutableStateOf(false) }
    var showAccountExpand by remember { mutableStateOf(false) }
    var showPlayerExpand by remember { mutableStateOf(false) }
    var keepPlaylistOnExitEnabled by remember {
        mutableStateOf(
            DownloadSettingsStore.isKeepPlaylistOnExitEnabled(context)
        )
    }
    var autoPlayOnStartEnabled by remember {
        mutableStateOf(
            DownloadSettingsStore.isAutoPlayOnStartEnabled(context)
        )
    }

    // 版本检查相关状态
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showVersionUpdateDialog by remember { mutableStateOf(false) }
    var versionUpdateInfo by remember { mutableStateOf<VersionUpdate?>(null) }
    // 从 PackageManager 获取应用版本号，并自动添加 debug/release 后缀
    val appVersion = remember {
        try {
            val versionName =
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    ?: "未知版本"
            val buildType = if (BuildConfig.DEBUG) "debug" else "release"
            "$versionName-$buildType"
        } catch (e: Exception) {
            "未知版本"
        }
    }

    // 本地音乐管理
    val localMusicManager = remember { LocalMusicManager(context) }
    var isScanningLocalMusic by remember { mutableStateOf(false) }
    var folderUri by remember { mutableStateOf<Uri?>(null) }

    // 从分享内容中提取歌单ID
    fun extractPlaylistId(shareContent: String): String? {
        // 尝试从URL中提取id参数
        val regex = Regex("id=([0-9]+)")
        val matchResult = regex.find(shareContent)
        return matchResult?.groupValues?.get(1)
    }

    // 处理文件夹选择结果
    LaunchedEffect(folderUri) {
        folderUri?.let { uri ->
            // 从Uri获取文件夹路径
            val folderPath = localMusicManager.getPathFromUri(uri)

            if (folderPath != null) {
                // 扫描本地歌曲
                isScanningLocalMusic = true
                try {
                    val songs = localMusicManager.scanFolder(folderPath)
                    if (songs.isNotEmpty()) {
                        // 创建本地歌单
                        val playlistId = "local_${System.currentTimeMillis()}"
                        val playlistName = "本地歌单_${songs.size}首"
                        val newPlaylist = UserSyncedPlaylist(
                            id = playlistId,
                            name = playlistName,
                            coverPic = "", // 本地歌单默认无封面
                            songs = songs
                        )
                        PlaylistDataStore.save(context, newPlaylist)
                        syncedPlaylists.clear()
                        syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                        Toast.makeText(
                            context,
                            "本地歌单导入成功，共 ${songs.size} 首歌曲",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "未找到音乐文件", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "扫描失败：${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isScanningLocalMusic = false
                    folderUri = null
                }
            }
        }
    }

    val statsManager = remember { MusicStatsManager(context) }
    val favoriteSongs = statsManager.getTopFavorites(vm.historyList)

    // 初始化加载本地保存的歌单
    LaunchedEffect(Unit) {
        syncedPlaylists.clear()
        // 获取所有歌单（包含收藏歌单）
        syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
        // 确保收藏歌单存在
        PlaylistDataStore.getFavoritesPlaylist(context)
    }

    // 当refreshTrigger变化时，刷新歌单数据
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            syncedPlaylists.clear()
            syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
        }
    }

    // 监听歌单更新触发器，当封面更新时重新加载歌单
    LaunchedEffect(vm.playlistUpdateTrigger.intValue) {
        syncedPlaylists.clear()
        syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )
    val lazyListState = rememberLazyListState()

    val previewSongs by remember {
        derivedStateOf {
            vm.historyList.takeLast(10).reversed()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            LargeTopAppBar(
                title = { Text("我的音乐", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                modifier = Modifier.padding(top = 8.dp)
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                // 最近播放预览板块（显示10首）
                if (vm.historyList.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "最近播放",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.onBackground
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        activeRecentPlaylist = vm.historyList.takeLast(50).reversed()
                                    }
                                ) {
                                    Text(
                                        "查看更多",
                                        fontSize = 13.sp,
                                        color = colorScheme.primary,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(previewSongs) { song ->
                                    val itemHazeState = remember { HazeState() }
                                    Box(
                                        modifier = Modifier
                                            .width(120.dp)
                                            .clickable {
                                                if (song.source == SongSource.NETEASE) {
                                                    vm.playNeteaseSong(song, previewSongs)
                                                } else {
                                                    vm.playSong(song, previewSongs)
                                                }
                                            }
                                    ) {
                                        AsyncImage(
                                            model = song.pic,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(120.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(colorScheme.surfaceVariant)
                                                .hazeSource(itemHazeState),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = getRandomPlaceholderId())
                                        )
                                        // 分p标识 - 在封面右上角
                                        if (song.isPartOfMultiPage) {
                                            MultiPageTaijiBadge(
                                                pageIndex = song.pageIndex,
                                                pageCount = song.pageCount,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(6.dp)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(120.dp, 40.dp)
                                                .clip(
                                                    RoundedCornerShape(
                                                        bottomStart = 16.dp,
                                                        bottomEnd = 16.dp
                                                    )
                                                )
                                                .hazeEffect(
                                                    itemHazeState,
                                                    HazeStyle(
                                                        blurRadius = 10.dp,
                                                        tint = HazeTint(Color.Black.copy(alpha = 0.2f))
                                                    )
                                                )
                                                .align(Alignment.BottomStart)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp)
                                                    .padding(bottom = 4.dp),
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    song.name,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    softWrap = false
                                                )
                                                Text(
                                                    song.artist,
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }

                // 最近最爱部分
                if (favoriteSongs.isNotEmpty()) {
                    item {
                        SectionHeaderV6("最近最爱", Icons.Default.Star)
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(favoriteSongs) { song ->
                                val itemHazeState = remember { HazeState() }
                                Box(
                                    modifier = Modifier.width(120.dp)
                                        .clickable {
                                            if (song.source == SongSource.NETEASE) {
                                                vm.playNeteaseSong(song, favoriteSongs)
                                            } else {
                                                vm.playSong(song, favoriteSongs)
                                            }
                                        }) {
                                    AsyncImage(
                                        model = song.pic,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .hazeSource(itemHazeState),
                                        contentScale = ContentScale.Crop,
                                        error = painterResource(id = getRandomPlaceholderId())
                                    )
                                    // 分p标识 - 在封面右上角
                                    if (song.isPartOfMultiPage) {
                                        MultiPageTaijiBadge(
                                            pageIndex = song.pageIndex,
                                            pageCount = song.pageCount,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(120.dp, 40.dp)
                                            .clip(
                                                RoundedCornerShape(
                                                    bottomStart = 16.dp,
                                                    bottomEnd = 16.dp
                                                )
                                            )
                                            .hazeEffect(
                                                itemHazeState,
                                                HazeStyle(
                                                    blurRadius = 10.dp,
                                                    tint = HazeTint(Color.Black.copy(alpha = 0.2f))
                                                )
                                            )
                                            .align(Alignment.BottomStart)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp)
                                                .padding(bottom = 4.dp),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                song.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                softWrap = false
                                            )
                                            Text(
                                                song.artist,
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(12.dp)) }

                // 同步歌单标题栏：改为 Add 图标
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LibraryMusic, null, tint = colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("我的歌单", color = colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, null, tint = colorScheme.primary)
                        }
                    }
                }

                if (syncedPlaylists.isEmpty()) {
                    item {
                        Text(
                            "暂无同步歌单，点击上方 + 号导入",
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                } else {
                    items(syncedPlaylists) { playlist ->
                        PlaylistItemV6(
                            playlist = playlist,
                            onClick = {
                                activePlaylist = playlist
                            },
                            onLongClick = {
                                selectedPlaylistForMenu = playlist
                            }
                        )
                    }
                }
                item { Spacer(Modifier.height(200.dp)) }
            }
        }

        // --- 弹窗逻辑 1：添加歌单 ---
            if (showAddDialog) {
                var selectedSource by remember { mutableStateOf(0) } // 0: 网易云歌单, 1: 本地歌单, 2: B站歌单, 3: 创建新歌单
                var isSyncingBili by remember { mutableStateOf(false) }
                var newPlaylistNameInput by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    containerColor = colorScheme.surface,
                    title = { Text("添加歌单") },
                    text = {
                        Column {
                            // 歌单来源选择
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f).clickable { selectedSource = 0 },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    RadioButton(
                                        selected = selectedSource == 0,
                                        onClick = { selectedSource = 0 }
                                    )
                                    Text("网易云歌单")
                                }
                                Column(
                                    modifier = Modifier.weight(1f).clickable { selectedSource = 1 },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    RadioButton(
                                        selected = selectedSource == 1,
                                        onClick = { selectedSource = 1 }
                                    )
                                    Text("本地歌单")
                                }
                                Column(
                                    modifier = Modifier.weight(1f).clickable { selectedSource = 2 },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    RadioButton(
                                        selected = selectedSource == 2,
                                        onClick = { selectedSource = 2 }
                                    )
                                    Text("B站歌单")
                                }
                                Column(
                                    modifier = Modifier.weight(1f).clickable { selectedSource = 3 },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    RadioButton(
                                        selected = selectedSource == 3,
                                        onClick = { selectedSource = 3 }
                                    )
                                    Text("新建歌单")
                                }
                            }

                            // 根据选择显示不同的输入界面
                            when (selectedSource) {
                                0 -> {
                                    if (NeteaseApiService.isLoggedIn) {
                                        Text("已登录网易云账号", color = colorScheme.primary)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = playlistIdInput,
                                            onValueChange = { playlistIdInput = it },
                                            label = { Text("输入分享内容") },
                                            placeholder = { Text("直接复制你从网易云复制的内容") },
                                            singleLine = true
                                        )
                                    } else {
                                        Text("未登录网易云账号", color = colorScheme.error)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(onClick = {
                                            showAddDialog = false
                                            (context as MainActivity).startNeteaseLogin()
                                        }) {
                                            Text("登录")
                                        }
                                    }
                                }

                                1 -> {
                                    Text("选择本地文件夹导入歌曲")
                                }

                                2 -> {
                                    when (vm.biliLoginState.value) {
                                        MusicViewModel.BiliLoginState.LoggedIn -> {
                                            Text("已登录B站账号", color = colorScheme.primary)
                                        }

                                        MusicViewModel.BiliLoginState.NotLoggedIn, MusicViewModel.BiliLoginState.Expired -> {
                                            Text("未登录或登录已过期", color = colorScheme.error)
                                        }

                                        MusicViewModel.BiliLoginState.Unknown -> {
                                            Text(
                                                "正在检查登录状态...",
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                
                                3 -> {
                                    OutlinedTextField(
                                        value = newPlaylistNameInput,
                                        onValueChange = { newPlaylistNameInput = it },
                                        label = { Text("歌单名称") },
                                        placeholder = { Text("请输入歌单名称") },
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        when (selectedSource) {
                            0 -> {
                                if (NeteaseApiService.isLoggedIn) {
                                    Button(onClick = {
                                        if (playlistIdInput.isBlank()) return@Button
                                        scope.launch {
                                            val playlistId = extractPlaylistId(playlistIdInput)
                                            if (playlistId.isNullOrBlank()) {
                                                Toast.makeText(
                                                    context,
                                                    "无法提取歌单ID，请检查输入",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                return@launch
                                            }
                                            val songs =
                                                PlaylistSyncManager.fetchPlaylist(playlistId, context)
                                            if (songs != null) {
                                                val newList = UserSyncedPlaylist(
                                                    playlistId,
                                                    "新歌单_${playlistId}",
                                                    songs.firstOrNull()?.pic ?: "",
                                                    songs
                                                )
                                                PlaylistDataStore.save(context, newList)
                                                syncedPlaylists.clear()
                                                syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                                showAddDialog = false
                                                playlistIdInput = ""
                                                Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT)
                                                    .show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "同步失败，请检查分享内容",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }) {
                                        Text("同步")
                                    }
                                } else {
                                    Button(onClick = {
                                        showAddDialog = false
                                        (context as MainActivity).startNeteaseLogin()
                                    }) {
                                        Text("登录")
                                    }
                                }
                            }

                            1 -> {
                                Button(onClick = {
                                    showAddDialog = false
                                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                    intent.addCategory(Intent.CATEGORY_DEFAULT)
                                    (context as? MainActivity)?.folderUriCallback = {
                                        folderUri = it
                                    }
                                    (context as? android.app.Activity)?.startActivityForResult(
                                        intent,
                                        1001
                                    )
                                }) {
                                    Text("选择文件夹")
                                }
                            }

                            2 -> {
                                when (vm.biliLoginState.value) {
                                    MusicViewModel.BiliLoginState.LoggedIn -> {
                                        Button(
                                            onClick = {
                                                if (isSyncingBili) return@Button
                                                scope.launch {
                                                    isSyncingBili = true
                                                    val playlists = vm.syncBiliPlaylists()
                                                    if (playlists != null) {
                                                        val context = context
                                                        playlists.forEach { playlist ->
                                                            PlaylistDataStore.save(
                                                                context,
                                                                playlist
                                                            )
                                                        }
                                                        syncedPlaylists.clear()
                                                        syncedPlaylists.addAll(
                                                            PlaylistDataStore.getAll(
                                                                context
                                                            )
                                                        )
                                                        Toast.makeText(
                                                            context,
                                                            "同步成功，共${playlists.size}个歌单",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "同步失败",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                    isSyncingBili = false
                                                    showAddDialog = false
                                                }
                                            },
                                            enabled = !isSyncingBili
                                        ) {
                                            if (isSyncingBili) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(
                                                        16.dp
                                                    ), strokeWidth = 2.dp
                                                )
                                            } else {
                                                Text("刷新")
                                            }
                                        }
                                    }

                                    MusicViewModel.BiliLoginState.NotLoggedIn, MusicViewModel.BiliLoginState.Expired -> {
                                        Button(onClick = {
                                            showAddDialog = false
                                            (context as MainActivity).startBiliLogin()
                                        }) {
                                            Text("登录")
                                        }
                                    }

                                    MusicViewModel.BiliLoginState.Unknown -> {
                                        Button(onClick = {}, enabled = false) {
                                            Text("等待")
                                        }
                                    }
                                }
                            }
                            
                            3 -> {
                                Button(
                                    onClick = {
                                        if (newPlaylistNameInput.isNotBlank()) {
                                            val newPlaylist = PlaylistDataStore.createPlaylist(
                                                context,
                                                newPlaylistNameInput.trim()
                                            )
                                            syncedPlaylists.clear()
                                            syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                            showAddDialog = false
                                            newPlaylistNameInput = ""
                                            Toast.makeText(
                                                context,
                                                "歌单创建成功",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    enabled = newPlaylistNameInput.isNotBlank()
                                ) {
                                    Text("创建")
                                }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showAddDialog = false
                        }) { Text("取消") }
                    }
                )
            }

            // --- 弹窗逻辑 2：长按菜单 ---
            if (selectedPlaylistForMenu != null) {
                val isFavoritesPlaylist =
                    selectedPlaylistForMenu?.id == "jianyin_favorites_playlist"
                ModalBottomSheet(
                    onDismissRequest = { selectedPlaylistForMenu = null },
                    containerColor = colorScheme.surface
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                        Text(
                            text = selectedPlaylistForMenu?.name ?: "",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.primary
                        )
                        // 菜单项：刷新
                        ListItem(
                            headlineContent = { Text("刷新歌单内容") },
                            leadingContent = { Icon(Icons.Default.Refresh, null) },
                            modifier = Modifier.clickable {
                                val target = selectedPlaylistForMenu!!
                                scope.launch {
                                    if (target.id == "jianyin_favorites_playlist") {
                                        // 收藏歌单不刷新歌曲列表
                                        Toast.makeText(context, "已更新！", Toast.LENGTH_SHORT)
                                            .show()
                                    } else if (target.id.startsWith("local_") && !target.isLocalPlaylist) {
                                        // 本地歌单：重新扫描文件夹
                                        val localMusicManager = LocalMusicManager(context)
                                        // 尝试从歌单中获取文件夹路径（假设第一首歌的url是文件夹路径）
                                        if (target.songs.isNotEmpty() && target.songs[0].url.isNotEmpty()) {
                                            val firstSongPath = target.songs[0].url
                                            val folderPath = File(firstSongPath).parent
                                            if (folderPath != null) {
                                                val songs = localMusicManager.scanFolder(folderPath)
                                                if (songs.isNotEmpty()) {
                                                    // 关键：为每首歌检查是否有自定义封面
                                                    val processedSongs = songs.map { song ->
                                                        val customCover =
                                                            SongCustomDataStore.getCover(
                                                                context,
                                                                song.url
                                                            )
                                                        if (customCover.isNotEmpty()) {
                                                            song.copy(pic = customCover)
                                                        } else {
                                                            song
                                                        }
                                                    }
                                                    // 更新歌单封面为第一首歌的封面（优先使用自定义封面）
                                                    val newCoverPic = processedSongs[0].pic
                                                    val updated = target.copy(
                                                        songs = processedSongs,
                                                        coverPic = newCoverPic
                                                    )
                                                    PlaylistDataStore.update(context, updated)
                                                    // 更新 UI 列表
                                                    val index =
                                                        syncedPlaylists.indexOfFirst { it.id == target.id }
                                                    if (index != -1) syncedPlaylists[index] =
                                                        updated
                                                    Toast.makeText(
                                                        context,
                                                        "已更新本地歌曲列表",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        context,
                                                        "未发现音乐文件",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "无法获取文件夹路径",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "歌单为空，无法刷新",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else if (target.id.startsWith("bili_")) {
                                        // B站歌单：从B站API获取
                                        val folderId = target.id.removePrefix("bili_").toLongOrNull()
                                        if (folderId != null) {
                                            val songs = withContext(Dispatchers.IO) { 
                                                BiliPlaylistSyncManager.fetchPlaylistItems(context, folderId) 
                                            } ?: emptyList()
                                            val updated = target.copy(songs = songs, coverPic = songs.firstOrNull()?.pic ?: target.coverPic)
                                            PlaylistDataStore.update(context, updated)
                                            val index = syncedPlaylists.indexOfFirst { it.id == target.id }
                                            if (index != -1) syncedPlaylists[index] = updated
                                            Toast.makeText(context, "已更新歌曲列表", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "无效的歌单ID", Toast.LENGTH_SHORT).show()
                                        }
                                        } else {
                                            // 网络歌单：从服务器获取
                                            val songs =
                                                PlaylistSyncManager.fetchPlaylist(target.id, context)
                                            if (songs != null) {
                                                // 更新歌单封面为第一首歌的封面
                                                val newCoverPic = songs.firstOrNull()?.pic ?: ""
                                                val updated = target.copy(
                                                    songs = songs,
                                                    coverPic = newCoverPic
                                                )
                                                PlaylistDataStore.update(context, updated)
                                                // 更新 UI 列表
                                                val index =
                                                    syncedPlaylists.indexOfFirst { it.id == target.id }
                                                if (index != -1) syncedPlaylists[index] = updated
                                                Toast.makeText(
                                                    context,
                                                    "已更新歌曲列表",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    selectedPlaylistForMenu = null
                                }
                            }
                        )
                        // 菜单项：重命名
                        ListItem(
                            headlineContent = {
                                Text(
                                    "重命名",
                                    color = if (isFavoritesPlaylist) colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.5f
                                    )
                                    else colorScheme.onSurface
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Edit,
                                    null,
                                    tint = if (isFavoritesPlaylist) colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.5f
                                    )
                                    else colorScheme.onSurface
                                )
                            },
                            modifier = Modifier.clickable(
                                enabled = !isFavoritesPlaylist,
                                onClick = {
                                    if (!isFavoritesPlaylist) {
                                        newNameInput = selectedPlaylistForMenu?.name ?: ""
                                        showRenameDialog = true
                                    }
                                }
                            )
                        )

                        // 菜单项：上移
                        if (syncedPlaylists.size > 1) {
                            val currentIndex = syncedPlaylists.indexOfFirst { it.id == selectedPlaylistForMenu?.id }
                            val canMoveUp = currentIndex > 0
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "上移",
                                        color = if (!canMoveUp) colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else colorScheme.onSurface
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        null,
                                        tint = if (!canMoveUp) colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else colorScheme.onSurface
                                    )
                                },
                                modifier = Modifier.clickable(
                                    enabled = canMoveUp,
                                    onClick = {
                                        if (canMoveUp && selectedPlaylistForMenu != null) {
                                            val index = syncedPlaylists.indexOfFirst { it.id == selectedPlaylistForMenu!!.id }
                                            if (index > 0) {
                                                val temp = syncedPlaylists[index]
                                                syncedPlaylists[index] = syncedPlaylists[index - 1]
                                                syncedPlaylists[index - 1] = temp
                                                PlaylistDataStore.savePlaylistsOrder(context, syncedPlaylists.toList())
                                            }
                                        }
                                        selectedPlaylistForMenu = null
                                    }
                                )
                            )

                            // 菜单项：下移
                            val canMoveDown = currentIndex < syncedPlaylists.lastIndex
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "下移",
                                        color = if (!canMoveDown) colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else colorScheme.onSurface
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        null,
                                        tint = if (!canMoveDown) colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else colorScheme.onSurface
                                    )
                                },
                                modifier = Modifier.clickable(
                                    enabled = canMoveDown,
                                    onClick = {
                                        if (canMoveDown && selectedPlaylistForMenu != null) {
                                            val index = syncedPlaylists.indexOfFirst { it.id == selectedPlaylistForMenu!!.id }
                                            if (index < syncedPlaylists.lastIndex) {
                                                val temp = syncedPlaylists[index]
                                                syncedPlaylists[index] = syncedPlaylists[index + 1]
                                                syncedPlaylists[index + 1] = temp
                                                PlaylistDataStore.savePlaylistsOrder(context, syncedPlaylists.toList())
                                            }
                                        }
                                        selectedPlaylistForMenu = null
                                    }
                                )
                            )
                        }

                        // 菜单项：删除
                        ListItem(
                            headlineContent = {
                                Text(
                                    "删除歌单",
                                    color = if (isFavoritesPlaylist) colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.5f
                                    )
                                    else colorScheme.error
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    tint = if (isFavoritesPlaylist) colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.5f
                                    )
                                    else colorScheme.error
                                )
                            },
                            modifier = Modifier.clickable(
                                enabled = !isFavoritesPlaylist,
                                onClick = {
                                    if (!isFavoritesPlaylist) {
                                        PlaylistDataStore.safeDelete(
                                            context,
                                            selectedPlaylistForMenu!!.id
                                        )
                                        syncedPlaylists.remove(selectedPlaylistForMenu)
                                        selectedPlaylistForMenu = null
                                    }
                                }
                            )
                        )

                    }
                }
            }

            // --- 弹窗逻辑 3：重命名对话框 ---
            if (showRenameDialog) {
                AlertDialog(
                    onDismissRequest = { showRenameDialog = false },
                    title = { Text("重命名歌单") },
                    text = {
                        OutlinedTextField(
                            value = newNameInput,
                            onValueChange = { newNameInput = it },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                selectedPlaylistForMenu?.let {
                                    if (it.id == "jianyin_favorites_playlist") {
                                        Toast.makeText(
                                            context,
                                            "收藏歌单不可重命名",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        val updated = it.copy(name = newNameInput)
                                        if (PlaylistDataStore.safeUpdate(context, updated)) {
                                            val idx =
                                                syncedPlaylists.indexOfFirst { p -> p.id == it.id }
                                            if (idx != -1) syncedPlaylists[idx] = updated
                                        }
                                    }
                                }
                                showRenameDialog = false
                                selectedPlaylistForMenu = null
                            }
                        ) { Text("保存") }
                    }
                )
            }

            // 歌单详情页
            AnimatedContent(
                targetState = activePlaylist,
                transitionSpec = {
                    // 进入动画：从中心缩放并淡入
                    scaleIn(initialScale = 0.1f) + fadeIn() with
                            // 退出动画：缩放到中心并淡出
                            scaleOut(targetScale = 0.1f) + fadeOut()
                }
            ) {
                it?.let { playlist ->
                    // 检查本地歌单目录是否存在（仅对旧版本地歌单，非用户创建的歌单）
                    if (playlist.id.startsWith("local_") && !playlist.isLocalPlaylist) {
                        LaunchedEffect(Unit) {
                            // 尝试从歌单中获取文件夹路径
                            if (playlist.songs.isNotEmpty() && playlist.songs[0].url.isNotEmpty()) {
                                val firstSongPath = playlist.songs[0].url
                                val folderPath = File(firstSongPath).parent
                                if (folderPath != null) {
                                    val folder = File(folderPath)
                                    if (!folder.exists() || !folder.isDirectory) {
                                        // 文件夹不存在，删除歌单并退出
                                        PlaylistDataStore.delete(context, playlist.id)
                                        syncedPlaylists.clear()
                                        syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                        activePlaylist = null
                                        Toast.makeText(
                                            context,
                                            "文件夹已被改名或移动，歌单已删除",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    // 无法获取文件夹路径，删除歌单并退出
                                    PlaylistDataStore.delete(context, playlist.id)
                                    syncedPlaylists.clear()
                                    syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                    activePlaylist = null
                                    Toast.makeText(
                                        context,
                                        "无法获取文件夹路径，歌单已删除",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                // 歌单为空，删除歌单并退出
                                PlaylistDataStore.delete(context, playlist.id)
                                syncedPlaylists.clear()
                                syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                activePlaylist = null
                                Toast.makeText(context, "歌单为空，已删除", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    var isLoadingSongs by remember { mutableStateOf(false) }
                    var selectedSongs by remember { mutableStateOf<Set<Int>>(emptySet()) }
                    var isSelectionMode by remember { mutableStateOf(false) }
                    var firstSelectedIndex by remember { mutableStateOf<Int?>(null) }
                    var lastSelectedIndex by remember { mutableStateOf<Int?>(null) }

                    // 排序和搜索功能状态
                    var showSortMenu by remember { mutableStateOf(false) }
                    var showSearchBar by remember { mutableStateOf(false) }
                    var searchQuery by remember { mutableStateOf("") }
                    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
                    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
                    var createPlaylistName by remember { mutableStateOf("") }
                    var showSongMenu by remember { mutableStateOf(false) }

                    // 拖拽排序状态
                    var draggedSongIndex by remember { mutableStateOf(-1) }
                    var originalDraggedIndex by remember { mutableStateOf(-1) }
                    var accumulatedForThreshold by remember { mutableStateOf(0f) }
                    val itemHeight = 72.dp
                    
                    // 本地临时列表，用于拖拽时的UI显示
                    var tempSongs by remember { mutableStateOf<List<Song>?>(null) }
                    
                    // 初始化临时列表
                    LaunchedEffect(isSelectionMode) {
                        if (isSelectionMode) {
                            tempSongs = playlist.songs.toList()
                        } else {
                            tempSongs = null
                        }
                    }

                    // 从 SharedPreferences 加载排序设置
                    var sortBy by remember(playlist.id) {
                        val prefs = context.getSharedPreferences(
                            "playlist_sort_prefs",
                            android.content.Context.MODE_PRIVATE
                        )
                        mutableStateOf(
                            prefs.getString("sort_by_${playlist.id}", "default") ?: "default"
                        )
                    }
                    var sortOrder by remember(playlist.id) {
                        val prefs = context.getSharedPreferences(
                            "playlist_sort_prefs",
                            android.content.Context.MODE_PRIVATE
                        )
                        mutableStateOf(prefs.getBoolean("sort_order_${playlist.id}", true))
                    }

                    // 处理排序和搜索后的歌曲列表
                    val filteredAndSortedSongs by remember {
                        derivedStateOf {
                            var result = if (isSelectionMode && tempSongs != null) tempSongs!! else playlist.songs

                            // 应用搜索过滤，只匹配首字母
                            if (searchQuery.isNotBlank()) {
                                result = result.filter {
                                    it.name.startsWith(searchQuery, ignoreCase = true) ||
                                            it.artist.startsWith(searchQuery, ignoreCase = true)
                                }
                            }

                            // 应用排序
                            result = when (sortBy) {
                                "name" -> result.sortedBy { if (sortOrder) it.name else it.name.reversed() }
                                "artist" -> result.sortedBy { if (sortOrder) it.artist else it.artist.reversed() }
                                "default" -> result // 保持当前顺序（已应用搜索过滤）
                                else -> result
                            }

                            result
                        }
                    }

                    // 保存排序设置到 SharedPreferences
                    fun saveSortSettings() {
                        val prefs = context.getSharedPreferences(
                            "playlist_sort_prefs",
                            android.content.Context.MODE_PRIVATE
                        )
                        prefs.edit()
                            .putString("sort_by_${playlist.id}", sortBy)
                            .putBoolean("sort_order_${playlist.id}", sortOrder)
                            .apply()

                        // 更新歌单封面图为排序后的第一首歌曲的封面
                        if (filteredAndSortedSongs.isNotEmpty()) {
                            val newCoverPic = filteredAndSortedSongs[0].pic
                            if (newCoverPic != playlist.coverPic) {
                                val updatedPlaylist = playlist.copy(
                                    coverPic = newCoverPic
                                )
                                PlaylistDataStore.update(context, updatedPlaylist)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize().background(colorScheme.background)
                    ) {
                        // 处理返回键，优先关闭播放器
                        BackHandler {
                            if (isSelectionMode) {
                                // 退出选择模式时保存更改
                                if (tempSongs != null) {
                                    val updatedPlaylist = playlist.copy(
                                        songs = tempSongs!!.toMutableList(),
                                        coverPic = if (tempSongs!!.isNotEmpty()) tempSongs!![0].pic else ""
                                    )
                                    PlaylistDataStore.update(context, updatedPlaylist)
                                    activePlaylist = updatedPlaylist
                                    syncedPlaylists.clear()
                                    syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                }
                                // 退出选择模式
                                isSelectionMode = false
                                selectedSongs = emptySet()
                                firstSelectedIndex = null
                                lastSelectedIndex = null
                            } else if (vm.isPlayerSheetVisible.value) {
                                vm.isPlayerSheetVisible.value = false
                            } else {
                                activePlaylist = null
                            }
                        }

                        CenterAlignedTopAppBar(
                            title = {
                                if (isSelectionMode) {
                                    Text("已选择 ${selectedSongs.size} 首歌曲")
                                } else {
                                    Text(
                                        playlist.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(0.66f)
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (isSelectionMode) {
                                        // 退出选择模式时保存更改
                                        if (tempSongs != null) {
                                            val updatedPlaylist = playlist.copy(
                                                songs = tempSongs!!.toMutableList(),
                                                coverPic = if (tempSongs!!.isNotEmpty()) tempSongs!![0].pic else ""
                                            )
                                            PlaylistDataStore.update(context, updatedPlaylist)
                                            activePlaylist = updatedPlaylist
                                            syncedPlaylists.clear()
                                            syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                        }
                                        // 退出选择模式
                                        isSelectionMode = false
                                        selectedSongs = emptySet()
                                        firstSelectedIndex = null
                                        lastSelectedIndex = null
                                    } else if (vm.isPlayerSheetVisible.value) {
                                        vm.isPlayerSheetVisible.value = false
                                    } else {
                                        activePlaylist = null
                                    }
                                }) {
                                    Icon(Icons.Default.ArrowBack, null)
                                }
                            },
                            actions = {
                                if (isSelectionMode && selectedSongs.isNotEmpty()) {
                                    // 批量下载按钮（旧版本地歌单不需要下载）
                                    val canDownload = !(playlist.id.startsWith("local_") && !playlist.isLocalPlaylist)
                                    if (canDownload) {
                                        IconButton(onClick = {
                                            val songsToDownload =
                                                selectedSongs.mapNotNull { index ->
                                                    filteredAndSortedSongs.getOrNull(index)
                                                }

                                            scope.launch {
                                                val customUri =
                                                    if (DownloadSettingsStore.isUsingCustomPath(
                                                            context
                                                        )
                                                    ) DownloadSettingsStore.getCustomUri(context) else null
                                                DownloadStateManager.startDownload(songsToDownload.size)

                                                DownloadManager.downloadSongs(
                                                    context,
                                                    songsToDownload,
                                                    customUri
                                                ) { index, total, songName, progress ->
                                                    DownloadStateManager.updateCurrentSong(
                                                        index,
                                                        songName
                                                    )
                                                    DownloadStateManager.updateProgress(progress)
                                                }
                                                    .onSuccess { results ->
                                                        val successCount =
                                                            results.count { it.startsWith("下载完成") }
                                                        val failCount = results.size - successCount
                                                        Toast.makeText(
                                                            context,
                                                            "下载完成：成功 $successCount 首，失败 $failCount 首",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        DownloadStateManager.downloadComplete()
                                                    }
                                                    .onFailure { e ->
                                                        Toast.makeText(
                                                            context,
                                                            "下载失败：${e.message}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        DownloadStateManager.downloadFailed(
                                                            e.message ?: "未知错误"
                                                        )
                                                    }

                                                isSelectionMode = false
                                                selectedSongs = emptySet()
                                                firstSelectedIndex = null
                                                lastSelectedIndex = null
                                            }
                                        }) {
                                            Icon(
                                                Icons.Default.Download,
                                                null,
                                                tint = colorScheme.primary
                                            )
                                        }
                                    }

                                    IconButton(onClick = {
                                        showAddToPlaylistDialog = true
                                    }) {
                                        Icon(
                                            Icons.Default.PlaylistAdd,
                                            null,
                                            tint = colorScheme.primary
                                        )
                                    }

                                    val isOldLocalPlaylist = playlist.id.startsWith("local_") && !playlist.isLocalPlaylist
                                    val showDeleteOptions = isOldLocalPlaylist

                                    IconButton(onClick = {
                                        // 删除选中的歌曲（不删除歌曲文件）
                                        val songsToRemove = selectedSongs.mapNotNull { index ->
                                            filteredAndSortedSongs.getOrNull(index)
                                        }

                                        if (songsToRemove.isNotEmpty()) {
                                            var removedCount = 0
                                            songsToRemove.forEach { song ->
                                                val removed =
                                                    PlaylistDataStore.removeSongFromPlaylist(
                                                        context,
                                                        playlist.id,
                                                        song
                                                    )
                                                if (removed) removedCount++
                                            }

                                            val updatedPlaylist = playlist.copy(
                                                songs = playlist.songs.filter { song ->
                                                    !songsToRemove.contains(song)
                                                }
                                            )
                                            val idx = syncedPlaylists.indexOfFirst { it.id == playlist.id }
                                            if (idx != -1) syncedPlaylists[idx] = updatedPlaylist
                                            activePlaylist = updatedPlaylist

                                            isSelectionMode = false
                                            selectedSongs = emptySet()
                                            firstSelectedIndex = null
                                            lastSelectedIndex = null

                                            Toast.makeText(
                                                context,
                                                "已移除 $removedCount 首歌曲",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, null, tint = colorScheme.error)
                                    }
                                } else {
                                    // 非选择模式下显示排序和搜索图标
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.Default.Sort, null, tint = colorScheme.primary)
                                    }
                                    IconButton(onClick = { showSearchBar = !showSearchBar }) {
                                        Icon(Icons.Default.Search, null, tint = colorScheme.primary)
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = colorScheme.background,
                                titleContentColor = colorScheme.onBackground,
                                navigationIconContentColor = colorScheme.primary
                            )
                        )

                        // 搜索栏
                        AnimatedVisibility(
                            visible = showSearchBar,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                placeholder = { Text("搜索歌曲或歌手") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, null)
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = colorScheme.surfaceColorAtElevation(2.dp),
                                    unfocusedContainerColor = colorScheme.surfaceColorAtElevation(2.dp),
                                    disabledContainerColor = colorScheme.surfaceColorAtElevation(2.dp)
                                )
                            )
                        }

                        // 排序菜单
                        if (showSortMenu) {
                            ModalBottomSheet(
                                onDismissRequest = { showSortMenu = false },
                                containerColor = colorScheme.surface
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text(
                                        "排序方式",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    // 默认排序
                                    ListItem(
                                        headlineContent = { Text("默认排序") },
                                        leadingContent = {
                                            RadioButton(
                                                selected = sortBy == "default",
                                                onClick = {
                                                    sortBy = "default"
                                                    val prefs = context.getSharedPreferences(
                                                        "playlist_sort_prefs",
                                                        android.content.Context.MODE_PRIVATE
                                                    )
                                                    prefs.edit()
                                                        .remove("sort_by_${playlist.id}")
                                                        .remove("sort_order_${playlist.id}")
                                                        .apply()
                                                }
                                            )
                                        }
                                    )

                                    // 按歌曲名排序
                                    ListItem(
                                        headlineContent = { Text("按歌曲名") },
                                        leadingContent = {
                                            RadioButton(
                                                selected = sortBy == "name",
                                                onClick = {
                                                    sortBy = "name"
                                                    saveSortSettings()
                                                }
                                            )
                                        },
                                        trailingContent = {
                                            if (sortBy == "name") {
                                                IconButton(onClick = {
                                                    sortOrder = !sortOrder
                                                    saveSortSettings()
                                                }) {
                                                    Icon(
                                                        if (sortOrder) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                        null
                                                    )
                                                }
                                            }
                                        }
                                    )

                                    // 按歌手排序
                                    ListItem(
                                        headlineContent = { Text("按歌手") },
                                        leadingContent = {
                                            RadioButton(
                                                selected = sortBy == "artist",
                                                onClick = {
                                                    sortBy = "artist"
                                                    saveSortSettings()
                                                }
                                            )
                                        },
                                        trailingContent = {
                                            if (sortBy == "artist") {
                                                IconButton(onClick = {
                                                    sortOrder = !sortOrder
                                                    saveSortSettings()
                                                }) {
                                                    Icon(
                                                        if (sortOrder) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                        null
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (showAddToPlaylistDialog) {
                            val otherPlaylists = syncedPlaylists.filter {
                                it.id != playlist.id
                            }
                            ModalBottomSheet(
                                onDismissRequest = { showAddToPlaylistDialog = false },
                                containerColor = colorScheme.surface
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        "添加到歌单",
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )

                                    if (otherPlaylists.isEmpty()) {
                                        Text(
                                            "没有其他歌单可添加",
                                            color = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 32.dp)
                                        )
                                    } else {
                                        otherPlaylists.forEach { targetPlaylist ->
                                            ListItem(
                                                headlineContent = { Text(targetPlaylist.name) },
                                                supportingContent = {
                                                    Text("${targetPlaylist.songs.size} 首歌曲")
                                                },
                                                leadingContent = {
                                                    val cover = targetPlaylist.coverPic.ifBlank { null }
                                                    if (cover != null) {
                                                        AsyncImage(
                                                            model = cover,
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(colorScheme.surfaceVariant),
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
                                                                .background(colorScheme.surfaceVariant),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.clickable {
                                                    val songsToAdd = selectedSongs.mapNotNull { index ->
                                                        filteredAndSortedSongs.getOrNull(index)
                                                    }
                                                    var addedCount = 0
                                                    songsToAdd.forEach { song ->
                                                        if (PlaylistDataStore.addSongToPlaylist(
                                                                context,
                                                                targetPlaylist.id,
                                                                song
                                                            )
                                                        ) {
                                                            addedCount++
                                                        }
                                                    }
                                                    showAddToPlaylistDialog = false
                                                    isSelectionMode = false
                                                    selectedSongs = emptySet()
                                                    firstSelectedIndex = null
                                                    lastSelectedIndex = null
                                                    syncedPlaylists.clear()
                                                    syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                                    Toast.makeText(
                                                        context,
                                                        "已添加 $addedCount 首歌曲到 ${targetPlaylist.name}",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            )
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    ListItem(
                                        headlineContent = { Text("添加到当前播放列表", color = colorScheme.primary) },
                                        leadingContent = {
                                            Icon(Icons.Default.QueuePlayNext, null, tint = colorScheme.primary)
                                        },
                                        modifier = Modifier.clickable {
                                            val songsToAdd = selectedSongs.mapNotNull { index ->
                                                filteredAndSortedSongs.getOrNull(index)
                                            }
                                            songsToAdd.forEach { song ->
                                                vm.addNextToQueue(song)
                                            }
                                            showAddToPlaylistDialog = false
                                            isSelectionMode = false
                                            selectedSongs = emptySet()
                                            firstSelectedIndex = null
                                            lastSelectedIndex = null
                                            Toast.makeText(
                                                context,
                                                "已添加 ${songsToAdd.size} 首歌曲到当前播放列表",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    ListItem(
                                        headlineContent = { Text("创建新歌单", color = colorScheme.primary) },
                                        leadingContent = {
                                            Icon(Icons.Default.Add, null, tint = colorScheme.primary)
                                        },
                                        modifier = Modifier.clickable {
                                            showAddToPlaylistDialog = false
                                            createPlaylistName = ""
                                            showCreatePlaylistDialog = true
                                        }
                                    )
                                    Spacer(Modifier.height(32.dp))
                                }
                            }
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
                                                val songsToAdd = selectedSongs.mapNotNull { index ->
                                                    filteredAndSortedSongs.getOrNull(index)
                                                }
                                                var addedCount = 0
                                                songsToAdd.forEach { song ->
                                                    if (PlaylistDataStore.addSongToPlaylist(
                                                            context,
                                                            newPlaylist.id,
                                                            song
                                                        )
                                                    ) {
                                                        addedCount++
                                                    }
                                                }
                                                showCreatePlaylistDialog = false
                                                isSelectionMode = false
                                                selectedSongs = emptySet()
                                                firstSelectedIndex = null
                                                lastSelectedIndex = null
                                                syncedPlaylists.clear()
                                                syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                                Toast.makeText(
                                                    context,
                                                    "已创建歌单并添加 $addedCount 首歌曲",
                                                    Toast.LENGTH_SHORT
                                                ).show()
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

                        if (isLoadingSongs) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colorScheme.primary)
                            }
                        } else {
                            val playlistDetailListState = rememberLazyListState()

                            // 计算当前播放歌曲在歌单中的索引
                            val currentPlayingIndex = vm.currentSong.value?.let { currentSong ->
                                filteredAndSortedSongs.indexOfFirst { song ->
                                    (song.id.isNotBlank() && song.id == currentSong.id) ||
                                    (song.url.isNotBlank() && song.url == currentSong.url)
                                }.takeIf { it >= 0 }
                            }

                            // 自动滚动到当前播放歌曲
                            LaunchedEffect(currentPlayingIndex) {
                                currentPlayingIndex?.let { index ->
                                    if (index >= 0) {
                                        kotlinx.coroutines.delay(100)
                                        playlistDetailListState.animateScrollToItem(index.coerceIn(0, (filteredAndSortedSongs.size - 1).coerceAtLeast(0)))
                                    }
                                }
                            }

                            LazyColumn(modifier = Modifier.fillMaxSize(), state = playlistDetailListState) {
                                if (filteredAndSortedSongs.isNotEmpty()) {
                                    item {
                                        val hazeState = remember { HazeState() }
                                        Box(Modifier.fillMaxWidth()) {
                                            AsyncImage(
                                                model = filteredAndSortedSongs[0].pic,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(190.dp)
                                                    .hazeSource(hazeState),
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(id = getRandomPlaceholderId())
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(50.dp)
                                                    .hazeEffect(
                                                        hazeState,
                                                        HazeStyle(
                                                            blurRadius = 8.dp,
                                                            tint = HazeTint(Color.Black.copy(alpha = 0.3f))
                                                        )
                                                    )
                                                    .align(Alignment.BottomStart)
                                            ) {
                                                Text(
                                                    playlist.name,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .align(Alignment.CenterStart)
                                                        .padding(start = 16.dp)
                                                        .fillMaxWidth(0.66f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val firstSong = filteredAndSortedSongs[0]
                                                        if (firstSong.source == SongSource.NETEASE) {
                                                            vm.playNeteaseSong(firstSong, filteredAndSortedSongs)
                                                        } else {
                                                            vm.playSong(firstSong, filteredAndSortedSongs)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(end = 16.dp, bottom = 8.dp)
                                                        .size(48.dp),
                                                    colors = IconButtonDefaults.iconButtonColors(
                                                        containerColor = colorScheme.primary,
                                                        contentColor = Color.White
                                                    )
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(24.dp))
                                                }
                                            }
                                        }
                                    }
                                    item { Spacer(Modifier.height(16.dp)) }
                                }

                                itemsIndexed(filteredAndSortedSongs) { index, song ->
                                    var isDownloading by remember { mutableStateOf(false) }
                                    var showSongMenu by remember { mutableStateOf(false) }

                                    Column {
                                        val isSelected = selectedSongs.contains(index)
                                        val isDragging = draggedSongIndex != -1 && selectedSongs.contains(index)
                                        val isCurrentlyPlaying = remember(vm.currentSong.value) {
                                            vm.currentSong.value?.let { currentSong ->
                                                song.id.isNotBlank() && song.id == currentSong.id ||
                                                song.url.isNotBlank() && song.url == currentSong.url
                                            } ?: false
                                        }
                                        val playingLineHeight by animateDpAsState(
                                            targetValue = if (isCurrentlyPlaying) 24.dp else 0.dp,
                                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                                            label = "playingLineHeight"
                                        )
                                        
                                        // 微小的弹簧缩放动画
                                        val scale by animateFloatAsState(
                                            targetValue = if (isDragging) 1.02f else 1f,
                                            animationSpec = spring(
                                                dampingRatio = 0.6f,
                                                stiffness = 400f
                                            ),
                                            label = "dragScale"
                                        )

                                        var isLongClick by remember { mutableStateOf(false) }

                                        val density = LocalDensity.current

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (isSelected)
                                                        colorScheme.primary.copy(alpha = 0.1f)
                                                    else if (isDragging)
                                                        colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                    else if (isCurrentlyPlaying)
                                                        colorScheme.primary.copy(alpha = 0.05f)
                                                    else
                                                        colorScheme.background
                                                )
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                }
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onTap = {
                                                            if (isSelectionMode) {
                                                                val newSelected = if (isSelected) {
                                                                    selectedSongs - index
                                                                } else {
                                                                    selectedSongs + index
                                                                }
                                                                selectedSongs = newSelected

                                                                if (newSelected.isNotEmpty()) {
                                                                    firstSelectedIndex = newSelected.minOrNull()
                                                                    lastSelectedIndex = newSelected.maxOrNull()
                                                                } else {
                                                                    firstSelectedIndex = null
                                                                    lastSelectedIndex = null
                                                                }
                                                            } else {
                                                                if (song.source == SongSource.NETEASE) {
                                                                    vm.playNeteaseSong(song, filteredAndSortedSongs)
                                                                } else {
                                                                    vm.playSong(song, filteredAndSortedSongs)
                                                                }
                                                            }
                                                        },
                                                        onLongPress = {
                                                            VibrationManager.heavyTap(context)
                                                            if (!isSelectionMode) {
                                                                isSelectionMode = true
                                                                selectedSongs = setOf(index)
                                                                firstSelectedIndex = index
                                                                lastSelectedIndex = index
                                                            } else if (selectedSongs.isNotEmpty() && firstSelectedIndex != null) {
                                                                val start = min(firstSelectedIndex!!, index)
                                                                val end = max(firstSelectedIndex!!, index)
                                                                selectedSongs = (start..end).toSet()
                                                                lastSelectedIndex = end
                                                            }
                                                        }
                                                    )
                                                }
                                                .padding(horizontal = 20.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 当前播放主题色竖线标识
                                            if (isCurrentlyPlaying) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(3.dp)
                                                        .height(playingLineHeight)
                                                        .background(
                                                            colorScheme.primary,
                                                            shape = RoundedCornerShape(2.dp)
                                                        )
                                                )
                                                Spacer(Modifier.width(12.dp))
                                            }
                                            // 选择模式下显示选择框
                                            if (isSelectionMode) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (isSelected)
                                                                colorScheme.primary
                                                            else
                                                                colorScheme.surfaceVariant
                                                        )
                                                        .padding(2.dp)
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = "已选择",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.width(12.dp))
                                            }

                                            AsyncImage(
                                                model = song.pic,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(colorScheme.surfaceVariant),
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(id = getRandomPlaceholderId())
                                            )
                                            Column(
                                                Modifier.padding(start = if (isSelectionMode) 12.dp else 16.dp)
                                                    .weight(1f)
                                            ) {
                                                Text(
                                                    song.name,
                                                    color = colorScheme.onBackground,
                                                    fontSize = 16.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        song.artist,
                                                        color = colorScheme.onSurfaceVariant,
                                                        fontSize = 13.sp,
                                                        maxLines = 1
                                                    )
                                                    // 分p视频标识
                                                    if (song.isPartOfMultiPage) {
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Badge(
                                                            containerColor = colorScheme.primaryContainer,
                                                            contentColor = colorScheme.onPrimaryContainer
                                                        ) {
                                                            Text("P${song.pageIndex}/${song.pageCount}", fontSize = 10.sp)
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            // 选择模式下显示拖拽器（仅网络歌单和用户创建歌单）
                                            if (isSelectionMode && (!playlist.id.startsWith("local_") || playlist.isLocalPlaylist)) {
                                                Icon(
                                                    imageVector = Icons.Default.DragHandle,
                                                    contentDescription = "拖拽排序",
                                                    tint = colorScheme.onSurfaceVariant,
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .pointerInput(Unit) {
                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = { offset ->
                                                                    VibrationManager.heavyTap(context)
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
                                                                    // 设置更高的触发阈值，半个item高度才触发
                                                                    val threshold = itemHeightPx * 0.5f
                                                                    
                                                                    val movedItems = (accumulatedForThreshold / threshold).toInt()
                                                                    
                                                                    if (movedItems != 0) {
                                                                        // 获取要移动的歌曲范围
                                                                        val minSelected = selectedSongs.minOrNull() ?: index
                                                                        val maxSelected = selectedSongs.maxOrNull() ?: index
                                                                        val rangeSize = maxSelected - minSelected + 1
                                                                        
                                                                        // 计算目标起始位置
                                                                        val targetStartIndex = (minSelected + movedItems)
                                                                            .coerceIn(0, (tempSongs?.size ?: 0) - rangeSize)
                                                                        
                                                                        if (targetStartIndex != minSelected) {
                                                                            VibrationManager.lightTap(context)
                                                                            
                                                                            // 获取要移动的歌曲
                                                                            val songsToMove = selectedSongs.sorted().map { tempSongs!![it] }
                                                                            
                                                                            // 从原位置移除
                                                                            val updatedSongs = tempSongs!!.toMutableList()
                                                                            for (i in selectedSongs.sortedDescending()) {
                                                                                updatedSongs.removeAt(i)
                                                                            }
                                                                            
                                                                            // 插入到新位置
                                                                            for ((i, s) in songsToMove.withIndex()) {
                                                                                updatedSongs.add(targetStartIndex + i, s)
                                                                            }
                                                                            
                                                                            // 更新临时列表（不保存到歌单）
                                                                            tempSongs = updatedSongs
                                                                            
                                                                            // 更新选中的索引
                                                                            selectedSongs = (targetStartIndex until targetStartIndex + rangeSize).toSet()
                                                                            firstSelectedIndex = targetStartIndex
                                                                            lastSelectedIndex = targetStartIndex + rangeSize - 1
                                                                            
                                                                            // 更新拖拽的起始位置
                                                                            draggedSongIndex = targetStartIndex + (index - minSelected)
                                                                            
                                                                            // 重置阈值计数
                                                                            accumulatedForThreshold = 0f
                                                                        }
                                                                    }
                                                                }
                                                            )
                                                        }
                                                )
                                                Spacer(Modifier.width(8.dp))
                                            }

                                            if (!isSelectionMode) {
                                                // 播放按钮
                                                IconButton(
                                                    onClick = {
                                                        if (song.source == SongSource.NETEASE) {
                                                            vm.playNeteaseSong(song, filteredAndSortedSongs)
                                                        } else {
                                                            vm.playSong(song, filteredAndSortedSongs)
                                                        }
                                                    },
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.ic_play),
                                                        contentDescription = "播放",
                                                        tint = colorScheme.primary,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }

                                            // 歌曲操作菜单弹窗
                                            if (showSongMenu) {
                                                AlertDialog(
                                                    onDismissRequest = { showSongMenu = false },
                                                    title = {
                                                        Text(
                                                            song.name,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    },
                                                    text = {
                                                        if (playlist.id.startsWith("local_") && !playlist.isLocalPlaylist) {
                                                            Text("选择删除方式")
                                                        } else {
                                                            Text("选择操作")
                                                        }
                                                    },
                                                    containerColor = MaterialTheme.colorScheme.surface,
                                                    confirmButton = {
                                                        if (!song.isLocal) {
                                                            TextButton(
                                                                onClick = {
                                                                    showSongMenu = false
                                                                    isDownloading = true
                                                                    DownloadStateManager.startDownload(
                                                                        1
                                                                    )
                                                                    DownloadStateManager.updateCurrentSong(
                                                                        0,
                                                                        song.name
                                                                    )
                                                                    scope.launch {
                                                                        val customUri =
                                                                            if (DownloadSettingsStore.isUsingCustomPath(
                                                                                    context
                                                                                )
                                                                            ) DownloadSettingsStore.getCustomUri(
                                                                                context
                                                                            ) else null
                                                                        DownloadManager.downloadSong(
                                                                            context,
                                                                            song,
                                                                            customUri
                                                                        ) {
                                                                            DownloadStateManager.updateProgress(
                                                                                it
                                                                            )
                                                                        }
                                                                            .onSuccess {
                                                                                Toast.makeText(
                                                                                    context,
                                                                                    "下载完成: ${song.name}",
                                                                                    Toast.LENGTH_LONG
                                                                                ).show()
                                                                                DownloadStateManager.downloadComplete()
                                                                            }
                                                                            .onFailure { e ->
                                                                                Toast.makeText(
                                                                                    context,
                                                                                    "下载失败: ${e.message}",
                                                                                    Toast.LENGTH_SHORT
                                                                                ).show()
                                                                                DownloadStateManager.downloadFailed(
                                                                                    e.message
                                                                                        ?: "未知错误"
                                                                                )
                                                                            }
                                                                        isDownloading = false
                                                                    }
                                                                }
                                                            ) {
                                                                Text("下载")
                                                            }
                                                        } else if (playlist.id.startsWith("local_") && !playlist.isLocalPlaylist) {
                                                            // 本地歌单：删除文件
                                                            TextButton(
                                                                onClick = {
                                                                    showSongMenu = false
                                                                    // 从歌单移除并删除文件
                                                                    val removed =
                                                                        PlaylistDataStore.removeSongFromPlaylist(
                                                                            context,
                                                                            playlist.id,
                                                                            song
                                                                        )
                                                                    if (removed) {
                                                                        // 删除本地文件
                                                                        try {
                                                                            val file =
                                                                                File(song.url)
                                                                            if (file.exists()) {
                                                                                file.delete()
                                                                            }
                                                                        } catch (e: Exception) {
                                                                            e.printStackTrace()
                                                                        }

                                                                        // 更新本地歌单数据
                                                                        val updatedPlaylist =
                                                                            playlist.copy(
                                                                                songs = playlist.songs.filterNot {
                                                                                    (it.id.isNotBlank() && it.id == song.id) ||
                                                                                            (it.url.isNotBlank() && it.url == song.url)
                                                                                }
                                                                            )
                                                                        activePlaylist =
                                                                            updatedPlaylist
                                                                        // 刷新歌单列表
                                                                        syncedPlaylists.clear()
                                                                        syncedPlaylists.addAll(
                                                                            PlaylistDataStore.getAll(
                                                                                context
                                                                            )
                                                                        )
                                                                        Toast.makeText(
                                                                            context,
                                                                            "已移除并删除文件: ${song.name}",
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    }
                                                                }
                                                            ) {
                                                                Text("删除文件")
                                                            }
                                                        }
                                                    },
                                                    dismissButton = {
                                                        TextButton(
                                                            onClick = {
                                                                showSongMenu = false
                                                                // 从歌单移除歌曲
                                                                val removed =
                                                                    PlaylistDataStore.removeSongFromPlaylist(
                                                                        context,
                                                                        playlist.id,
                                                                        song
                                                                    )
                                                                if (removed) {
                                                                    // 更新本地歌单数据
                                                                    val updatedPlaylist =
                                                                        playlist.copy(
                                                                            songs = playlist.songs.filterNot {
                                                                                (it.id.isNotBlank() && it.id == song.id) ||
                                                                                        (it.url.isNotBlank() && it.url == song.url)
                                                                            }
                                                                        )
                                                                    activePlaylist = updatedPlaylist
                                                                    // 刷新歌单列表
                                                                    syncedPlaylists.clear()
                                                                    syncedPlaylists.addAll(
                                                                        PlaylistDataStore.getAll(
                                                                            context
                                                                        )
                                                                    )
                                                                    Toast.makeText(
                                                                        context,
                                                                        "已从歌单移除: ${song.name}",
                                                                        Toast.LENGTH_SHORT
                                                                    ).show()
                                                                }
                                                            }
                                                        ) {
                                                            Text(
                                                                if (playlist.id.startsWith("local_") && !playlist.isLocalPlaylist) "删除列表" else "从歌单移除",
                                                                color = colorScheme.error
                                                            )
                                                        }
                                                    }
                                                )
                                            }

                                            // 下载中指示器
                                            if (isDownloading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                    color = colorScheme.primary
                                                )
                                            }
                                        }

                                        // 添加分割线，除了最后一首歌曲
                                        if (index < playlist.songs.size - 1) {
                                            Divider(
                                                modifier = Modifier
                                                    .padding(horizontal = 20.dp)
                                                    .fillMaxWidth(),
                                                color = colorScheme.surfaceVariant
                                            )
                                        }
                                    }
                                }
                                item { Spacer(Modifier.navigationBarsPadding().height(160.dp)) }
                            }
                        }
                    }
                }
            }

            // 最近播放详情页
            AnimatedVisibility(
                visible = activeRecentPlaylist != null,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
                    // 最近播放选择模式状态
                    var isRecentSelectionMode by remember { mutableStateOf(false) }
                    var selectedRecentSongs by remember { mutableStateOf(setOf<Int>()) }
                    var firstRecentSelectedIndex by remember { mutableStateOf<Int?>(null) }
                    var lastRecentSelectedIndex by remember { mutableStateOf<Int?>(null) }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // 处理返回键，优先关闭播放器
                        BackHandler {
                            if (vm.isPlayerSheetVisible.value) {
                                vm.isPlayerSheetVisible.value = false
                            } else if (isRecentSelectionMode) {
                                isRecentSelectionMode = false
                                selectedRecentSongs = emptySet()
                                firstRecentSelectedIndex = null
                                lastRecentSelectedIndex = null
                            } else {
                                activeRecentPlaylist = null
                            }
                        }

                        CenterAlignedTopAppBar(
                            title = {
                                if (isRecentSelectionMode) {
                                    Text(
                                        "已选择 ${selectedRecentSongs.size} 首",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text("最近播放", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (vm.isPlayerSheetVisible.value) {
                                        vm.isPlayerSheetVisible.value = false
                                    } else if (isRecentSelectionMode) {
                                        isRecentSelectionMode = false
                                        selectedRecentSongs = emptySet()
                                        firstRecentSelectedIndex = null
                                        lastRecentSelectedIndex = null
                                    } else {
                                        activeRecentPlaylist = null
                                    }
                                }) {
                                    Icon(Icons.Default.ArrowBack, null)
                                }
                            },
                            actions = {
                                if (isRecentSelectionMode && selectedRecentSongs.isNotEmpty()) {
                                    IconButton(onClick = {
                                        // 批量下载选中的歌曲
                                        val songsToDownload =
                                            selectedRecentSongs.mapNotNull { idx ->
                                                activeRecentPlaylist?.getOrNull(idx)
                                            }

                                        if (songsToDownload.isNotEmpty()) {
                                            scope.launch {
                                                val customUri =
                                                    if (DownloadSettingsStore.isUsingCustomPath(
                                                            context
                                                        )
                                                    ) DownloadSettingsStore.getCustomUri(context) else null
                                                DownloadStateManager.startDownload(songsToDownload.size)

                                                DownloadManager.downloadSongs(
                                                    context,
                                                    songsToDownload,
                                                    customUri
                                                ) { index, total, songName, progress ->
                                                    DownloadStateManager.updateCurrentSong(
                                                        index,
                                                        songName
                                                    )
                                                    DownloadStateManager.updateProgress(progress)
                                                }
                                                    .onSuccess { results ->
                                                        val successCount =
                                                            results.count { it.startsWith("下载完成") }
                                                        val failCount = results.size - successCount
                                                        Toast.makeText(
                                                            context,
                                                            "下载完成：成功 $successCount 首，失败 $failCount 首",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        DownloadStateManager.downloadComplete()
                                                    }
                                                    .onFailure { e ->
                                                        Toast.makeText(
                                                            context,
                                                            "下载失败：${e.message}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        DownloadStateManager.downloadFailed(
                                                            e.message ?: "未知错误"
                                                        )
                                                    }
                                            }
                                        }
                                    }) {
                                        Icon(
                                            Icons.Default.Download,
                                            null,
                                            tint = colorScheme.primary
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = colorScheme.background,
                                titleContentColor = colorScheme.onBackground,
                                navigationIconContentColor = colorScheme.primary
                            )
                        )

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            // 歌曲列表
                            itemsIndexed(activeRecentPlaylist ?: emptyList()) { index, song ->
                                var isDownloading by remember { mutableStateOf(false) }
                                var showSongMenu by remember { mutableStateOf(false) }
                                val isSelected = selectedRecentSongs.contains(index)

                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isSelected)
                                                    colorScheme.primary.copy(alpha = 0.1f)
                                                else
                                                    colorScheme.background
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    if (isRecentSelectionMode) {
                                                        // 普通点击：只进行单选
                                                        val newSelected = if (isSelected) {
                                                            // 取消选择
                                                            selectedRecentSongs - index
                                                        } else {
                                                            // 单选
                                                            selectedRecentSongs + index
                                                        }
                                                        selectedRecentSongs = newSelected

                                                        // 更新首尾选择索引
                                                        if (newSelected.isNotEmpty()) {
                                                            firstRecentSelectedIndex =
                                                                newSelected.minOrNull()
                                                            lastRecentSelectedIndex =
                                                                newSelected.maxOrNull()
                                                        } else {
                                                            firstRecentSelectedIndex = null
                                                            lastRecentSelectedIndex = null
                                                        }
                                                    } else {
                                                        if (song.source == SongSource.NETEASE) {
                                                            vm.playNeteaseSong(song, activeRecentPlaylist ?: emptyList())
                                                        } else {
                                                            vm.playSong(song, activeRecentPlaylist ?: emptyList())
                                                        }
                                                    }
                                                },
                                                onLongClick = {
                                                    if (!isRecentSelectionMode) {
                                                        // 进入选择模式并选择当前歌曲
                                                        isRecentSelectionMode = true
                                                        selectedRecentSongs = setOf(index)
                                                        firstRecentSelectedIndex = index
                                                        lastRecentSelectedIndex = index
                                                    } else if (selectedRecentSongs.isNotEmpty() && firstRecentSelectedIndex != null) {
                                                        // 长按第二首：自动包含中间的歌曲
                                                        val start =
                                                            min(firstRecentSelectedIndex!!, index)
                                                        val end =
                                                            max(firstRecentSelectedIndex!!, index)
                                                        // 选择范围内的所有歌曲
                                                        selectedRecentSongs = (start..end).toSet()
                                                        lastRecentSelectedIndex = end
                                                    }
                                                }
                                            )
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 选择模式下显示选择框
                                        if (isRecentSelectionMode) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected)
                                                            colorScheme.primary
                                                        else
                                                            colorScheme.surfaceVariant
                                                    )
                                                    .padding(2.dp)
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "已选择",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                        }

                                        Box(modifier = Modifier.size(52.dp)) {
                                            AsyncImage(
                                                model = song.pic,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(colorScheme.surfaceVariant),
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(id = getRandomPlaceholderId())
                                            )
                                        }
                                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                            Text(
                                                song.name,
                                                color = colorScheme.onBackground,
                                                fontSize = 16.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    song.artist,
                                                    color = colorScheme.onSurfaceVariant,
                                                    fontSize = 13.sp,
                                                    maxLines = 1
                                                )
                                                // 分p视频标识
                                                if (song.isPartOfMultiPage) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    MultiPageTaijiBadge(
                                                        pageIndex = song.pageIndex,
                                                        pageCount = song.pageCount
                                                    )
                                                }
                                                // 试听标识
                                                if (song.isPreview) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Badge(
                                                        containerColor = colorScheme.primaryContainer,
                                                        contentColor = colorScheme.onPrimaryContainer
                                                    ) {
                                                        Text("试听", fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }
                                        // 非选择模式下显示播放按钮
                                        if (!isRecentSelectionMode) {
                                            IconButton(
                                                onClick = {
                                                    if (song.source == SongSource.NETEASE) {
                                                        vm.playNeteaseSong(song, activeRecentPlaylist ?: emptyList())
                                                    } else {
                                                        vm.playSong(song, activeRecentPlaylist ?: emptyList())
                                                    }
                                                },
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_play),
                                                    contentDescription = "播放",
                                                    tint = colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }

                                        // 歌曲操作菜单弹窗
                                        if (showSongMenu) {
                                            AlertDialog(
                                                onDismissRequest = { showSongMenu = false },
                                                title = {
                                                    Text(
                                                        song.name,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                text = { Text("选择操作") },
                                                confirmButton = {
                                                    TextButton(
                                                        onClick = {
                                                            showSongMenu = false
                                                            isDownloading = true
                                                            DownloadStateManager.startDownload(1)
                                                            DownloadStateManager.updateCurrentSong(
                                                                0,
                                                                song.name
                                                            )
                                                            scope.launch {
                                                                val customUri =
                                                                    if (DownloadSettingsStore.isUsingCustomPath(
                                                                            context
                                                                        )
                                                                    ) DownloadSettingsStore.getCustomUri(
                                                                        context
                                                                    ) else null
                                                                DownloadManager.downloadSong(
                                                                    context,
                                                                    song,
                                                                    customUri
                                                                ) {
                                                                    DownloadStateManager.updateProgress(
                                                                        it
                                                                    )
                                                                }
                                                                    .onSuccess {
                                                                        Toast.makeText(
                                                                            context,
                                                                            "下载完成: ${song.name}",
                                                                            Toast.LENGTH_LONG
                                                                        ).show()
                                                                        DownloadStateManager.downloadComplete()
                                                                    }
                                                                    .onFailure { e ->
                                                                        Toast.makeText(
                                                                            context,
                                                                            "下载失败: ${e.message}",
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                        DownloadStateManager.downloadFailed(
                                                                            e.message ?: "未知错误"
                                                                        )
                                                                    }
                                                                isDownloading = false
                                                            }
                                                        }
                                                    ) {
                                                        Text("下载")
                                                    }
                                                },
                                                dismissButton = {
                                                    Column {
                                                        TextButton(
                                                            onClick = {
                                                                showSongMenu = false
                                                                // 进入选择模式并选择当前歌曲
                                                                isRecentSelectionMode = true
                                                                selectedRecentSongs = setOf(index)
                                                                firstRecentSelectedIndex = index
                                                                lastRecentSelectedIndex = index
                                                            }
                                                        ) {
                                                            Text("批量选择")
                                                        }
                                                        TextButton(
                                                            onClick = {
                                                                showSongMenu = false
                                                                // 从最近播放移除
                                                                vm.historyList.remove(song)
                                                                Toast.makeText(
                                                                    context,
                                                                    "已从最近播放移除: ${song.name}",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        ) {
                                                            Text(
                                                                "从最近播放移除",
                                                                color = colorScheme.error
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }

                                        // 下载中指示器
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                                color = colorScheme.primary
                                            )
                                        }
                                    }

                                    // 添加分割线，除了最后一首歌曲
                                    if (index < (activeRecentPlaylist?.size ?: 0) - 1) {
                                        Divider(
                                            modifier = Modifier
                                                .padding(horizontal = 20.dp)
                                                .fillMaxWidth(),
                                            color = colorScheme.surfaceVariant
                                        )
                                    }
                                }
                            }
                            item { Spacer(Modifier.navigationBarsPadding().height(160.dp)) }
                        }
                    }
                }
            }

            // 设置对话框
            AnimatedVisibility(
                visible = showSettingsDialog,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {

                var showSearchBar by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf("") }

                val backupAudioStatus by remember {
                    derivedStateOf {
                        if (backupAudioApiUrl.isBlank()) "未设置" else "已设置"
                    }
                }

                val allSettingsItems by remember {
                    derivedStateOf {
                        listOf(
                            SettingsItem("账号管理", Icons.Default.ManageAccounts, "管理网易云和B站账号", "account"),
                            SettingsItem("下载位置设置", Icons.Default.Folder, "设置下载文件保存路径", "folder"),
                            SettingsItem("音质设置", Icons.Default.MusicNote, "下载和播放音质选项", "quality"),
                            SettingsItem("本地音乐歌词来源", Icons.Default.LibraryMusic, if (selectedLyricSource == 0) "内嵌" else "网络", "lyric"),
                            SettingsItem("全屏播放器设置", Icons.Default.Fullscreen, "歌词字体、亮度、屏幕常亮等", "player"),
                            SettingsItem("歌曲淡入淡出", Icons.Default.GraphicEq, "播放暂停时音量渐变", "fade"),
                            SettingsItem("自动缓存", Icons.Default.Download, "根据歌曲播放次数，自动缓存歌曲", "auto_cache"),
                            SettingsItem("触感反馈", Icons.Default.Vibration, "按钮点击、操作完成等震动反馈", "vibration"),
                            SettingsItem("默认音乐打开方式", Icons.Default.Apps, "将本应用设为默认音乐播放器", "default_opener"),
                            SettingsItem("备用音源", Icons.Default.Link, backupAudioStatus, "backup_audio"),
                            SettingsItem("启动设置", Icons.Default.Power, "控制应用启动时的播放行为", "startup"),
                            SettingsItem("深色模式", Icons.Default.DarkMode, when (selectedDarkMode) {
                                0 -> "跟随系统"
                                1 -> "浅色"
                                2 -> "深色"
                                else -> "跟随系统"
                            }, "dark"),
                            SettingsItem("主题色", Icons.Default.Palette, when (selectedThemeSource) {
                                0 -> "内置配色"
                                1 -> "壁纸取色"
                                2 -> "专辑封面"
                                3 -> "用户自定义"
                                else -> "内置配色"
                            }, "theme"),
                            SettingsItem("备份与恢复", Icons.Default.Backup, "数据备份与恢复", "backup"),
                            SettingsItem("关于", Icons.Default.Info, "应用信息", "about")
                        )
                    }
                }

                val filteredItems by remember {
                    derivedStateOf {
                        if (searchQuery.isBlank()) {
                            allSettingsItems
                        } else {
                            allSettingsItems.filter {
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                        it.subtitle.contains(searchQuery, ignoreCase = true)
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        CenterAlignedTopAppBar(
                            title = {
                                AnimatedContent(
                                    targetState = showSearchBar,
                                    transitionSpec = {
                                        if (targetState) {
                                            slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith
                                                    slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                                        } else {
                                            slideInHorizontally(initialOffsetX = { -it }) + fadeIn() togetherWith
                                                    slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                                        }
                                    },
                                    label = "title"
                                ) { isSearching ->
                                    if (isSearching) {
                                        TextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            modifier = Modifier.weight(1f),
                                            placeholder = { Text("搜索设置") },
                                            singleLine = true,
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                            )
                                        )
                                    } else {
                                        Text("设置", fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { showSettingsDialog = false }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                }
                            },
                            actions = {
                                if (showSearchBar) {
                                    IconButton(onClick = { showSearchBar = false; searchQuery = "" }) {
                                        Icon(Icons.Default.Close, null)
                                    }
                                } else {
                                    IconButton(onClick = { showSearchBar = true }) {
                                        Icon(Icons.Default.Search, null)
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = colorScheme.background,
                                titleContentColor = colorScheme.onBackground,
                                navigationIconContentColor = colorScheme.primary
                            )
                        )

                        HorizontalDivider()

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            filteredItems.forEach { item ->
                                if (item.id == "account") {
                                    // 可展开的账号管理分组
                                    ListItem(
                                        headlineContent = { Text(item.title) },
                                        supportingContent = { Text(item.subtitle, color = colorScheme.onSurfaceVariant) },
                                        leadingContent = {
                                            Icon(item.icon, null, tint = colorScheme.onSurfaceVariant)
                                        },
                                        trailingContent = {
                                            Icon(
                                                if (showAccountExpand) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                                null,
                                                tint = colorScheme.onSurfaceVariant
                                            )
                                        },
                                        modifier = Modifier.clickable { showAccountExpand = !showAccountExpand }
                                    )
                                    HorizontalDivider(color = colorScheme.surfaceVariant.copy(alpha = 0.3f))

                                    AnimatedVisibility(visible = showAccountExpand) {
                                        Column {
                                            // 网易云账号
                                            ListItem(
                                                headlineContent = { Text("网易云账号") },
                                                supportingContent = {
                                                    Text(
                                                        if (NeteaseApiService.isLoggedIn) "已登录" else "未登录",
                                                        color = colorScheme.onSurfaceVariant
                                                    )
                                                },
                                                leadingContent = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_netease_cloud_music),
                                                        contentDescription = "网易云",
                                                        tint = Color(0xFFDD001B),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                },
                                                modifier = Modifier.clickable {
                                                    if (NeteaseApiService.isLoggedIn) showNeteaseLogoutDialog = true
                                                    else (context as MainActivity).startNeteaseLogin()
                                                }
                                            )
                                            HorizontalDivider(color = colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                            // B站账号
                                            ListItem(
                                                headlineContent = { Text("B站账号") },
                                                supportingContent = {
                                                    Text(
                                                        when (vm.biliLoginState.value) {
                                                            MusicViewModel.BiliLoginState.LoggedIn -> "已登录"
                                                            MusicViewModel.BiliLoginState.NotLoggedIn -> "未登录"
                                                            MusicViewModel.BiliLoginState.Expired -> "登录已过期"
                                                            MusicViewModel.BiliLoginState.Unknown -> "检查中..."
                                                        },
                                                        color = colorScheme.onSurfaceVariant
                                                    )
                                                },
                                                leadingContent = {
                                                    Icon(
                                                        painter = painterResource(R.drawable.ic_bilibili),
                                                        contentDescription = "B站",
                                                        tint = Color.Unspecified,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                },
                                                modifier = Modifier.clickable {
                                                    if (vm.biliLoginState.value == MusicViewModel.BiliLoginState.LoggedIn) showBiliLogoutDialog = true
                                                    else (context as MainActivity).startBiliLogin()
                                                }
                                            )
                                            HorizontalDivider(color = colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        }
                                    }
                                } else if (item.id == "player") {
                                    // 可展开的全屏播放器设置分组
                                    ListItem(
                                        headlineContent = { Text(item.title) },
                                        supportingContent = { Text(item.subtitle, color = colorScheme.onSurfaceVariant) },
                                        leadingContent = {
                                            Icon(item.icon, null, tint = colorScheme.onSurfaceVariant)
                                        },
                                        trailingContent = {
                                            Icon(
                                                if (showPlayerExpand) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                                null,
                                                tint = colorScheme.onSurfaceVariant
                                            )
                                        },
                                        modifier = Modifier.clickable { showPlayerExpand = !showPlayerExpand }
                                    )
                                    HorizontalDivider(color = colorScheme.surfaceVariant.copy(alpha = 0.3f))

                                    AnimatedVisibility(visible = showPlayerExpand) {
                                        Column {
                                            // 歌词字体大小
                                            ListItem(
                                                headlineContent = { Text("歌词字体大小") },
                                                supportingContent = { Text("${lyricFontSize.toInt()}sp", color = colorScheme.onSurfaceVariant) },
                                                leadingContent = {
                                                    Icon(Icons.Default.FormatSize, null, tint = colorScheme.onSurfaceVariant)
                                                },
                                                modifier = Modifier.clickable {
                                                    lyricFontSize = PlaybackSettingsStore.getLyricFontSize(context)
                                                    showLyricFontSizeDialog = true
                                                }
                                            )
                                            HorizontalDivider(color = colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                            // 渐变层亮度
                                            ListItem(
                                                headlineContent = { Text("渐变层亮度") },
                                                supportingContent = { Text("${(gradientBrightnessMultiplier * 100).toInt()}%", color = colorScheme.onSurfaceVariant) },
                                                leadingContent = {
                                                    Icon(Icons.Default.BrightnessAuto, null, tint = colorScheme.onSurfaceVariant)
                                                },
                                                modifier = Modifier.clickable {
                                                    gradientBrightnessMultiplier = PlaybackSettingsStore.getGradientBrightnessMultiplier(context)
                                                    showGradientBrightnessDialog = true
                                                }
                                            )
                                            HorizontalDivider(color = colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                            // 屏幕常亮
                                            ListItem(
                                                headlineContent = { Text("屏幕常亮") },
                                                supportingContent = { Text("仅全屏播放器生效", color = colorScheme.onSurfaceVariant) },
                                                leadingContent = {
                                                    Icon(Icons.Default.Visibility, null, tint = colorScheme.onSurfaceVariant)
                                                },
                                                trailingContent = {
                                                    Switch(
                                                        checked = keepScreenOnEnabled,
                                                        onCheckedChange = { enabled ->
                                                            keepScreenOnEnabled = enabled
                                                            PlaybackSettingsStore.setKeepScreenOnEnabled(context, enabled)
                                                        }
                                                    )
                                                }
                                            )

                                        }
                                    }
                                } else {
                                ListItem(
                                    headlineContent = { Text(item.title) },
                                    supportingContent = { Text(item.subtitle, color = colorScheme.onSurfaceVariant) },
                                    leadingContent = {
                                        Icon(item.icon, null, tint = colorScheme.onSurfaceVariant)
                                    },
                                    trailingContent = {
                                        if (item.id == "fade") {
                                            Switch(
                                                checked = fadeEnabled,
                                                onCheckedChange = { enabled ->
                                                    fadeEnabled = enabled
                                                    DownloadSettingsStore.setFadeEnabled(context, enabled)
                                                }
                                            )
                                        } else if (item.id == "auto_cache") {
                                            Switch(
                                                checked = autoCacheEnabled,
                                                onCheckedChange = { enabled ->
                                                    autoCacheEnabled = enabled
                                                    DownloadSettingsStore.setAutoCacheEnabled(context, enabled)
                                                }
                                            )
                                        } else if (item.id == "vibration") {
                                            Switch(
                                                checked = vibrationEnabled,
                                                onCheckedChange = { enabled ->
                                                    vibrationEnabled = enabled
                                                    DownloadSettingsStore.setVibrationEnabled(context, enabled)
                                                }
                                            )
                                        } else if (item.id == "default_opener") {
                                            Switch(
                                                checked = defaultOpenerEnabled,
                                                onCheckedChange = { enabled ->
                                                    defaultOpenerEnabled = enabled
                                                    DownloadSettingsStore.setDefaultMusicOpenerEnabled(context, enabled)
                                                    if (enabled) {
                                                        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                                        context.startActivity(intent)
                                                    }
                                                }
                                            )
                                        } else {
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        when (item.id) {
                                            "folder" -> showDownloadPathDialog = true
                                            "quality" -> {
                                                selectedDownloadQuality = DownloadSettingsStore.getDownloadQuality(context)
                                                selectedPlayQuality = DownloadSettingsStore.getPlayQuality(context)
                                                showAudioQualityScreen = true
                                            }
                                            "lyric" -> showLyricSourceDialog = true
                                            "dark" -> {
                                                selectedDarkMode = DownloadSettingsStore.getDarkMode(context)
                                                showDarkModeDialog = true
                                            }
                                            "theme" -> {
                                                selectedThemeSource = DownloadSettingsStore.getThemeSource(context)
                                                selectedSeedColor = DownloadSettingsStore.getSeedColor(context)
                                                showThemeDialog = true
                                            }
                                            "startup" -> showStartupSettingsDialog = true
                                            "backup_audio" -> {
                                                backupAudioApiUrl = DownloadSettingsStore.getBackupAudioApiUrl(context)
                                                showBackupAudioDialog = true
                                            }
                                            "backup" -> showBackupDialog = true
                                            "about" -> showAboutScreen = true
                                        }
                                    }
                                )
                                HorizontalDivider(color = colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                } // end else (non-account item)
                            }

                            // 底部留白，与我的音乐界面保持一致
                            Spacer(Modifier.navigationBarsPadding().height(160.dp))
                        }
                    }
                }
            }

            // 备用音源设置对话框
            if (showBackupAudioDialog) {
                AlertDialog(
                    onDismissRequest = { showBackupAudioDialog = false },
                    title = { Text("备用音源设置（实验性功能）") },
                    text = {
                        Column {
                            Text("当官方音源无法播放时，将使用备用音源。", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(
                                value = backupAudioApiUrl,
                                onValueChange = { backupAudioApiUrl = it },
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
                                    backupAudioApiUrl = ""
                                    DownloadSettingsStore.setBackupAudioApiUrl(context, "")
                                    showBackupAudioDialog = false
                                    Toast.makeText(context, "已清除备用音源", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("清除")
                                }
                            }
                            TextButton(onClick = {
                                DownloadSettingsStore.setBackupAudioApiUrl(context, backupAudioApiUrl)
                                showBackupAudioDialog = false
                                Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("保存")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBackupAudioDialog = false }) {
                            Text("取消")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            // 下载路径设置对话框
            if (showDownloadPathDialog) {
                // 初始化状态
                LaunchedEffect(Unit) {
                    useCustomPath = DownloadSettingsStore.isUsingCustomPath(context)
                    customUri = DownloadSettingsStore.getCustomUri(context)
                }

                // 获取Uri的显示路径
                fun getUriPath(uri: Uri?): String {
                    if (uri == null) return "未选择"
                    return uri.path ?: uri.toString()
                }

                AlertDialog(
                    onDismissRequest = { showDownloadPathDialog = false },
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
                            showDownloadPathDialog = false
                        }) {
                            Text("保存")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDownloadPathDialog = false }) {
                            Text("取消")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            // 全屏音质设置界面
            AnimatedVisibility(
                visible = showAudioQualityScreen,
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
                                IconButton(onClick = {
                                    showAudioQualityScreen = false
                                }) {
                                    Icon(Icons.Default.ArrowBack, null)
                                }
                            },
                            actions = {
                                TextButton(onClick = {
                                    DownloadSettingsStore.setDownloadQuality(context, selectedDownloadQuality)
                                    DownloadSettingsStore.setPlayQuality(context, selectedPlayQuality)
                                    showAudioQualityScreen = false
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

            // 本地音乐歌词来源设置对话框
            if (showLyricSourceDialog) {
                AlertDialog(
                    onDismissRequest = { showLyricSourceDialog = false },
                    title = { Text("本地音乐歌词来源") },
                    text = {
                        Column {
                            // 内嵌歌词选项
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedLyricSource = 0
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedLyricSource == 0,
                                    onClick = { selectedLyricSource = 0 },
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
                                    selectedLyricSource = 1
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedLyricSource == 1,
                                    onClick = { selectedLyricSource = 1 },
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
                            showLyricSourceDialog = false
                        }) {
                            Text("确定")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            // 歌词字体大小设置对话框
            if (showLyricFontSizeDialog) {
                val tempFontSize = remember { mutableStateOf(lyricFontSize) }
                val lastFontSizeValue = remember { mutableStateOf(tempFontSize.value) }
                
                AlertDialog(
                    onDismissRequest = { showLyricFontSizeDialog = false },
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
                            lyricFontSize = tempFontSize.value
                            PlaybackSettingsStore.setLyricFontSize(context, tempFontSize.value)
                            showLyricFontSizeDialog = false
                        }) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLyricFontSizeDialog = false }) {
                            Text("取消")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            // 渐变层亮度调节对话框
            if (showGradientBrightnessDialog) {
                val tempMultiplier = remember { mutableStateOf(gradientBrightnessMultiplier) }
                val lastMultiplierValue = remember { mutableStateOf(tempMultiplier.value) }
                
                AlertDialog(
                    onDismissRequest = { showGradientBrightnessDialog = false },
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
                            gradientBrightnessMultiplier = tempMultiplier.value
                            PlaybackSettingsStore.setGradientBrightnessMultiplier(context, tempMultiplier.value)
                            showGradientBrightnessDialog = false
                        }) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGradientBrightnessDialog = false }) {
                            Text("取消")
                        }
                    },
                    containerColor = colorScheme.surface
                )
            }

            // 深色模式设置对话框
            if (showDarkModeDialog) {
                AlertDialog(
                    onDismissRequest = { showDarkModeDialog = false },
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
                                        selectedDarkMode = mode
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedDarkMode == mode,
                                        onClick = { selectedDarkMode = mode },
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
                            showDarkModeDialog = false
                        }) {
                            Text("保存")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDarkModeDialog = false }) {
                            Text("取消")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            // 主题色设置对话框
            if (showThemeDialog) {
                var isExtracting by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                // 切换到壁纸取色时自动提取
                LaunchedEffect(selectedThemeSource) {
                    if (selectedThemeSource == 1 && selectedSeedColor == 0L) {
                        isExtracting = true
                        scope.launch {
                            val result = ThemeColorUtil.extractFromWallpaper(context)
                            if (result != null) {
                                selectedSeedColor = result
                            } else {
                                Toast.makeText(context, "无法读取系统壁纸", Toast.LENGTH_SHORT).show()
                            }
                            isExtracting = false
                        }
                    }
                }

                AlertDialog(
                    onDismissRequest = { showThemeDialog = false },
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
                                        selectedThemeSource = mode
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedThemeSource == mode,
                                        onClick = { selectedThemeSource = mode },
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
                                                selectedSeedColor = result
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
                                selectedSeedColor = previewColor.toArgb().toLong() and 0xFFFFFFFFL

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
                            showThemeDialog = false
                        }) {
                            Text("保存")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showThemeDialog = false }) {
                            Text("取消")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            // 启动设置对话框
            if (showStartupSettingsDialog) {
                AlertDialog(
                    onDismissRequest = { showStartupSettingsDialog = false },
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
                                        keepPlaylistOnExitEnabled = enabled
                                        DownloadSettingsStore.setKeepPlaylistOnExitEnabled(context, enabled)
                                        // 如果关闭保留列表，同时关闭启动时播放
                                        if (!enabled && autoPlayOnStartEnabled) {
                                            autoPlayOnStartEnabled = false
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
                                            autoPlayOnStartEnabled = enabled
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
                        TextButton(onClick = {
                            showStartupSettingsDialog = false
                        }) {
                            Text("确定")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }

            // 全屏关于界面
            AnimatedVisibility(
                visible = showAboutScreen,
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
                                IconButton(onClick = { showAboutScreen = false }) {
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

        BackHandler(enabled = activePlaylist != null || activeRecentPlaylist != null || showAboutScreen || showSettingsDialog || showAudioQualityScreen) {
            if (activePlaylist != null) {
                activePlaylist = null
            } else if (activeRecentPlaylist != null) {
                activeRecentPlaylist = null
            } else if (showAudioQualityScreen) {
                showAudioQualityScreen = false
            } else if (showAboutScreen) {
                showAboutScreen = false
            } else if (showSettingsDialog) {
                showSettingsDialog = false
            }
        }

        // 网易云退出登录确认
        if (showNeteaseLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showNeteaseLogoutDialog = false },
                containerColor = colorScheme.surface,
                title = { Text("网易云账号") },
                text = { Text("确定要退出网易云登录吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        NeteaseApiService.logout()
                        showNeteaseLogoutDialog = false
                        Toast.makeText(context, "已退出网易云登录", Toast.LENGTH_SHORT).show()
                    }) { Text("退出", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showNeteaseLogoutDialog = false }) { Text("取消") }
                }
            )
        }

        // B站退出登录确认
        if (showBiliLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showBiliLogoutDialog = false },
                containerColor = colorScheme.surface,
                title = { Text("B站账号") },
                text = { Text("确定要退出B站登录吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        val biliApi = com.qian.jianyin.bili.BiliApi.getInstance(context)
                        biliApi.clearCookies()
                        vm.biliLoginState.value = MusicViewModel.BiliLoginState.NotLoggedIn
                        showBiliLogoutDialog = false
                        Toast.makeText(context, "已退出B站登录", Toast.LENGTH_SHORT).show()
                    }) { Text("退出", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showBiliLogoutDialog = false }) { Text("取消") }
                }
            )
        }

        // 备份与恢复对话框
        if (showBackupDialog) {
            val backupManager = remember { BackupManager(context) }
            val backupFiles = remember { backupManager.getBackupFiles() }
            var isBackingUp by remember { mutableStateOf(false) }
            var backupResult by remember { mutableStateOf<String?>(null) }

            AlertDialog(
                onDismissRequest = { showBackupDialog = false },
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
                                                showBackupDialog = false
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
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text("关闭")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }

@Composable
fun DeveloperCard(
    avatarRes: Int,
    name: String,
    role: String,
    description: String,
    githubUrl: String?
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = {
            if (githubUrl != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                context.startActivity(intent)
            }
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(300.dp),
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
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = avatarRes),
                    contentDescription = "$name 头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    name,
                    color = colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    role,
                    color = colorScheme.primary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1
                )
                Text(
                    description,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1
                )
            }

            if (githubUrl != null) {
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.github),
                        contentDescription = "GitHub",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        )
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
