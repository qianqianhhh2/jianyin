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


private val placeholderIds = listOf(R.drawable.miku_1, R.drawable.miku_2, R.drawable.miku_3, R.drawable.miku_4, R.drawable.miku_5, R.drawable.miku_6, R.drawable.miku_7, R.drawable.miku_8, R.drawable.miku_9)

private fun getPlaceholderId(index: Int): Int {
    return placeholderIds[(index and Int.MAX_VALUE) % placeholderIds.size]
}

@Composable
fun SongItemV6(song: Song, cs: ColorScheme, onClick: () -> Unit) {
    val mikuPainter = painterResource(id = getPlaceholderId((song.id ?: song.url).hashCode()))
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
    val mikuPainter = painterResource(id = getPlaceholderId(playlist.id.hashCode()))
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
    // 音质设置相关状态
    var showAudioQualityScreen by remember { mutableStateOf(false) }

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

    // Haze效果开关
    var hazeEffectEnabled by remember {
        mutableStateOf(
            DownloadSettingsStore.isHazeEffectEnabled(context)
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
                                itemsIndexed(previewSongs, key = { _, song -> song.id.ifBlank { song.url } }) { index, song ->
                                    val useHazeEffect = DownloadSettingsStore.isHazeEffectEnabled(context)
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
                                                .run { if (useHazeEffect) hazeSource(itemHazeState) else this },
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = getPlaceholderId(index))
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
                                                .run {
                                                    if (useHazeEffect) {
                                                        hazeEffect(
                                                            itemHazeState,
                                                            HazeStyle(
                                                                blurRadius = 10.dp,
                                                                tint = HazeTint(Color.Black.copy(alpha = 0.2f))
                                                            )
                                                        )
                                                    } else {
                                                        background(Color.Black.copy(alpha = 0.4f))
                                                    }
                                                }
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
                            itemsIndexed(favoriteSongs, key = { _, song -> song.id.ifBlank { song.url } }) { index, song ->
                                val useHazeEffect = DownloadSettingsStore.isHazeEffectEnabled(context)
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
                                            .run { if (useHazeEffect) hazeSource(itemHazeState) else this },
                                        contentScale = ContentScale.Crop,
                                        error = painterResource(id = getPlaceholderId(index))
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
                                            .run {
                                                if (useHazeEffect) {
                                                    hazeEffect(
                                                        itemHazeState,
                                                        HazeStyle(
                                                            blurRadius = 10.dp,
                                                            tint = HazeTint(Color.Black.copy(alpha = 0.2f))
                                                        )
                                                    )
                                                } else {
                                                    background(Color.Black.copy(alpha = 0.4f))
                                                }
                                            }
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
                                            val placeholderId = getPlaceholderId(targetPlaylist.id.hashCode())
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
                                                            error = painterResource(id = placeholderId)
                                                        )
                                                    } else {
                                                        Image(
                                                            painter = painterResource(id = placeholderId),
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
                                        val useHazeEffect = DownloadSettingsStore.isHazeEffectEnabled(context)
                                        val hazeState = remember { HazeState() }
                                        Box(Modifier.fillMaxWidth()) {
                                            AsyncImage(
                                                model = filteredAndSortedSongs[0].pic,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(190.dp)
                                                    .run { if (useHazeEffect) hazeSource(hazeState) else this },
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(id = getPlaceholderId(0))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(50.dp)
                                                    .run {
                                                        if (useHazeEffect) {
                                                            hazeEffect(
                                                                hazeState,
                                                                HazeStyle(
                                                                    blurRadius = 8.dp,
                                                                    tint = HazeTint(Color.Black.copy(alpha = 0.3f))
                                                                )
                                                            )
                                                        } else {
                                                            background(Color.Black.copy(alpha = 0.4f))
                                                        }
                                                    }
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

                                itemsIndexed(filteredAndSortedSongs, key = { _, song -> song.id.ifBlank { song.url } }) { index, song ->
                                    var isDownloading by remember { mutableStateOf(false) }
                                    var showSongMenu by remember { mutableStateOf(false) }

                                    Column {
                                        val isSelected = selectedSongs.contains(index)
                                        val isDragging = draggedSongIndex != -1 && selectedSongs.contains(index)
                                        val currentSong = vm.currentSong.value
                                        val isCurrentlyPlaying = remember(currentSong, song.id, song.url) {
                                            currentSong?.let {
                                                (song.id.isNotBlank() && song.id == it.id) ||
                                                (song.url.isNotBlank() && song.url == it.url)
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
                                                error = painterResource(id = getPlaceholderId(index))
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
                            itemsIndexed(activeRecentPlaylist ?: emptyList(), key = { _, song -> song.id.ifBlank { song.url } }) { index, song ->
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
                                                error = painterResource(id = getPlaceholderId(index))
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
                            SettingsItem("高级效果", Icons.Default.BlurLinear, "将为组件添加模糊效果", "haze"),
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
                                        } else if (item.id == "haze") {
                                            Switch(
                                                checked = hazeEffectEnabled,
                                                onCheckedChange = { enabled ->
                                                    hazeEffectEnabled = enabled
                                                    DownloadSettingsStore.setHazeEffectEnabled(context, enabled)
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
                                            "quality" -> showAudioQualityScreen = true
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

            BackupAudioDialog(
                show = showBackupAudioDialog,
                onDismiss = { showBackupAudioDialog = false },
                backupAudioApiUrl = backupAudioApiUrl,
                onBackupAudioApiUrlChange = { backupAudioApiUrl = it }
            )

            DownloadPathDialog(
                show = showDownloadPathDialog,
                onDismiss = { showDownloadPathDialog = false }
            )

            AudioQualityScreen(
                visible = showAudioQualityScreen,
                onBack = { showAudioQualityScreen = false }
            )

            LyricSourceDialog(
                show = showLyricSourceDialog,
                onDismiss = { showLyricSourceDialog = false },
                selectedLyricSource = selectedLyricSource,
                onSelectedLyricSourceChange = { selectedLyricSource = it }
            )

            LyricFontSizeDialog(
                show = showLyricFontSizeDialog,
                onDismiss = { showLyricFontSizeDialog = false },
                lyricFontSize = lyricFontSize,
                onLyricFontSizeChange = { lyricFontSize = it }
            )

            GradientBrightnessDialog(
                show = showGradientBrightnessDialog,
                onDismiss = { showGradientBrightnessDialog = false },
                gradientBrightnessMultiplier = gradientBrightnessMultiplier,
                onGradientBrightnessMultiplierChange = { gradientBrightnessMultiplier = it }
            )

            DarkModeDialog(
                show = showDarkModeDialog,
                onDismiss = { showDarkModeDialog = false },
                selectedDarkMode = selectedDarkMode,
                onSelectedDarkModeChange = { selectedDarkMode = it }
            )

            ThemeDialog(
                show = showThemeDialog,
                onDismiss = { showThemeDialog = false },
                selectedThemeSource = selectedThemeSource,
                onSelectedThemeSourceChange = { selectedThemeSource = it },
                selectedSeedColor = selectedSeedColor,
                onSelectedSeedColorChange = { selectedSeedColor = it }
            )

            StartupSettingsDialog(
                show = showStartupSettingsDialog,
                onDismiss = { showStartupSettingsDialog = false },
                keepPlaylistOnExitEnabled = keepPlaylistOnExitEnabled,
                onKeepPlaylistOnExitEnabledChange = { keepPlaylistOnExitEnabled = it },
                autoPlayOnStartEnabled = autoPlayOnStartEnabled,
                onAutoPlayOnStartEnabledChange = { autoPlayOnStartEnabled = it }
            )

            AboutScreen(
                visible = showAboutScreen,
                onBack = { showAboutScreen = false },
                appVersion = appVersion
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

        NeteaseLogoutDialog(
            show = showNeteaseLogoutDialog,
            onDismiss = { showNeteaseLogoutDialog = false }
        )

        BiliLogoutDialog(
            show = showBiliLogoutDialog,
            onDismiss = { showBiliLogoutDialog = false },
            vm = vm
        )

        BackupDialog(
            show = showBackupDialog,
            onDismiss = { showBackupDialog = false }
        )
    }

