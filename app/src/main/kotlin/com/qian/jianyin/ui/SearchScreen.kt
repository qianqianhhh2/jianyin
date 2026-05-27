package com.qian.jianyin

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qian.jianyin.R
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch

private fun getRandomPlaceholderId(): Int {
    val ids = listOf(R.drawable.miku_1, R.drawable.miku_2, R.drawable.miku_3, R.drawable.miku_4, R.drawable.miku_5, R.drawable.miku_6, R.drawable.miku_7, R.drawable.miku_8, R.drawable.miku_9)
    return ids.random()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: MusicViewModel,
    innerPadding: PaddingValues,
    isDarkMode: Boolean = isSystemInDarkTheme()
) {
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<Int>()) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var createPlaylistName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 页面背景适配动态色
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            // 1. 搜索框：适配 M3 OutlinedTextField 风格
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp), // 减小底部padding，缩小与搜索历史的间隙
                placeholder = { Text("搜索音乐/歌手", color = colorScheme.onSurfaceVariant) },
                shape = RoundedCornerShape(28.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { vm.executeSearch(searchText) }) {
                            Icon(Icons.Default.Send, null, tint = colorScheme.primary)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE3EAF6),
                    unfocusedContainerColor = if (isDarkMode) Color(0xFF2D3748).copy(alpha = 0.8f) else Color(0xFFE3EAF6).copy(alpha = 0.8f),
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outline,
                    focusedTextColor = colorScheme.onSurface,
                    unfocusedTextColor = colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        vm.executeSearch(searchText)
                        focusManager.clearFocus()
                    }
                )
            )

            // 2. 内容区域逻辑判断
            if (searchText.isEmpty() && vm.searchResults.isEmpty()) {
                // 搜索历史与推荐
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = innerPadding
                ) {
                    // 搜索历史部分
                    if (vm.searchHistory.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "搜索历史",
                                    color = colorScheme.onBackground,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { 
                                    // 根据 ViewModel 逻辑清空历史
                                    vm.searchHistory.clear() 
                                    // 保存清空后的状态到SharedPreferences
                                    vm.saveSearchHistory()
                                }) {
                                    Text("清空", color = colorScheme.primary, fontSize = 12.sp)
                                }
                            }
                            
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                vm.searchHistory.forEach { history ->
                                    SuggestionTagV2(history, colorScheme, isDarkMode) {
                                        searchText = it
                                        vm.executeSearch(it)
                                    }
                                }
                            }
                        }
                    }

                    // 推荐搜索部分
                    item {
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "推荐搜索",
                            color = colorScheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            vm.recommendedSearches.forEach { tag ->
                                SuggestionTagV2(tag, colorScheme, isDarkMode) {
                                    searchText = it
                                    vm.executeSearch(it)
                                }
                            }
                        }
                    }
                }
            } else {
                // 3. 搜索结果展示
                if (vm.isSearching.value) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colorScheme.primary)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isSelectionMode) {
                            CenterAlignedTopAppBar(
                                title = { Text("已选择 ${selectedSongs.size} 首", fontSize = 18.sp) },
                                navigationIcon = {
                                    IconButton(onClick = {
                                        isSelectionMode = false
                                        selectedSongs = emptySet()
                                    }) {
                                        Icon(Icons.Default.Close, null)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = {
                                        val songsToDownload = selectedSongs.mapNotNull { idx ->
                                            vm.searchResults.getOrNull(idx)
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
                                    IconButton(onClick = { showAddToPlaylistDialog = true }) {
                                        Icon(Icons.Default.PlaylistAdd, null, tint = colorScheme.primary)
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = colorScheme.background,
                                    titleContentColor = colorScheme.onBackground,
                                    navigationIconContentColor = colorScheme.primary
                                )
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = innerPadding
                        ) {
                            itemsIndexed(vm.searchResults) { index, song ->
                                val isSelected = selectedSongs.contains(index)
                                Column(
                                    modifier = Modifier
                                        .background(if (isSelected) colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    selectedSongs = if (isSelected) selectedSongs - index else selectedSongs + index
                                                } else {
                                                    vm.playSong(song, vm.searchResults)
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) {
                                                    isSelectionMode = true
                                                    selectedSongs = setOf(index)
                                                }
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                                                .size(if (isSelectionMode) 48.dp else 56.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE3EAF6)),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(id = getRandomPlaceholderId())
                                        )
                                        Column(Modifier.padding(start = if (isSelectionMode) 12.dp else 16.dp).weight(1f)) {
                                            Text(song.name, color = colorScheme.onBackground, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(song.artist, color = colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1)
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = colorScheme.outlineVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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
                                        vm.searchResults.getOrNull(idx)
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
                        headlineContent = { Text("添加到当前播放列表", color = colorScheme.primary) },
                        leadingContent = {
                            Icon(Icons.Default.QueuePlayNext, null, tint = colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            val songsToAdd = selectedSongs.mapNotNull { idx ->
                                vm.searchResults.getOrNull(idx)
                            }
                            songsToAdd.forEach { song ->
                                vm.addNextToQueue(song)
                            }
                            showAddToPlaylistDialog = false
                            isSelectionMode = false
                            selectedSongs = emptySet()
                            Toast.makeText(context, "已添加 ${songsToAdd.size} 首歌曲到播放队列", Toast.LENGTH_SHORT).show()
                        }
                    )
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
                                vm.searchResults.getOrNull(idx)
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

@Composable
fun SuggestionTagV2(text: String, cs: ColorScheme, isDarkMode: Boolean, onClick: (String) -> Unit) {
    Surface(
        onClick = { onClick(text) },
        shape = RoundedCornerShape(16.dp),
        color = if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE3EAF6),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 13.sp,
            color = cs.onSurfaceVariant
        )
    }
}

@Composable
fun SongItemViewV2(song: Song, cs: ColorScheme, isDarkMode: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.pic,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDarkMode) Color(0xFF2D3748) else Color(0xFFE3EAF6)),
                contentScale = ContentScale.Crop,
                error = painterResource(id = getRandomPlaceholderId())
            )
            
            Column(
                Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
                Text(
                    text = song.name,
                    color = cs.onSurface, // 主要文字
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    color = cs.onSurfaceVariant, // 次要文字
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 0.5.dp,
            color = cs.outlineVariant // 动态分割线颜色
        )
    }
}
