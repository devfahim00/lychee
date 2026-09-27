package com.devfahim00.lychee.core

import org.json.JSONObject

data class VideoFormat(
    val formatId: String,
    val ext: String?,
    val height: Int?,
    val fps: Double?,
    val vcodec: String?,
    val acodec: String?,
    val tbr: Double?,
    val abr: Double?,
    val filesize: Long?,
    val filesizeApprox: Long?,
    val formatNote: String?,
    val resolution: String?
) {
    val isAudioOnly: Boolean get() = vcodec == null || vcodec == "none"
    val isVideoOnly: Boolean get() = acodec == null || acodec == "none"
    val sizeBytes: Long get() = filesize ?: filesizeApprox ?: -1L
}

data class PlaylistEntry(
    val id: String?,
    val title: String?,
    val url: String?,
    val duration: Long?
)

data class VideoInfo(
    val id: String?,
    val title: String,
    val uploader: String?,
    val duration: Long?,
    val thumbnail: String?,
    val webpageUrl: String?,
    val extractor: String?,
    val isLive: Boolean,
    val isPlaylist: Boolean,
    val playlistCount: Int,
    val entries: List<PlaylistEntry>,
    val formats: List<VideoFormat>
) {
    val bestAudioFormat: VideoFormat?
        get() = formats.filter { it.isAudioOnly }.maxByOrNull { it.abr ?: it.tbr ?: 0.0 }
}

data class DownloadOptions(
    val url: String,
    val formatSelector: String,
    val audioOnly: Boolean,
    val playlist: Boolean,
    val title: String
)

enum class DownloadStatus { QUEUED, ACTIVE, COMPLETED, ERROR, CANCELED }

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val status: DownloadStatus,
    val progress: Float = -1f,
    val etaSeconds: Long = -1L,
    val speedBps: Long = -1L,
    val stage: String = "",
    val filePath: String? = null,
    val error: String? = null,
    val log: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long = 0L,
    val audioOnly: Boolean = false
)

object InfoParser {

    fun parse(json: String): VideoInfo {
        val root = JSONObject(json)
        val entries = if (root.optJSONObject("entries") != null || root.optString("_type") == "playlist") {
            val arr = root.optJSONArray("entries")
            (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                val e = arr?.optJSONObject(i) ?: return@mapNotNull null
                PlaylistEntry(
                    id = e.optStringOrNull("id"),
                    title = e.optStringOrNull("title"),
                    url = e.optStringOrNull("url") ?: e.optStringOrNull("webpage_url"),
                    duration = e.optLongOrNull("duration")
                )
            }
        } else emptyList()

        val formatsArr = root.optJSONArray("formats")
        val formats = (0 until (formatsArr?.length() ?: 0)).mapNotNull { i ->
            val f = formatsArr?.optJSONObject(i) ?: return@mapNotNull null
            VideoFormat(
                formatId = f.optString("format_id", "?"),
                ext = f.optStringOrNull("ext"),
                height = f.optIntOrNull("height"),
                fps = f.optDoubleOrNull("fps"),
                vcodec = f.optStringOrNull("vcodec"),
                acodec = f.optStringOrNull("acodec"),
                tbr = f.optDoubleOrNull("tbr"),
                abr = f.optDoubleOrNull("abr"),
                filesize = f.optLongOrNull("filesize"),
                filesizeApprox = f.optLongOrNull("filesize_approx"),
                formatNote = f.optStringOrNull("format_note"),
                resolution = f.optStringOrNull("resolution")
            )
        }

        return VideoInfo(
            id = root.optStringOrNull("id"),
            title = root.optString("title", "Untitled"),
            uploader = root.optStringOrNull("uploader") ?: root.optStringOrNull("channel"),
            duration = root.optLongOrNull("duration"),
            thumbnail = root.optStringOrNull("thumbnail"),
            webpageUrl = root.optStringOrNull("webpage_url") ?: root.optStringOrNull("original_url"),
            extractor = root.optStringOrNull("extractor_key") ?: root.optStringOrNull("extractor"),
            isLive = root.optBoolean("is_live", false),
            isPlaylist = root.optString("_type") == "playlist" || entries.isNotEmpty(),
            playlistCount = root.optInt("playlist_count", entries.size),
            entries = entries,
            formats = formats
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val v = optString(key, "") ?: return null
        return v.ifEmpty { null }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key, -1).takeIf { it >= 0 } else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key, -1).takeIf { it >= 0 } else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key, -1.0).takeIf { !it.isNaN() && it >= 0 } else null
}
