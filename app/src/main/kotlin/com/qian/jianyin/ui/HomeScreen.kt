package com.qian.jianyin

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.qian.jianyin.netease.NeteasePlaylistResult
import com.qian.jianyin.netease.NeteaseSongSearchResult

private fun getRandomPlaceholderId(): Int {
    val ids = listOf(
        R.drawable.miku_1, R.drawable.miku_2, R.drawable.miku_3,
        R.drawable.miku_4, R.drawable.miku_5, R.drawable.miku_6,
        R.drawable.miku_7, R.drawable.miku_8, R.drawable.miku_9
    )
    return ids.random()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun SectionLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: MusicViewModel,
    innerPadding: PaddingValues
) {
    val context = LocalContext.current
    val homeVm: HomeScreenViewModel = viewModel()
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var activePlaylistId by remember { mutableStateOf<String?>(null) }
    var activePlaylistName by remember { mutableStateOf("") }
    val playlistSongs = remember { mutableStateListOf<Song>() }
    var isDetailLoading by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<Int>()) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var createPlaylistName by remember { mutableStateOf("") }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )
    val gridState = rememberLazyGridState()

    fun openPlaylist(id: String, name: String) {
        activePlaylistId = id
        activePlaylistName = name
        playlistSongs.clear()
        scope.launch {
            isDetailLoading = true
            val result = PlaylistSyncManager.fetchPlaylist(id, context)
            if (result != null) playlistSongs.addAll(result)
            isDetailLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            LargeTopAppBar(
                title = { Text("简音", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { homeVm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新推荐")
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

            LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(
                        start = 4.dp, end = 4.dp,
                        top = 8.dp, bottom = innerPadding.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ── 今日推荐 ──
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            icon = Icons.Outlined.LibraryMusic,
                            title = "今日推荐"
                        )
                    }
                    when {
                        homeVm.uiState.radarSongs.loading -> {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionLoading() }
                        }
                        homeVm.uiState.radarSongs.error != null -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    homeVm.uiState.radarSongs.error ?: "加载失败",
                                    color = colorScheme.error,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                        homeVm.uiState.radarSongs.items.isNotEmpty() -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                TodayRecommendShelf(
                                    songs = homeVm.uiState.radarSongs.items.take(8),
                                    onClick = { song ->
                                        val s = song.toSong()
                                        vm.playNeteaseSong(s, listOf(s))
                                    }
                                )
                            }
                        }
                    }

                    // ── 热歌推荐 ──
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            icon = Icons.Outlined.LocalFireDepartment,
                            title = "热歌推荐"
                        )
                    }
                    when {
                        homeVm.uiState.hotSongs.loading -> {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionLoading() }
                        }
                        homeVm.uiState.hotSongs.error != null -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "加载失败",
                                    color = colorScheme.error,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                        homeVm.uiState.hotSongs.items.isNotEmpty() -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SongShelf(
                                    songs = homeVm.uiState.hotSongs.items,
                                    onClick = { song ->
                                        val s = song.toSong()
                                        vm.playNeteaseSong(s, listOf(s))
                                    }
                                )
                            }
                        }
                    }

                    // ── 个性化推荐 ──
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            icon = Icons.Outlined.Star,
                            title = "个性化推荐"
                        )
                    }
                    when {
                        homeVm.uiState.recommendedPlaylists.loading -> {
                            item(span = { GridItemSpan(maxLineSpan) }) { SectionLoading() }
                        }
                        homeVm.uiState.recommendedPlaylists.error != null -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    homeVm.uiState.recommendedPlaylists.error ?: "加载失败",
                                    color = colorScheme.error,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                        else -> {
                            items(
                                items = homeVm.uiState.recommendedPlaylists.items,
                                key = { it.id }
                            ) { pl ->
                                PlaylistCard(
                                    playlist = pl,
                                    onClick = { openPlaylist(pl.id, pl.name) }
                                )
                            }
                        }
                    }

                }
        }

        // ── 歌单详情页 ──
        AnimatedContent(
            targetState = activePlaylistId,
            transitionSpec = {
                scaleIn(initialScale = 0.1f) + fadeIn() togetherWith
                        scaleOut(targetScale = 0.1f) + fadeOut()
            }
        ) { targetId ->
            targetId?.let {
                // 计算当前播放歌曲在歌单中的索引
                val currentPlayingIndex = vm.currentSong.value?.let { currentSong ->
                    playlistSongs.indexOfFirst { song ->
                        (song.id.isNotBlank() && song.id == currentSong.id) ||
                        (song.url.isNotBlank() && song.url == currentSong.url)
                    }.takeIf { it >= 0 }
                }
                PlaylistDetailPage(
                    playlistName = activePlaylistName,
                    songs = playlistSongs,
                    isLoading = isDetailLoading,
                    isSelectionMode = isSelectionMode,
                    selectedSongs = selectedSongs,
                    showAddToPlaylistDialog = showAddToPlaylistDialog,
                    showCreatePlaylistDialog = showCreatePlaylistDialog,
                    createPlaylistName = createPlaylistName,
                    currentPlayingIndex = currentPlayingIndex,
                    currentSong = vm.currentSong.value,
                    onBack = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedSongs = emptySet()
                        } else if (vm.isPlayerSheetVisible.value) {
                            vm.isPlayerSheetVisible.value = false
                        } else {
                            activePlaylistId = null
                        }
                    },
                    onPlayAll = {
                        if (playlistSongs.isNotEmpty()) {
                            vm.playNeteaseSong(playlistSongs[0], playlistSongs.toList())
                        }
                    },
                    onSongClick = { song, index ->
                        if (isSelectionMode) {
                            selectedSongs = if (index in selectedSongs)
                                selectedSongs - index else selectedSongs + index
                        } else {
                            vm.playNeteaseSong(song, playlistSongs.toList())
                        }
                    },
                    onSongLongClick = { index ->
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            selectedSongs = setOf(index)
                        }
                    },
                    onAddToPlaylist = { showAddToPlaylistDialog = true },
                    onDownload = {
                        val songsToDownload = selectedSongs.mapNotNull { playlistSongs.getOrNull(it) }
                        if (songsToDownload.isNotEmpty()) {
                            scope.launch {
                                val customUri = if (DownloadSettingsStore.isUsingCustomPath(context)) {
                                    DownloadSettingsStore.getCustomUri(context)
                                } else null
                                DownloadStateManager.startDownload(songsToDownload.size)
                                DownloadManager.downloadSongs(
                                    context, songsToDownload, customUri
                                ) { index, total, songName, progress ->
                                    DownloadStateManager.updateCurrentSong(index, songName)
                                    DownloadStateManager.updateProgress(progress)
                                }.onSuccess { results ->
                                    val successCount = results.count { it.startsWith("下载完成") }
                                    Toast.makeText(
                                        context,
                                        "下载完成：成功 $successCount 首",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    DownloadStateManager.downloadComplete()
                                    isSelectionMode = false
                                    selectedSongs = emptySet()
                                }.onFailure { e ->
                                    Toast.makeText(
                                        context,
                                        "下载失败：${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    DownloadStateManager.downloadFailed(e.message ?: "未知错误")
                                }
                            }
                        }
                    },
                    onCreatePlaylistName = { createPlaylistName = it },
                    onConfirmCreatePlaylist = {
                        if (createPlaylistName.isNotBlank()) {
                            val newPlaylist = PlaylistDataStore.createPlaylist(
                                context, createPlaylistName.trim()
                            )
                            val songsToAdd =
                                selectedSongs.mapNotNull { playlistSongs.getOrNull(it) }
                            songsToAdd.forEach { s ->
                                PlaylistDataStore.addSongToPlaylist(
                                    context, newPlaylist.id, s
                                )
                            }
                            showCreatePlaylistDialog = false
                            isSelectionMode = false
                            selectedSongs = emptySet()
                            Toast.makeText(
                                context,
                                "已创建歌单并添加 ${songsToAdd.size} 首歌曲",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onDismissAddDialog = { showAddToPlaylistDialog = false },
                    onDismissCreateDialog = {
                        showCreatePlaylistDialog = false
                        createPlaylistName = ""
                    },
                    onDismissCreateDialogCancel = {
                        showCreatePlaylistDialog = false
                        createPlaylistName = ""
                    }
                )
            }
        }
    }
}

// ── Today Recommend Shelf (今日推荐) ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayRecommendShelf(
    songs: List<NeteaseSongSearchResult>,
    onClick: (NeteaseSongSearchResult) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            val itemHazeState = remember { HazeState() }
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .clickable { onClick(song) }
            ) {
                AsyncImage(
                    model = song.picUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .hazeSource(itemHazeState),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = getRandomPlaceholderId())
                )
                Box(
                    modifier = Modifier
                        .size(110.dp, 38.dp)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = 12.dp,
                                bottomEnd = 12.dp
                            )
                        )
                        .hazeEffect(
                            itemHazeState,
                            HazeStyle(
                                blurRadius = 8.dp,
                                tint = HazeTint(Color.Black.copy(alpha = 0.25f))
                            )
                        )
                        .align(Alignment.BottomStart)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .padding(bottom = 2.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            song.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artist,
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ── Song Horizontal Shelf (热歌推荐) ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongShelf(
    songs: List<NeteaseSongSearchResult>,
    onClick: (NeteaseSongSearchResult) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            val itemHazeState = remember { HazeState() }
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .clickable { onClick(song) }
            ) {
                AsyncImage(
                    model = song.picUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .hazeSource(itemHazeState),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = getRandomPlaceholderId())
                )
                Box(
                    modifier = Modifier
                        .size(120.dp, 36.dp)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = 14.dp,
                                bottomEnd = 14.dp
                            )
                        )
                        .hazeEffect(
                            itemHazeState,
                            HazeStyle(
                                blurRadius = 8.dp,
                                tint = HazeTint(Color.Black.copy(alpha = 0.25f))
                            )
                        )
                        .align(Alignment.BottomStart)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .padding(bottom = 2.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            song.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artist,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ── Playlist Card ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCard(
    playlist: NeteasePlaylistResult,
    onClick: () -> Unit
) {
    val hazeState = remember { HazeState() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = playlist.picUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .hazeSource(hazeState),
            contentScale = ContentScale.Crop,
            error = painterResource(id = getRandomPlaceholderId())
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(
                    RoundedCornerShape(
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .hazeEffect(
                    hazeState,
                    HazeStyle(
                        blurRadius = 8.dp,
                        tint = HazeTint(Color.Black.copy(alpha = 0.25f))
                    )
                )
                .align(Alignment.BottomStart)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    playlist.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${playlist.trackCount} 首",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ── Rank Card ──
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RankCard(
    name: String,
    subTitle: String,
    coverUrl: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(id = getRandomPlaceholderId())
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subTitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Playlist Detail Page ──
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PlaylistDetailPage(
    playlistName: String,
    songs: List<Song>,
    isLoading: Boolean,
    isSelectionMode: Boolean,
    selectedSongs: Set<Int>,
    showAddToPlaylistDialog: Boolean,
    showCreatePlaylistDialog: Boolean,
    createPlaylistName: String,
    currentPlayingIndex: Int?,
    currentSong: Song?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onSongClick: (Song, Int) -> Unit,
    onSongLongClick: (Int) -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onCreatePlaylistName: (String) -> Unit,
    onConfirmCreatePlaylist: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onDismissCreateDialog: () -> Unit,
    onDismissCreateDialogCancel: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    var showSearchBox by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val filteredSongs = if (searchQuery.isNotEmpty()) {
        songs.filter { song ->
            song.name.contains(searchQuery, ignoreCase = true) ||
            song.artist.contains(searchQuery, ignoreCase = true)
        }
    } else {
        songs
    }

    val listState = rememberLazyListState()

    // 自动滚动到当前播放歌曲
    LaunchedEffect(currentPlayingIndex) {
        currentPlayingIndex?.let { index ->
            if (index >= 0) {
                // 延迟一点滚动，等待列表渲染完成
                kotlinx.coroutines.delay(100)
                listState.animateScrollToItem(index.coerceIn(0, (filteredSongs.size - 1).coerceAtLeast(0)))
            }
        }
    }

    BackHandler {
        if (showSearchBox) {
            showSearchBox = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(colorScheme.background)
    ) {
        CenterAlignedTopAppBar(
            title = {
                if (isSelectionMode) Text("已选择 ${selectedSongs.size} 首")
                else Text(
                    playlistName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.66f)
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            },
            actions = {
                if (isSelectionMode && selectedSongs.isNotEmpty()) {
                    IconButton(onClick = onAddToPlaylist) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = colorScheme.primary)
                    }
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Filled.Download, null, tint = colorScheme.primary)
                    }
                } else {
                    IconButton(onClick = { showSearchBox = !showSearchBox }) {
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

        AnimatedVisibility(
            visible = showSearchBox,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索歌曲", color = colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null, tint = colorScheme.onSurfaceVariant)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline,
                    focusedContainerColor = colorScheme.surface,
                    unfocusedContainerColor = colorScheme.surface
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { focusManager.clearFocus() }
                ),
                singleLine = true
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colorScheme.primary)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                if (songs.isNotEmpty()) {
                    item {
                        val hazeState = remember { HazeState() }
                        Box(Modifier.fillMaxWidth()) {
                            AsyncImage(
                                model = songs[0].pic,
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
                                    playlistName,
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
                                    onClick = onPlayAll,
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

                itemsIndexed(filteredSongs) { index, song ->
                    Column {
                        val isSelected = index in selectedSongs
                        val isCurrentlyPlaying = remember(currentSong) {
                            currentSong?.let { current ->
                                (song.id.isNotBlank() && song.id == current.id) ||
                                (song.url.isNotBlank() && song.url == current.url)
                            } ?: false
                        }
                        val playingLineHeight by animateDpAsState(
                            targetValue = if (isCurrentlyPlaying) 24.dp else 0.dp,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                            label = "playingLineHeight"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isSelected) colorScheme.primary.copy(alpha = 0.1f)
                                    else if (isCurrentlyPlaying) colorScheme.primary.copy(alpha = 0.05f)
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = { onSongClick(song, index) },
                                    onLongClick = { onSongLongClick(index) }
                                )
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
                            if (isSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) colorScheme.primary
                                            else colorScheme.surfaceVariant
                                        )
                                        .padding(2.dp)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
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
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp)),
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
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        song.artist,
                                        color = colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
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
                            if (!isSelectionMode) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_play),
                                    contentDescription = "播放",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        if (index < songs.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(160.dp)) }
            }
        }
    }

    if (showAddToPlaylistDialog) {
        val otherPlaylists = PlaylistDataStore.getAll(context).filter {
            !it.id.startsWith("local_") || it.isLocalPlaylist
        }
        AlertDialog(
            onDismissRequest = onDismissAddDialog,
            title = { Text("添加到歌单") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (otherPlaylists.isEmpty()) {
                        Text("没有其他歌单可添加", modifier = Modifier.padding(vertical = 16.dp))
                    } else {
                        otherPlaylists.forEach { target ->
                            ListItem(
                                headlineContent = { Text(target.name) },
                                supportingContent = { Text("${target.songs.size} 首歌曲") },
                                leadingContent = {
                                    AsyncImage(
                                        model = target.coverPic,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop,
                                        error = painterResource(id = getRandomPlaceholderId())
                                    )
                                },
                                modifier = Modifier.clickable {
                                    val songsToAdd =
                                        selectedSongs.mapNotNull { songs.getOrNull(it) }
                                    var addedCount = 0
                                    songsToAdd.forEach { s ->
                                        if (PlaylistDataStore.addSongToPlaylist(
                                                context, target.id, s
                                            )
                                        ) addedCount++
                                    }
                                    onDismissAddDialog()
                                    Toast.makeText(
                                        context,
                                        "已添加 $addedCount 首到 ${target.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                            onDismissAddDialog()
                            onCreatePlaylistName("")
                            onDismissCreateDialogCancel()
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissAddDialog) { Text("取消") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = onDismissCreateDialogCancel,
            title = { Text("创建新歌单") },
            text = {
                OutlinedTextField(
                    value = createPlaylistName,
                    onValueChange = onCreatePlaylistName,
                    label = { Text("歌单名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmCreatePlaylist,
                    enabled = createPlaylistName.isNotBlank()
                ) { Text("创建并添加") }
            },
            dismissButton = {
                TextButton(onClick = onDismissCreateDialogCancel) { Text("取消") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
