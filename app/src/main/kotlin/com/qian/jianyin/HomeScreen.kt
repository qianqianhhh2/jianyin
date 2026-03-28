package com.qian.jianyin

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.isSystemInDarkTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    vm: MusicViewModel,
    homeVm: HomeScreenViewModel = viewModel()
) {
    // 获取 MD3 动态配色方案
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    
    var activePlaylist by remember { mutableStateOf<HomePlaylist?>(null) }
    val playlistSongs = remember { mutableStateListOf<Song>() }
    var isDetailLoading by remember { mutableStateOf(false) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(colorScheme.background)
    ) {
        
        // 主页内容：参照 MyMusicScreenV2 的状态栏预留方式
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars) // 自动预留状态栏高度，无硬编码
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "简音", 
                fontSize = 32.sp, 
                fontWeight = FontWeight.Black, 
                color = colorScheme.onBackground, 
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp) // 仅保留少量间距增加呼吸感
            )

            // 1. 推荐歌单
            Text("精选推荐", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colorScheme.onBackground)
            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(animationSpec = tween(durationMillis = 500, delayMillis = 100))
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(homeVm.recommendedPlaylists) { item ->
                    val coverUrl = homeVm.coverMap[item.playlistId]
                    Column(modifier = Modifier.width(150.dp).clickable {
                        activePlaylist = item
                        scope.launch {
                            isDetailLoading = true
                            val result = PlaylistSyncManager.fetchPlaylist(item.playlistId)
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
                                .background(if (isSystemInDarkTheme()) Color(0xFF2D3748) else Color(0xFFE3EAF6)), 
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            item.name, 
                            color = colorScheme.onBackground, 
                            fontSize = 14.sp, 
                            modifier = Modifier.padding(top = 8.dp), 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            item.subTitle, 
                            color = colorScheme.onSurfaceVariant, 
                            fontSize = 12.sp, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                }
            }

            Spacer(Modifier.height(36.dp))

            // 2. 排行榜
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSystemInDarkTheme()) Color(0xFF2D3748) else Color(0xFFE3EAF6))
                        .clickable {
                            activePlaylist = rank
                            scope.launch {
                                isDetailLoading = true
                                val result = PlaylistSyncManager.fetchPlaylist(rank.playlistId)
                                playlistSongs.clear()
                                if (result != null) playlistSongs.addAll(result)
                                isDetailLoading = false
                            }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = rankCover, 
                                contentDescription = null, 
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(86.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorScheme.surface), 
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.padding(end = 12.dp).weight(1f)) {
                                Text(rank.name, color = colorScheme.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
            Spacer(Modifier.height(120.dp))
        }

        // 详情页
        // 详情页
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
            // 顶部应用栏
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

            // 加载状态或内容
            if (isDetailLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
                    CircularProgressIndicator(color = colorScheme.primary) 
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // 顶部区域：封面图、间隙、描述文字和播放按钮
                    if (playlistSongs.isNotEmpty()) {
                        // 封面图
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = playlistSongs[0].pic, 
                                    contentDescription = null, 
                                    modifier = Modifier
                                        .size(240.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(colorScheme.surfaceVariant), 
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        
                        // 封面图与描述文字之间的间隙
                        item {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        
                        // 描述文字与播放按钮
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // 描述文字
                                    Text(
                                        text = playlist.subTitle,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .padding(horizontal = 40.dp)
                                    )
                                    // 描述文字与播放按钮之间的间隙
                                    Spacer(modifier = Modifier.height(16.dp))
                                    // 播放全部按钮
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
                    items(playlistSongs) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.playSong(song, playlistSongs) }
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
                            Icon(
                                Icons.Default.PlayArrow, 
                                null, 
                                tint = colorScheme.primary.copy(alpha = 0.5f), 
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    // 列表底部留白
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        } // 结束 Column
    } // 结束 activePlaylist?.let
} // 结束 AnimatedVisibility


    BackHandler(enabled = activePlaylist != null) { activePlaylist = null }
}
}