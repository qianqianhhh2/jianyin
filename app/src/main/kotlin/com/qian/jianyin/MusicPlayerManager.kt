package com.qian.jianyin

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class MusicPlayerManager(context: Context) {
    private val exoPlayer = ExoPlayer.Builder(context).build()

    fun play(song: Song) {
        val mediaItem = MediaItem.fromUri(song.url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun togglePlay() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }
    
    fun release() {
        exoPlayer.release()
    }
}
