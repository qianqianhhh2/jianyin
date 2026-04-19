package moe.ouom.biliapi.data.platform

import java.net.URI

data class BiliAudioStreamInfo(
    val id: Int?,
    val mimeType: String,
    val bitrateKbps: Int,
    val qualityTag: String?,
    val url: String,
    val candidateUrls: List<String> = listOf(url)
)

internal fun isBiliStreamHost(host: String): Boolean {
    val normalized = host.trim().lowercase()
    if (normalized.isBlank()) return false
    return normalized.contains("bilivideo.") || normalized.endsWith(".mountaintoys.cn")
}

internal fun isBiliStreamUrl(url: String): Boolean =
    runCatching { URI(url).host.orEmpty() }
        .getOrNull()
        ?.let(::isBiliStreamHost) == true

private fun scoreBiliStreamUrl(url: String): Int {
    val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    return when {
        host.startsWith("upos-") && host.contains("bilivideo.") -> 3
        host.contains("bilivideo.") -> 2
        host.endsWith(".mountaintoys.cn") -> 1
        else -> 0
    }
}

fun prioritizeBiliStreamUrls(primaryUrl: String, backupUrls: List<String>): List<String> {
    val deduped = buildList {
        add(primaryUrl)
        addAll(backupUrls)
    }.map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    return deduped.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<String>> { scoreBiliStreamUrl(it.value) }
                .thenBy { it.index }
        )
        .map { it.value }
}

private fun regularQualityUpperBoundExclusive(quality: BiliQuality): Int = when (quality) {
    BiliQuality.LOSSLESS -> BiliQuality.HIRES.minBitrateKbps
    BiliQuality.HIGH -> BiliQuality.LOSSLESS.minBitrateKbps
    BiliQuality.MEDIUM -> BiliQuality.HIGH.minBitrateKbps
    BiliQuality.LOW -> BiliQuality.MEDIUM.minBitrateKbps
    else -> Int.MAX_VALUE
}

private fun matchesRegularQuality(
    stream: BiliAudioStreamInfo,
    quality: BiliQuality
): Boolean {
    if (stream.qualityTag != null) return false
    val upperBoundExclusive = regularQualityUpperBoundExclusive(quality)
    return stream.bitrateKbps >= quality.minBitrateKbps &&
        stream.bitrateKbps < upperBoundExclusive
}

private fun isLosslessLikeStream(stream: BiliAudioStreamInfo): Boolean {
    if (stream.qualityTag == "lossless" || stream.qualityTag == "hires") return true
    val mimeType = stream.mimeType.trim().lowercase()
    return mimeType == "audio/flac" || mimeType == "audio/x-flac"
}

enum class BiliQuality(val key: String, val minBitrateKbps: Int) {
    DOLBY("dolby",      0),
    HIRES("hires",    1000),
    LOSSLESS("lossless", 500),
    HIGH("high",       180),
    MEDIUM("medium",   120),
    LOW("low",          60);

    companion object {
        private val order = listOf(DOLBY, HIRES, LOSSLESS, HIGH, MEDIUM, LOW)

        fun fromKey(key: String): BiliQuality =
            order.find { it.key == key } ?: HIGH

        fun degradeChain(from: BiliQuality): List<BiliQuality> {
            val startIdx = order.indexOf(from).coerceAtLeast(0)
            return order.drop(startIdx)
        }
    }
}

fun selectStreamByPreference(
    available: List<BiliAudioStreamInfo>,
    preferredKey: String
): BiliAudioStreamInfo? {
    if (available.isEmpty()) return null
    val pref = BiliQuality.fromKey(preferredKey)

    val regularSorted = available
        .filter { it.qualityTag == null }
        .sortedByDescending { it.bitrateKbps }
    val taggedSorted = available
        .filter { it.qualityTag != null }
        .sortedByDescending { it.bitrateKbps }
    val sorted = (regularSorted + taggedSorted).distinctBy { it.url }

    when (pref) {
        BiliQuality.DOLBY ->
            sorted.firstOrNull { it.qualityTag == "dolby" }?.let { return it }
        BiliQuality.HIRES ->
            sorted.firstOrNull { it.qualityTag == "hires" }?.let { return it }
        BiliQuality.LOSSLESS ->
            sorted.firstOrNull(::isLosslessLikeStream)?.let { return it }
        else -> Unit
    }

    for (q in BiliQuality.degradeChain(pref)) {
        val hit = when (q) {
            BiliQuality.DOLBY   -> sorted.firstOrNull { it.qualityTag == "dolby" }
            BiliQuality.HIRES   -> sorted.firstOrNull { it.qualityTag == "hires" }
            BiliQuality.LOSSLESS ->
                sorted.firstOrNull(::isLosslessLikeStream)
                    ?: regularSorted.firstOrNull { matchesRegularQuality(it, q) }
            else -> regularSorted.firstOrNull { matchesRegularQuality(it, q) }
        }
        if (hit != null) return hit
    }

    return sorted.firstOrNull()
}
