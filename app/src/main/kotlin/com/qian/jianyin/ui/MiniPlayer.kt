package com.qian.jianyin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun MiniPlayer(song: Song, isPlaying: Boolean, onTogglePlay: () -> Unit) {
    Surface(
        // MD3 的 tonalElevation，会根据系统动态颜色产生色调
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)) // MD3 风格的圆角
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            AsyncImage(
                model = song.pic,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
            )
            // 歌名和歌手
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(song.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 播放/暂停/切歌
            IconButton(onClick = onTogglePlay) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "播放/暂停")
            }
            IconButton(onClick = { /* TODO: 下一首 */ }) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首")
            }
        }
    }
}
