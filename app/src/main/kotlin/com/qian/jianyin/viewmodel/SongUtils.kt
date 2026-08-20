package com.qian.jianyin

/**
 * 歌曲相关的纯工具函数
 */

/** 判断两首歌曲是否为同一首（优先比较 ID，其次比较 URL） */
internal fun isSameSong(a: Song, b: Song): Boolean {
    // 优先比较ID（最可靠的标识）
    if (a.id.isNotBlank() && b.id.isNotBlank() && a.id == b.id) {
        return true
    }
    // 如果ID不可用或不匹配，比较音乐文件的URL（通常是唯一的）
    if (a.url.isNotBlank() && b.url.isNotBlank() && a.url == b.url) {
        return true
    }
    return false
}

/** 计算搜索匹配得分 */
internal fun calculateMatchScore(song: Song, query: String): Int {
    var score = 0
    val songNameLower = song.name.lowercase()
    val artistLower = song.artist.lowercase()

    if (songNameLower == query) {
        score += 1000
    } else if (songNameLower.startsWith(query)) {
        score += 500
    } else if (songNameLower.contains(query)) {
        score += 200 + (100 - songNameLower.indexOf(query))
    }

    if (artistLower == query) {
        score += 800
    } else if (artistLower.contains(query)) {
        score += 100
    }

    return score
}

/**
 * 交错插入算法：将两个列表按比例交替插入，模拟自然混合效果
 * 比例约为 3:2（推荐:曲库），确保风格平滑过渡
 */
internal fun interleaveSongs(list1: List<Song>, list2: List<Song>): List<Song> {
    val result = mutableListOf<Song>()
    val it1 = list1.iterator()
    val it2 = list2.iterator()

    var count = 0
    while (it1.hasNext() || it2.hasNext()) {
        if (count % 5 < 3 && it1.hasNext()) {
            result.add(it1.next())
        } else if (it2.hasNext()) {
            result.add(it2.next())
        } else if (it1.hasNext()) {
            result.add(it1.next())
        }
        count++
    }

    return result
}
