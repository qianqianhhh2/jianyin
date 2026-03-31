package com.qian.jianyin

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun MyMusicScreenV2(vm: MusicViewModel) {
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
    val appVersion = remember { "3.1.1" } // 应用版本

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

    Box(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            // 修改标题行，添加设置按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的音乐",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground,
                    modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
                )
                
                // 设置按钮
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
                val previewSongs = vm.historyList.takeLast(10).reversed()
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
                            Column(
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
                                        .background(colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    song.name,
                                    color = colorScheme.onBackground,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
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
                        Column(modifier = Modifier.width(120.dp).clickable { vm.playSong(song, favoriteSongs) }) {
                            AsyncImage(
                                model = song.pic,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).background(if (isSystemInDarkTheme()) Color(0xFF2D3748) else Color(0xFFE3EAF6)),
                                contentScale = ContentScale.Crop
                            )
                            Text(song.name, color = colorScheme.onBackground, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
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
            Spacer(Modifier.height(120.dp))
        }

        // --- 弹窗逻辑 1：添加歌单 ---
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("同步新歌单") },
                text = {
                    OutlinedTextField(
                        value = playlistIdInput,
                        onValueChange = { playlistIdInput = it },
                        label = { Text("歌单 ID") },
                        placeholder = { Text("例如：24381616") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (playlistIdInput.isBlank()) return@Button
                            scope.launch {
                                val songs = PlaylistSyncManager.fetchPlaylist(playlistIdInput, context)
                                if (songs != null) {
                                    val newList = UserSyncedPlaylist(playlistIdInput, "新歌单_${playlistIdInput}", songs.firstOrNull()?.pic ?: "", songs)
                                    PlaylistDataStore.save(context, newList)
                                    syncedPlaylists.clear()
                                    syncedPlaylists.addAll(PlaylistDataStore.getAll(context))
                                    showAddDialog = false
                                    playlistIdInput = ""
                                    Toast.makeText(context, "同步成功", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "同步失败，请检查 ID", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }) { Text("同步") }
                },
                dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
            )
        }

        // --- 弹窗逻辑 2：长按菜单 ---
        if (selectedPlaylistForMenu != null) {
        val isFavoritesPlaylist = selectedPlaylistForMenu?.id == "jianyin_favorites_playlist"
            ModalBottomSheet(
                onDismissRequest = { selectedPlaylistForMenu = null },
                containerColor = colorScheme.surfaceContainerHigh
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
                                } else {
                                    val songs = PlaylistSyncManager.fetchPlaylist(target.id, context)
                                    if (songs != null) {
                                        val updated = target.copy(songs = songs)
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
                Column(
                    modifier = Modifier.fillMaxSize().background(colorScheme.background)
                ) {
                    CenterAlignedTopAppBar(
                        title = { Text(playlist.name, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { activePlaylist = null }) {
                                Icon(Icons.Default.ArrowBack, null)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = colorScheme.background,
                            titleContentColor = colorScheme.onBackground,
                            navigationIconContentColor = colorScheme.primary
                        )
                    )

                    if (isLoadingSongs) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                            CircularProgressIndicator(color = colorScheme.primary) 
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (playlist.songs.isNotEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                                        AsyncImage(
                                            model = playlist.songs[0].pic, 
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
                                            onClick = { vm.playSong(playlist.songs[0], playlist.songs) },
                                            modifier = Modifier.padding(bottom = 20.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("播放全部")
                                        }
                                    }
                                }
                            }
                            
                            items(playlist.songs) { song ->
                                var isDownloading by remember { mutableStateOf(false) }
                                var showSongMenu by remember { mutableStateOf(false) }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { vm.playSong(song, playlist.songs) },
                                            onLongClick = { showSongMenu = true }
                                        )
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                    // 播放按钮
                                    IconButton(
                                        onClick = { vm.playSong(song, playlist.songs) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = "播放",
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
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
                                                        scope.launch {
                                                            val customPath = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomPath(context) else null
                                                            DownloadManager.downloadSong(context, song, customPath)
                                                                .onSuccess {
                                                                    Toast.makeText(context, "下载完成: ${song.name}", Toast.LENGTH_LONG).show()
                                                                }
                                                                .onFailure { e ->
                                                                    Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            isDownloading = false
                                                        }
                                                    }
                                                ) {
                                                    Text("下载")
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
                                                    Text("从歌单移除", color = colorScheme.error)
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
                            }
                            item { Spacer(Modifier.navigationBarsPadding().height(80.dp)) }
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
                Column(modifier = Modifier.fillMaxSize()) {
                    CenterAlignedTopAppBar(
                        title = { Text("最近播放", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { activeRecentPlaylist = null }) {
                                Icon(Icons.Default.ArrowBack, null)
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
                        items(activeRecentPlaylist ?: emptyList()) { song ->
                            var isDownloading by remember { mutableStateOf(false) }
                            var showSongMenu by remember { mutableStateOf(false) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { vm.playSong(song, activeRecentPlaylist ?: emptyList()) },
                                        onLongClick = { showSongMenu = true }
                                    )
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                // 播放按钮
                                IconButton(
                                    onClick = { vm.playSong(song, activeRecentPlaylist ?: emptyList()) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "播放",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
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
                                                    scope.launch {
                                                        val customPath = if (DownloadSettingsStore.isUsingCustomPath(context)) DownloadSettingsStore.getCustomPath(context) else null
                                                        DownloadManager.downloadSong(context, song, customPath)
                                                            .onSuccess {
                                                                Toast.makeText(context, "下载完成: ${song.name}", Toast.LENGTH_LONG).show()
                                                            }
                                                            .onFailure { e ->
                                                                Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        isDownloading = false
                                                    }
                                                }
                                            ) {
                                                Text("下载")
                                            }
                                        },
                                        dismissButton = {
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
                        }
                        item { Spacer(Modifier.navigationBarsPadding().height(80.dp)) }
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
                }
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
                                    selectedColor = Color.Black
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
                                    selectedColor = Color.Black
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
                }
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
                                        selectedColor = Color.Black
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
                }
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
                                        selectedColor = Color.Black
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
                }
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
            }
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
