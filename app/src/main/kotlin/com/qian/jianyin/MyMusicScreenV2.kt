package com.qian.jianyin

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.*
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.qian.jianyin.R
import com.qian.jianyin.MainActivity
import com.qian.jianyin.BuildConfig
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min



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
    var customPathInput by remember { mutableStateOf("") }
    // 音质设置相关状态
    var showDownloadQualityDialog by remember { mutableStateOf(false) }
    var showPlayQualityDialog by remember { mutableStateOf(false) }
    var selectedDownloadQuality by remember { mutableStateOf(192) }
    var selectedPlayQuality by remember { mutableStateOf(192) }
    
    // 歌词来源设置相关状态
    var showLyricSourceDialog by remember { mutableStateOf(false) }
    var selectedLyricSource by remember { mutableStateOf(DownloadSettingsStore.getLyricSource(context)) } // 0: 内嵌, 1: 网络
    // 从 PackageManager 获取应用版本号，并自动添加 debug/release 后缀
    val appVersion = remember {
        try {
            val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "未知版本"
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
                        Toast.makeText(context, "本地歌单导入成功，共 ${songs.size} 首歌曲", Toast.LENGTH_LONG).show()
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

    Box(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // 添加64dp的顶部空间
            Spacer(modifier = Modifier.height(64.dp))
            // 顶部标题行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的音乐",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = colorScheme.onBackground
                )
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "设置"
                    )
                }
            }
            

            // 最近播放预览板块（显示10首）
            if (vm.historyList.isNotEmpty()) {
                val previewSongs by remember { derivedStateOf { vm.historyList.takeLast(10).reversed() } }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "最近播放",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                // 点击查看更多后进入详情页，显示完整的50首
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
                                        // 直接点击歌曲播放
                                        vm.playSong(song, previewSongs)
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
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .size(120.dp, 40.dp)
                                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                                        .hazeEffect(itemHazeState, HazeStyle(blurRadius = 10.dp, tint = HazeTint(Color.Black.copy(alpha = 0.2f))))
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
                Spacer(Modifier.height(24.dp))
            }

            // 最近最爱部分
            if (favoriteSongs.isNotEmpty()) {
                SectionHeaderV6("最近最爱", Icons.AutoMirrored.Filled.KeyboardArrowRight)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(favoriteSongs) { song ->
                        val itemHazeState = remember { HazeState() }
                        Box(modifier = Modifier.width(120.dp).clickable { vm.playSong(song, favoriteSongs) }) {
                            AsyncImage(
                                model = song.pic,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSystemInDarkTheme()) Color(0xFF2D3748) else Color(0xFFE3EAF6))
                                    .hazeSource(itemHazeState),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .size(120.dp, 40.dp)
                                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                                    .hazeEffect(itemHazeState, HazeStyle(blurRadius = 10.dp, tint = HazeTint(Color.Black.copy(alpha = 0.2f))))
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

            Spacer(Modifier.height(12.dp))

            // 同步歌单标题栏：改为 Add 图标
            SectionHeaderV6("同步的歌单", Icons.Default.Add) {
                showAddDialog = true
            }

            if (syncedPlaylists.isEmpty()) {
                Text("暂无同步歌单，点击上方 + 号导入", color = colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp))
            } else {
                syncedPlaylists.forEach { playlist ->
                    PlaylistItemV6(
                        playlist = playlist,
                        colorScheme = colorScheme,
                        onClick = {
                            activePlaylist = playlist
                        },
                        onLongClick = {
                            selectedPlaylistForMenu = playlist
                        }
                    )
                }
            }
            Spacer(Modifier.height(200.dp))
        }

        // --- 弹窗逻辑 1：添加歌单 ---
        if (showAddDialog) {
            var selectedSource by remember { mutableStateOf(0) } // 0: 网易云歌单, 1: 本地歌单
            
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
                        }
                        
                        // 根据选择显示不同的输入界面
                        if (selectedSource == 0) {
                            OutlinedTextField(
                                value = playlistIdInput,
                                onValueChange = { playlistIdInput = it },
                                label = { Text("输入分享内容") },
                                placeholder = { Text("例如：分享歌单: 谦谦=12793863438&creatorId=12638542831") },
                                singleLine = true
                            )
                        } else {
                            Text("选择本地文件夹导入歌曲")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (selectedSource == 0) {
                            // 网易云歌单
                            if (playlistIdInput.isBlank()) return@Button
                            scope.launch {
                                // 从分享内容中提取歌单ID
                                val playlistId = extractPlaylistId(playlistIdInput)
                                if (playlistId.isNullOrBlank()) {
                                    Toast.makeText(context, "无法提取歌单ID，请检查输入", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val songs = PlaylistSyncManager.fetchPlaylist(playlistId, context)
                                if (songs != null) {
                                    val newList = UserSyncedPlaylist(playlistId, "新歌单_${playlistId}", songs.firstOrNull()?.pic ?: "", songs)
                                    PlaylistDataStore.save(context, newList)
                                    syncedPlaylists.clear()
                                    syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                    showAddDialog = false
                                    playlistIdInput = ""
                                    Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "同步失败，请检查分享内容", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            // 本地歌单：打开文件选择器
                            showAddDialog = false
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                            intent.addCategory(Intent.CATEGORY_DEFAULT)
                            
                            // 设置回调函数，当文件夹选择完成后会被调用
                            (context as? MainActivity)?.folderUriCallback = {
                                folderUri = it
                            }
                            
                            // 启动文件夹选择器
                            (context as? android.app.Activity)?.startActivityForResult(intent, 1001)
                        }
                    }) {
                        Text(if (selectedSource == 0) "同步" else "选择文件夹")
                    }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
            )
        }

        // --- 弹窗逻辑 2：长按菜单 ---
        if (selectedPlaylistForMenu != null) {
            val isFavoritesPlaylist = selectedPlaylistForMenu?.id == "jianyin_favorites_playlist"
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
                                    Toast.makeText(context, "已更新！", Toast.LENGTH_SHORT).show()
                                } else if (target.id.startsWith("local_")) {
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
                                                    val customCover = SongCustomDataStore.getCover(context, song.url)
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
                                                val index = syncedPlaylists.indexOfFirst { it.id == target.id }
                                                if (index != -1) syncedPlaylists[index] = updated
                                                Toast.makeText(context, "已更新本地歌曲列表", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "未发现音乐文件", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "无法获取文件夹路径", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "歌单为空，无法刷新", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    // 网络歌单：从服务器获取
                                    val songs = PlaylistSyncManager.fetchPlaylist(target.id, context)
                                    if (songs != null) {
                                        // 更新歌单封面为第一首歌的封面
                                        val newCoverPic = songs.firstOrNull()?.pic ?: ""
                                        val updated = target.copy(
                                            songs = songs,
                                            coverPic = newCoverPic
                                        )
                                        PlaylistDataStore.update(context, updated)
                                        // 更新 UI 列表
                                        val index = syncedPlaylists.indexOfFirst { it.id == target.id }
                                        if (index != -1) syncedPlaylists[index] = updated
                                        Toast.makeText(context, "已更新歌曲列表", Toast.LENGTH_SHORT).show()
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
                                color = if (isFavoritesPlaylist) colorScheme.onSurfaceVariant.copy(alpha = 0.5f) 
                                      else colorScheme.onSurface
                            )
                        },
                        leadingContent = { 
                            Icon(
                                Icons.Default.Edit, 
                                null, 
                                tint = if (isFavoritesPlaylist) colorScheme.onSurfaceVariant.copy(alpha = 0.5f) 
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

                    // 菜单项：删除
                    ListItem(
                        headlineContent = { 
                            Text(
                                "删除歌单", 
                                color = if (isFavoritesPlaylist) colorScheme.onSurfaceVariant.copy(alpha = 0.5f) 
                                      else colorScheme.error
                            ) 
                        },
                        leadingContent = { 
                            Icon(
                                Icons.Default.Delete, 
                                null, 
                                tint = if (isFavoritesPlaylist) colorScheme.onSurfaceVariant.copy(alpha = 0.5f) 
                                      else colorScheme.error
                            ) 
                        },
                        modifier = Modifier.clickable(
                            enabled = !isFavoritesPlaylist,
                            onClick = {
                                if (!isFavoritesPlaylist) {
                                    PlaylistDataStore.safeDelete(context, selectedPlaylistForMenu!!.id)
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
                    OutlinedTextField(value = newNameInput, onValueChange = { newNameInput = it }, singleLine = true)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedPlaylistForMenu?.let {
                                if (it.id == "jianyin_favorites_playlist") {
                                    Toast.makeText(context, "收藏歌单不可重命名", Toast.LENGTH_SHORT).show()
                                } else {
                                    val updated = it.copy(name = newNameInput)
                                    if (PlaylistDataStore.safeUpdate(context, updated)) {
                                        val idx = syncedPlaylists.indexOfFirst { p -> p.id == it.id }
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
                // 检查本地歌单目录是否存在
                if (playlist.id.startsWith("local_")) {
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
                                    Toast.makeText(context, "文件夹已被改名或移动，歌单已删除", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                // 无法获取文件夹路径，删除歌单并退出
                                PlaylistDataStore.delete(context, playlist.id)
                                syncedPlaylists.clear()
                                syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                activePlaylist = null
                                Toast.makeText(context, "无法获取文件夹路径，歌单已删除", Toast.LENGTH_LONG).show()
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
                
                // 从 SharedPreferences 加载排序设置
                var sortBy by remember(playlist.id) {
                    val prefs = context.getSharedPreferences("playlist_sort_prefs", android.content.Context.MODE_PRIVATE)
                    mutableStateOf(prefs.getString("sort_by_${playlist.id}", "default") ?: "default")
                }
                var sortOrder by remember(playlist.id) {
                    val prefs = context.getSharedPreferences("playlist_sort_prefs", android.content.Context.MODE_PRIVATE)
                    mutableStateOf(prefs.getBoolean("sort_order_${playlist.id}", true))
                }
                
                // 处理排序和搜索后的歌曲列表
                val filteredAndSortedSongs by remember {
                    derivedStateOf {
                        var result = playlist.songs
                        
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
                    val prefs = context.getSharedPreferences("playlist_sort_prefs", android.content.Context.MODE_PRIVATE)
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
                                Text(playlist.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (isSelectionMode) {
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
                                // 本地歌单禁用下载按钮
                                if (!playlist.id.startsWith("local_")) {
                                    IconButton(onClick = {
                                        // 批量下载选中的歌曲
                                        val songsToDownload = selectedSongs.mapNotNull { index ->
                                            filteredAndSortedSongs.getOrNull(index)
                                        }
                                        
                                        scope.launch {
                                            val customPath = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomPath(context) else null
                                            DownloadStateManager.startDownload(songsToDownload.size)
                                            
                                            DownloadManager.downloadSongs(
                                                context,
                                                songsToDownload,
                                                customPath
                                            ) { index, total, songName, progress ->
                                                DownloadStateManager.updateCurrentSong(index, songName)
                                                DownloadStateManager.updateProgress(progress)
                                            }
                                                .onSuccess { results ->
                                                    val successCount = results.count { it.startsWith("下载完成") }
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
                                                    DownloadStateManager.downloadFailed(e.message ?: "未知错误")
                                                }
                                            
                                            // 退出选择模式
                                            isSelectionMode = false
                                            selectedSongs = emptySet()
                                            firstSelectedIndex = null
                                            lastSelectedIndex = null
                                        }
                                    }) {
                                        Icon(Icons.Default.Download, null, tint = colorScheme.primary)
                                    }
                                }
                                
                                IconButton(onClick = {
                                    // 批量从歌单移除选中的歌曲
                                    val songsToRemove = selectedSongs.mapNotNull { index ->
                                        filteredAndSortedSongs.getOrNull(index)
                                    }
                                    
                                    if (songsToRemove.isNotEmpty()) {
                                        var removedCount = 0
                                        songsToRemove.forEach { song ->
                                            val removed = PlaylistDataStore.removeSongFromPlaylist(context, playlist.id, song)
                                            if (removed) {
                                                removedCount++
                                            }
                                        }
                                        
                                        // 更新本地歌单数据
                                        val updatedPlaylist = playlist.copy(
                                            songs = playlist.songs.filter { song ->
                                                !songsToRemove.contains(song)
                                            }
                                        )
                                        activePlaylist = updatedPlaylist
                                        // 刷新歌单列表
                                        syncedPlaylists.clear()
                                        syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                        
                                        // 退出选择模式
                                        isSelectionMode = false
                                        selectedSongs = emptySet()
                                        firstSelectedIndex = null
                                        lastSelectedIndex = null
                                        
                                        Toast.makeText(
                                            context,
                                            "已从歌单移除 $removedCount 首歌曲",
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
                                Text("排序方式", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
                                
                                // 默认排序
                                ListItem(
                                    headlineContent = { Text("默认排序") },
                                    leadingContent = {
                                        RadioButton(
                                            selected = sortBy == "default",
                                            onClick = { 
                                                sortBy = "default"
                                                val prefs = context.getSharedPreferences("playlist_sort_prefs", android.content.Context.MODE_PRIVATE)
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

                    if (isLoadingSongs) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                            CircularProgressIndicator(color = colorScheme.primary) 
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (filteredAndSortedSongs.isNotEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                                        AsyncImage(
                                            model = filteredAndSortedSongs[0].pic, 
                                            contentDescription = null, 
                                            modifier = Modifier
                                                .size(240.dp)
                                                .clip(RoundedCornerShape(28.dp))
                                                .background(colorScheme.surfaceVariant), 
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                
                                item {
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        FilledTonalButton(
                                            onClick = { vm.playSong(filteredAndSortedSongs[0], filteredAndSortedSongs) },
                                            modifier = Modifier.padding(bottom = 20.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("播放全部")
                                        }
                                    }
                                }
                            }
                            
                            itemsIndexed(filteredAndSortedSongs) { index, song ->
                                var isDownloading by remember { mutableStateOf(false) }
                                var showSongMenu by remember { mutableStateOf(false) }
                                
                                Column {
                                    val isSelected = selectedSongs.contains(index)
                                    
                                    var isLongClick by remember { mutableStateOf(false) }
                                    
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
                                                    if (isSelectionMode) {
                                                        // 普通点击：只进行单选
                                                        val newSelected = if (isSelected) {
                                                            // 取消选择
                                                            selectedSongs - index
                                                        } else {
                                                            // 单选
                                                            selectedSongs + index
                                                        }
                                                        selectedSongs = newSelected
                                                        
                                                        // 更新首尾选择索引
                                                        if (newSelected.isNotEmpty()) {
                                                            firstSelectedIndex = newSelected.minOrNull()
                                                            lastSelectedIndex = newSelected.maxOrNull()
                                                        } else {
                                                            firstSelectedIndex = null
                                                            lastSelectedIndex = null
                                                        }
                                                    } else {
                                                        vm.playSong(song, filteredAndSortedSongs)
                                                    }
                                                },
                                                onLongClick = {
                                                    if (playlist.id.startsWith("local_")) {
                                                        // 本地歌单：弹出删除确认对话框
                                                        showSongMenu = true
                                                    } else {
                                                        // 网络歌单：进入选择模式
                                                        if (!isSelectionMode) {
                                                            // 进入选择模式并选择当前歌曲
                                                            isSelectionMode = true
                                                            selectedSongs = setOf(index)
                                                            firstSelectedIndex = index
                                                            lastSelectedIndex = index
                                                        } else if (selectedSongs.isNotEmpty() && firstSelectedIndex != null) {
                                                            // 长按第二首：自动包含中间的歌曲
                                                            val start = min(firstSelectedIndex!!, index)
                                                            val end = max(firstSelectedIndex!!, index)
                                                            // 选择范围内的所有歌曲
                                                            selectedSongs = (start..end).toSet()
                                                            lastSelectedIndex = end
                                                        }
                                                    }
                                                }
                                            )
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
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
                                            contentScale = ContentScale.Crop
                                        )
                                        Column(Modifier.padding(start = if (isSelectionMode) 12.dp else 16.dp).weight(1f)) {
                                            Text(song.name, color = colorScheme.onBackground, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artist, color = colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                                        }
                                        
                                        if (!isSelectionMode) {
                                            // 播放按钮
                                            IconButton(
                                                onClick = { vm.playSong(song, filteredAndSortedSongs) },
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
                                                title = { Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                text = { 
                                                    if (playlist.id.startsWith("local_")) {
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
                                                                        .onSuccess {
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
                                                        ) {
                                                            Text("下载")
                                                        }
                                                    } else if (playlist.id.startsWith("local_")) {
                                                        // 本地歌单：删除文件
                                                        TextButton(
                                                            onClick = {
                                                                showSongMenu = false
                                                                // 从歌单移除并删除文件
                                                                val removed = PlaylistDataStore.removeSongFromPlaylist(context, playlist.id, song)
                                                                if (removed) {
                                                                    // 删除本地文件
                                                                    try {
                                                                        val file = File(song.url)
                                                                        if (file.exists()) {
                                                                            file.delete()
                                                                        }
                                                                    } catch (e: Exception) {
                                                                        e.printStackTrace()
                                                                    }
                                                                    
                                                                    // 更新本地歌单数据
                                                                    val updatedPlaylist = playlist.copy(
                                                                        songs = playlist.songs.filterNot { 
                                                                            (it.id.isNotBlank() && it.id == song.id) || 
                                                                            (it.url.isNotBlank() && it.url == song.url)
                                                                        }
                                                                    )
                                                                    activePlaylist = updatedPlaylist
                                                                    // 刷新歌单列表
                                                                    syncedPlaylists.clear()
                                                                    syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                                                    Toast.makeText(context, "已移除并删除文件: ${song.name}", Toast.LENGTH_SHORT).show()
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
                                                            val removed = PlaylistDataStore.removeSongFromPlaylist(context, playlist.id, song)
                                                            if (removed) {
                                                                // 更新本地歌单数据
                                                                val updatedPlaylist = playlist.copy(
                                                                    songs = playlist.songs.filterNot { 
                                                                        (it.id.isNotBlank() && it.id == song.id) || 
                                                                        (it.url.isNotBlank() && it.url == song.url)
                                                                    }
                                                                )
                                                                activePlaylist = updatedPlaylist
                                                                // 刷新歌单列表
                                                                syncedPlaylists.clear()
                                                                syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                                                Toast.makeText(context, "已从歌单移除: ${song.name}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    ) {
                                                        Text(if (playlist.id.startsWith("local_")) "删除列表" else "从歌单移除", color = colorScheme.error)
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
                                Text("已选择 ${selectedRecentSongs.size} 首", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                                    val songsToDownload = selectedRecentSongs.mapNotNull { idx ->
                                        activeRecentPlaylist?.getOrNull(idx)
                                    }
                                    
                                    if (songsToDownload.isNotEmpty()) {
                                        scope.launch {
                                            val customPath = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomPath(context) else null
                                            DownloadStateManager.startDownload(songsToDownload.size)
                                            
                                            DownloadManager.downloadSongs(
                                                context,
                                                songsToDownload,
                                                customPath
                                            ) { index, total, songName, progress ->
                                                DownloadStateManager.updateCurrentSong(index, songName)
                                                DownloadStateManager.updateProgress(progress)
                                            }
                                                .onSuccess { results ->
                                                    val successCount = results.count { it.startsWith("下载完成") }
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
                                                    DownloadStateManager.downloadFailed(e.message ?: "未知错误")
                                                }
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Download, null, tint = colorScheme.primary)
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
                                                        firstRecentSelectedIndex = newSelected.minOrNull()
                                                        lastRecentSelectedIndex = newSelected.maxOrNull()
                                                    } else {
                                                        firstRecentSelectedIndex = null
                                                        lastRecentSelectedIndex = null
                                                    }
                                                } else {
                                                    vm.playSong(song, activeRecentPlaylist ?: emptyList())
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
                                                    val start = min(firstRecentSelectedIndex!!, index)
                                                    val end = max(firstRecentSelectedIndex!!, index)
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
                                    
                                    AsyncImage(
                                        model = song.pic,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colorScheme.surfaceVariant),
                                        contentScale = ContentScale.Crop
                                    )
                                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                        Text(song.name, color = colorScheme.onBackground, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(song.artist, color = colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                                    }
                                    // 非选择模式下显示播放按钮
                                    if (!isRecentSelectionMode) {
                                        IconButton(
                                            onClick = { vm.playSong(song, activeRecentPlaylist ?: emptyList()) },
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
                                            title = { Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            text = { Text("选择操作") },
                                            confirmButton = {
                                                TextButton(
                                                    onClick = {
                                                        showSongMenu = false
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
                                                                .onSuccess {
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
                                                            Toast.makeText(context, "已从最近播放移除: ${song.name}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Text("从最近播放移除", color = colorScheme.error)
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
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("设置") },
                text = {
                    Column {
                        // 下载位置设置
                        ListItem(
                            headlineContent = { Text("下载位置设置") },
                            leadingContent = { Icon(Icons.Default.Folder, null) },
                            supportingContent = { Text(DownloadSettingsStore.getDownloadPath(context)) },
                            modifier = Modifier.clickable {
                                showDownloadPathDialog = true
                            }
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // 下载音质设置
                        ListItem(
                            headlineContent = { Text("下载音质") },
                            leadingContent = { Icon(Icons.Default.MusicNote, null) },
                            supportingContent = { Text("${DownloadSettingsStore.getDownloadQuality(context)}kbps") },
                            modifier = Modifier.clickable {
                                selectedDownloadQuality = DownloadSettingsStore.getDownloadQuality(context)
                                showDownloadQualityDialog = true
                            }
                        )
                        
                        // 播放音质设置
                        ListItem(
                            headlineContent = { Text("播放音质") },
                            leadingContent = { Icon(Icons.Default.Headphones, null) },
                            supportingContent = { Text("${DownloadSettingsStore.getPlayQuality(context)}kbps") },
                            modifier = Modifier.clickable {
                                selectedPlayQuality = DownloadSettingsStore.getPlayQuality(context)
                                showPlayQualityDialog = true
                            }
                        )
                        
                        // 本地音乐歌词来源设置
                        ListItem(
                            headlineContent = { Text("本地音乐歌词来源") },
                            leadingContent = { Icon(Icons.Default.LibraryMusic, null) },
                            supportingContent = { Text(if (selectedLyricSource == 0) "内嵌" else "网络") },
                            modifier = Modifier.clickable {
                                showLyricSourceDialog = true
                            }
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // 备份与恢复
                        ListItem(
                            headlineContent = { Text("备份与恢复") },
                            leadingContent = { Icon(Icons.Default.Backup, null) },
                            modifier = Modifier.clickable {
                                showSettingsDialog = false
                                showBackupDialog = true
                            }
                        )
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // 关于
                        ListItem(
                            headlineContent = { Text("关于") },
                            leadingContent = { Icon(Icons.Default.Info, null) },
                            modifier = Modifier.clickable { 
                                showSettingsDialog = false
                                showAboutScreen = true
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("关闭")
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
                customPathInput = DownloadSettingsStore.getCustomPath(context) ?: ""
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
                        
                        // 使用自定义路径选项
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
                                OutlinedTextField(
                                    value = customPathInput,
                                    onValueChange = { customPathInput = it },
                                    label = { Text("自定义路径") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = useCustomPath,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colorScheme.primary,
                                        unfocusedBorderColor = colorScheme.outline,
                                        focusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        unfocusedContainerColor = colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (useCustomPath) {
                            DownloadSettingsStore.setCustomPath(context, customPathInput)
                        } else {
                            DownloadSettingsStore.setCustomPath(context, null)
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
        
        // 下载音质选择对话框
        if (showDownloadQualityDialog) {
            val qualities = listOf(128, 192, 320, 2000)
            AlertDialog(
                onDismissRequest = { showDownloadQualityDialog = false },
                title = { Text("选择下载音质") },
                text = {
                    Column {
                        qualities.forEach { quality ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedDownloadQuality = quality
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedDownloadQuality == quality,
                                    onClick = { selectedDownloadQuality = quality },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = colorScheme.primary
                                    )
                                )
                                Text(
                                    when (quality) {
                                        128 -> "128kbps (标准)"
                                        192 -> "192kbps (高清)"
                                        320 -> "320kbps (超清)"
                                        2000 -> "2000kbps (无损)"
                                        else -> "$quality kbps"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "提示：部分音质可能在部分歌曲上不生效",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        DownloadSettingsStore.setDownloadQuality(context, selectedDownloadQuality)
                        showDownloadQualityDialog = false
                    }) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadQualityDialog = false }) {
                        Text("取消")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
        
        // 播放音质选择对话框
        if (showPlayQualityDialog) {
            val qualities = listOf(128, 192, 320, 2000)
            AlertDialog(
                onDismissRequest = { showPlayQualityDialog = false },
                title = { Text("选择播放音质") },
                text = {
                    Column {
                        qualities.forEach { quality ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedPlayQuality = quality
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedPlayQuality == quality,
                                    onClick = { selectedPlayQuality = quality },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = colorScheme.primary
                                    )
                                )
                                Text(
                                    when (quality) {
                                        128 -> "128kbps (标准)"
                                        192 -> "192kbps (高清)"
                                        320 -> "320kbps (超清)"
                                        2000 -> "2000kbps (无损)"
                                        else -> "$quality kbps"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "提示：部分音质可能在部分歌曲上不生效",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        DownloadSettingsStore.setPlayQuality(context, selectedPlayQuality)
                        showPlayQualityDialog = false
                    }) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPlayQualityDialog = false }) {
                        Text("取消")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
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
                            color = Color.Gray
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
                                
                                // 应用简介
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

Text(
    "感谢祈杰のMeting-API对此项目做出的贡献",
    color = colorScheme.onSurfaceVariant,
    fontSize = 12.sp,
    textAlign = TextAlign.Center,
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(top = 4.dp)
)

                            }
                            
                            Divider(
                                color = colorScheme.outline.copy(alpha = 0.1f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
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
                            
                            // 主要开发者卡片
                            Surface(
                                onClick = {},
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
                                    // 开发者头像
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(colorScheme.primary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
        painter = painterResource(id = R.drawable.dev_icon), // 您的图片名称
        contentDescription = "开发者头像",
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
                                            "谦谦TWT",
                                            color = colorScheme.onBackground,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "主要开发者",
                                            color = colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            "miku到底是蓝的还是绿的呢",
                                            color = colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp
                                        )
                                    }
                                    
                                    // GitHub 图标
                                    IconButton(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qianqianhhh2"))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Image(
        painter = painterResource(id = R.drawable.github), // 您的图片名称
        contentDescription = "GitHub",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(24.dp)
    )
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(12.dp))
                            
                            
                            
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
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/qianqianhhh2/jianyin"))
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
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ifdian.net/a/qianqiantwt"))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0x15FFA726)
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
                                            .background(Color(0x30FFA726)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Coffee,
                                            contentDescription = null,
                                            tint = Color(0xFFFFA726),
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
                                            color = Color(0xFFFFA726),
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
                                            .background(Color(0x30FF4081)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = Color(0xFFFF4081),
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
                        
                        // 底部留白
                        item {
                            Spacer(Modifier.navigationBarsPadding().height(80.dp))
                        }
                    }
                }
            }
        }
    }
    
    BackHandler(enabled = activePlaylist != null || activeRecentPlaylist != null || showAboutScreen) {
        if (activePlaylist != null) {
            activePlaylist = null
        } else if (activeRecentPlaylist != null) {
            activeRecentPlaylist = null
        } else if (showAboutScreen) {
            showAboutScreen = false
        }
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
                                    Toast.makeText(context, "备份成功", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    backupResult = "备份失败：${e.message}"
                                    Toast.makeText(context, "备份失败", Toast.LENGTH_SHORT).show()
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
                            val lastModified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(file.lastModified()))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val restored = backupManager.restoreData(file)
                                        if (restored) {
                                            Toast.makeText(context, "恢复成功，重启应用生效！", Toast.LENGTH_SHORT).show()
                                            showBackupDialog = false
                                        } else {
                                            Toast.makeText(context, "恢复失败", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fileName, fontSize = 14.sp)
                                    Text(lastModified, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else {
                        Text("没有找到备份文件")
                    }
                    
                    if (backupResult != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(backupResult!!, fontSize = 14.sp, color = if (backupResult!!.contains("成功")) colorScheme.primary else colorScheme.error)
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
fun SectionHeaderV6(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = cs.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (onClick != null) { IconButton(onClick = onClick) { Icon(icon, null, tint = cs.primary) } }
    }
}

@Composable
fun SongItemV6(song: Song, cs: ColorScheme, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = song.pic, contentDescription = null, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(song.name, color = cs.onBackground, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = cs.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistItemV6(playlist: UserSyncedPlaylist, colorScheme: ColorScheme, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = playlist.coverPic, contentDescription = null, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(playlist.name, color = colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.songs.size} 首歌曲", color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
    }
}
