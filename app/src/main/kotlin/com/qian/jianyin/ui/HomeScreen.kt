package com.qian.jianyin

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.qian.jianyin.R
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

private fun getRandomPlaceholderId(): Int {
    val ids = listOf(R.drawable.miku_1, R.drawable.miku_2, R.drawable.miku_3, R.drawable.miku_4, R.drawable.miku_5, R.drawable.miku_6, R.drawable.miku_7, R.drawable.miku_8, R.drawable.miku_9)
    return ids.random()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalHazeApi::class)
@Composable
fun HomeScreen(
    vm: MusicViewModel,
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    isDarkMode: Boolean = isSystemInDarkTheme()
) {
    val context = LocalContext.current
    val homeVm: HomeScreenViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return HomeScreenViewModel(context) as T
        }
    })

    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    
    var activePlaylist by remember { mutableStateOf<HomePlaylist?>(null) }
    val playlistSongs = remember { mutableStateListOf<Song>() }
    var isDetailLoading by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<Int>()) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var createPlaylistName by remember { mutableStateOf("") }
    var showSongMenu by remember { mutableStateOf(false) }
    var currentMenuSong by remember { mutableStateOf<Song?>(null) }

    // 创建歌单详情页的 HazeState
    val detailHazeState = remember { HazeState() }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(colorScheme.background)
    ) {
        

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars) // 自动预留状态栏高度，无硬编码
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            Spacer(modifier = Modifier.height(64.dp))
            Text(
                text = "简音", 
                fontSize = 32.sp, 
                fontWeight = FontWeight.Black, 
                color = colorScheme.onBackground, 
                modifier = Modifier.padding(bottom = 28.dp) 
            )

            // 推荐歌单
            Text("精选推荐", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 100))
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(homeVm.recommendedPlaylists) { item ->
                    val coverUrl = homeVm.coverMap[item.playlistId]
                    val itemHazeState = remember { HazeState() }
                    Box(modifier = Modifier.width(150.dp).clickable {
                        activePlaylist = item
                        scope.launch {
                            isDetailLoading = true
                            val result = PlaylistSyncManager.fetchPlaylist(item.playlistId, context)
                            playlistSongs.clear()
                            if (result != null) playlistSongs.addAll(result)
                            isDetailLoading = false
                        }
                    }) {
                        AsyncImage(
                            model = coverUrl, 
                            contentDescription = null, 
                            modifier = Modifier
                                .size(150.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE3EAF6))
                                .hazeSource(itemHazeState), 
                            contentScale = ContentScale.Crop,
                            error = painterResource(id = getRandomPlaceholderId())
                        )
                        Box(
                            modifier = Modifier
                                .size(150.dp, 45.dp)
                                .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
                                .hazeEffect(itemHazeState, HazeStyle(blurRadius = 10.dp, tint = HazeTint(Color.Black.copy(alpha = 0.2f))))
                                .align(Alignment.BottomStart)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                                    .padding(bottom = 6.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    item.name, 
                                    color = Color.White, 
                                    fontSize = 11.sp, 
                                    fontWeight = FontWeight.Medium,
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

            Spacer(Modifier.height(36.dp))

            // 排行榜
            Text("排行榜", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 200))
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(homeVm.topLists) { rank ->
                    val rankCover = homeVm.coverMap[rank.playlistId]
                    Box(modifier = Modifier
                        .width(280.dp)
                        .height(110.dp)
                        .clickable {
                            activePlaylist = rank
                            scope.launch {
                                isDetailLoading = true
                                val result = PlaylistSyncManager.fetchPlaylist(rank.playlistId, context)
                                playlistSongs.clear()
                                if (result != null) playlistSongs.addAll(result)
                                isDetailLoading = false
                            }
                        }
                    ) {
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(x = 8.dp, y = 8.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isDarkMode) 
                                        Color(0xFF171923)
                                    else 
                                        Color(0xFFCBD5E0)
                                )
                        )
                        
                    
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(x = 4.dp, y = 4.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isDarkMode) 
                                        Color(0xFF1A202C)
                                    else 
                                        Color(0xFFE2E8F0)
                                )
                        )
                        
                       
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isDarkMode) 
                                        Color(0xFF2D3748)
                                    else 
                                        Color(0xFFF7FAFC)
                                )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = rankCover, 
                                    contentDescription = null, 
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(86.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isDarkMode) 
                                                Color(0xFF4A5568)
                                            else 
                                                Color(0xFFFFFEFE)
                                        ), 
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = getRandomPlaceholderId())
                                )
                                Column(modifier = Modifier.padding(end = 12.dp).weight(1f)) {
                                    Text(
                                        rank.name, 
                                        color = colorScheme.onBackground, 
                                        fontSize = 17.sp, 
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        rank.subTitle, 
                                        color = colorScheme.onSurfaceVariant, 
                                        fontSize = 11.sp, 
                                        maxLines = 2, 
                                        overflow = TextOverflow.Ellipsis, 
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
            Spacer(Modifier.height(120.dp))
        }

        // 详情页
AnimatedContent(
    targetState = activePlaylist,
    transitionSpec = {
        scaleIn(initialScale = 0.1f) + fadeIn() with
        scaleOut(targetScale = 0.1f) + fadeOut()
    }
) {
    it?.let { playlist ->
        Column(
            modifier = Modifier.fillMaxSize().background(colorScheme.background)
        ) {
            // 处理返回键
            BackHandler {
                if (vm.isPlayerSheetVisible.value) {
                    vm.isPlayerSheetVisible.value = false
                } else {
                    activePlaylist = null
                }
            }
            
            CenterAlignedTopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("已选择 ${selectedSongs.size} 首", fontSize = 18.sp)
                    } else {
                        Text(playlist.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedSongs = emptySet()
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
                        IconButton(onClick = { showAddToPlaylistDialog = true }) {
                            Icon(Icons.Default.PlaylistAdd, null, tint = colorScheme.primary)
                        }
                        IconButton(onClick = {
                            val songsToDownload = selectedSongs.mapNotNull { idx ->
                                playlistSongs.getOrNull(idx)
                            }
                            if (songsToDownload.isNotEmpty()) {
                                scope.launch {
                                    val customUri = if (DownloadSettingsStore.isUsingCustomPath(context)) {
                                        DownloadSettingsStore.getCustomUri(context)
                                    } else null
                                    DownloadStateManager.startDownload(songsToDownload.size)
                                    DownloadManager.downloadSongs(context, songsToDownload, customUri) { index, total, songName, progress ->
                                        DownloadStateManager.updateCurrentSong(index, songName)
                                        DownloadStateManager.updateProgress(progress)
                                    }.onSuccess { results ->
                                        val successCount = results.count { it.startsWith("下载完成") }
                                        val failCount = results.size - successCount
                                        Toast.makeText(context, "下载完成：成功 $successCount 首，失败 $failCount 首", Toast.LENGTH_LONG).show()
                                        DownloadStateManager.downloadComplete()
                                        isSelectionMode = false
                                        selectedSongs = emptySet()
                                    }.onFailure { e ->
                                        Toast.makeText(context, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
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

            if (isDetailLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                    CircularProgressIndicator(color = colorScheme.primary) 
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (playlistSongs.isNotEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = playlistSongs[0].pic, 
                                    contentDescription = null, 
                                    modifier = Modifier
                                        .size(240.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(colorScheme.surfaceVariant), 
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = getRandomPlaceholderId())
                                )
                            }
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = playlist.subTitle,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .padding(horizontal = 40.dp)
                                    )
 
                                    Spacer(modifier = Modifier.height(16.dp))

                                    FilledTonalButton(
                                        onClick = { vm.playSong(playlistSongs[0], playlistSongs) },
                                        modifier = Modifier.padding(bottom = 20.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = colorScheme.secondaryContainer,
                                            contentColor = colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("播放全部")
                                    }
                                }
                            }
                        }
                    }
                    
                    // 歌曲列表
                    itemsIndexed(playlistSongs) { index, song ->
                        Column {
                            val isSelected = selectedSongs.contains(index)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedSongs = if (isSelected) selectedSongs - index else selectedSongs + index
                                            } else {
                                                vm.playSong(song, playlistSongs)
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedSongs = setOf(index)
                                            }
                                        }
                                    )
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectionMode) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) colorScheme.primary else colorScheme.surfaceVariant)
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
                                Column(Modifier.padding(start = if (isSelectionMode) 12.dp else 16.dp).weight(1f)) {
                                    Text(song.name, color = colorScheme.onBackground, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(song.artist, color = colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                                }
                                if (!isSelectionMode) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_play),
                                        contentDescription = "播放",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            if (index < playlistSongs.size - 1) {
                                Divider(
                                    modifier = Modifier
                                        .padding(horizontal = 20.dp)
                                        .fillMaxWidth(),
                                    color = Color.Gray.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.navigationBarsPadding().height(160.dp)) }
                }
            }
        } // 结束 Column
    } // 结束 activePlaylist?.let
} // 结束 AnimatedVisibility

        if (showAddToPlaylistDialog) {
            val otherPlaylists = PlaylistDataStore.getAll(context).filter {
                !it.id.startsWith("local_") || it.isLocalPlaylist
            }
            AlertDialog(
                onDismissRequest = { showAddToPlaylistDialog = false },
                title = { Text("添加到歌单") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        if (otherPlaylists.isEmpty()) {
                            Text("没有其他歌单可添加", modifier = Modifier.padding(vertical = 16.dp))
                        } else {
                            otherPlaylists.forEach { targetPlaylist ->
                                ListItem(
                                    headlineContent = { Text(targetPlaylist.name) },
                                    supportingContent = { Text("${targetPlaylist.songs.size} 首歌曲") },
                                    leadingContent = {
                                        AsyncImage(
                                            model = targetPlaylist.coverPic,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colorScheme.surfaceVariant),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = getRandomPlaceholderId())
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        val songsToAdd = selectedSongs.mapNotNull { idx ->
                                            playlistSongs.getOrNull(idx)
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
                                val songsToAdd = selectedSongs.mapNotNull { idx ->
                                    playlistSongs.getOrNull(idx)
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
    }
}