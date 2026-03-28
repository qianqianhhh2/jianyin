package com.qian.jianyin

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import coil.compose.AsyncImage
import androidx.compose.foundation.isSystemInDarkTheme

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: MusicViewModel) {
    var searchText by remember { mutableStateOf("") }
    val colorScheme = MaterialTheme.colorScheme

    // 页面背景适配动态色
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // 1. 搜索框：适配 M3 OutlinedTextField 风格
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                focusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF2D3748) else Color(0xFFE3EAF6),
                unfocusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF2D3748).copy(alpha = 0.8f) else Color(0xFFE3EAF6).copy(alpha = 0.8f),
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.outline,
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface,
            )
        )

        // 2. 内容区域逻辑判断
        if (searchText.isEmpty() && vm.searchResults.isEmpty()) {
            // 搜索历史与推荐
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
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
                            }) {
                                Text("清空", color = colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            vm.searchHistory.forEach { history ->
                                SuggestionTagV2(history, colorScheme) {
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
                            SuggestionTagV2(tag, colorScheme) {
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(vm.searchResults) { song ->
                        SongItemViewV2(song, colorScheme) {
                            vm.playSong(song, vm.searchResults)
                        }
                    }
                    // 关键：适配底部导航栏和手势指示条
                    item { Spacer(Modifier.navigationBarsPadding().height(86.dp)) }
                }
            }
        }
    }
}

@Composable
fun SuggestionTagV2(text: String, cs: ColorScheme, onClick: (String) -> Unit) {
    Surface(
        onClick = { onClick(text) },
        shape = RoundedCornerShape(16.dp),
        color = if (isSystemInDarkTheme()) Color(0xFF2D3748) else Color(0xFFE3EAF6),
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
fun SongItemViewV2(song: Song, cs: ColorScheme, onClick: () -> Unit) {
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
                    .background(if (isSystemInDarkTheme()) Color(0xFF2D3748) else Color(0xFFE3EAF6)),
                contentScale = ContentScale.Crop
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
