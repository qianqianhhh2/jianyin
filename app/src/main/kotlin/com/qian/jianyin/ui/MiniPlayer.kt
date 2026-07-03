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
import com.qian.jianyin.R
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.remember

private fun getRandomPlaceholderId(): Int {
    val ids = listOf(R.drawable.miku_1, R.drawable.miku_2, R.drawable.miku_3, R.drawable.miku_4, R.drawable.miku_5, R.drawable.miku_6, R.drawable.miku_7, R.drawable.miku_8, R.drawable.miku_9)
    return ids.random()
}

@Composable
fun MiniPlayer(song: Song, isPlaying: Boolean, onTogglePlay: () -> Unit, onSkipNext: () -> Unit) {
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
            val mikuPainter = painterResource(id = getRandomPlaceholderId())
            AsyncImage(
                model = song.pic,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                error = mikuPainter
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
            IconButton(onClick = onSkipNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首")
            }
        }
    }
}
