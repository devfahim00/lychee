package com.devfahim00.lychee.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * yt-dlp updater following https://github.com/yt-dlp/yt-dlp#update
 *
 * Release channels:
 *  - stable:  yt-dlp/yt-dlp (default)
 *  - nightly: yt-dlp/yt-dlp-nightly-builds (recommended by yt-dlp for regular users)
 *  - master:  yt-dlp/yt-dlp-master-builds
 *
 * The update replaces the yt-dlp zipapp that is executed by the bundled python.
 */
object Updater {

    data class UpdateInfo(
        val channel: String,
        val tag: String,
        val name: String,
        val downloadUrl: String,
        val sizeBytes: Long
    )

    object Channels {
        const val STABLE = "stable"
        const val NIGHTLY = "nightly"
        const val MASTER = "master"

        fun repo(channel: String): String = when (channel) {
            NIGHTLY -> "yt-dlp/yt-dlp-nightly-builds"
            MASTER -> "yt-dlp/yt-dlp-master-builds"
            else -> "yt-dlp/yt-dlp"
        }

        fun label(channel: String): String = when (channel) {
            NIGHTLY -> "Nightly"
            MASTER -> "Master"
            else -> "Stable"
        }
    }

    fun installedVersion(context: Context): String {
        val prefs = context.getSharedPreferences("lychee_runtime", Context.MODE_PRIVATE)
        return prefs.getString("ytdlp_version", null)
            ?: LycheeRuntime.BUNDLED_YTDLP_VERSION + " (bundled)"
    }

    private fun storeVersion(context: Context, tag: String) {
        context.getSharedPreferences("lychee_runtime", Context.MODE_PRIVATE)
            .edit().putString("ytdlp_version", tag).apply()
    }

    /** Fetch the latest release info for the channel. Returns null when up-to-date check should compare tags. */
    fun fetchLatest(channel: String): UpdateInfo {
        val repo = Channels.repo(channel)
        val url = URL("https://api.github.com/repos/$repo/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Lychee-App")
        try {
            if (conn.responseCode !in 200..299) {
                throw java.io.IOException("GitHub API error ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.getString("tag_name")
            val name = json.optString("name", tag)
            var downloadUrl: String? = null
            var size = 0L
            val assets = json.optJSONArray("assets")
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets?.optJSONObject(i) ?: continue
                if (a.getString("name") == LycheeRuntime.YTDLP_BIN_NAME) {
                    downloadUrl = a.getString("browser_download_url")
                    size = a.optLong("size", 0L)
                    break
                }
            }
            return UpdateInfo(channel, tag, name, downloadUrl ?: throw java.io.IOException("yt-dlp asset not found"), size)
        } finally {
            conn.disconnect()
        }
    }

    fun isUpdateAvailable(context: Context, info: UpdateInfo): Boolean {
        val current = installedVersion(context).removeSuffix(" (bundled)")
        return current != info.tag
    }

    /**
     * Download and install the given release. Replaces the yt-dlp zipapp atomically.
     */
    fun performUpdate(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit = {}): Result<String> {
        return try {
            LycheeRuntime.init(context)
            val tmp = File(context.cacheDir, "ytdlp-download.tmp")
            if (tmp.exists()) tmp.delete()

            val url = URL(info.downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "Lychee-App")
            try {
                if (conn.responseCode !in 200..299) {
                    return Result.failure(java.io.IOException("Download failed: HTTP ${conn.responseCode}"))
                }
                val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else info.sizeBytes
                var downloaded = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(tmp).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) onProgress(downloaded.toFloat() / total)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            // Sanity check: zipapp must be a zip larger than 1MB starting with PK
            if (tmp.length() < 1_000_000) {
                tmp.delete()
                return Result.failure(java.io.IOException("Downloaded file looks invalid (too small)"))
            }
            val head = java.io.RandomAccessFile(tmp, "r").use { raf ->
                val b = ByteArray(2); raf.readFully(b); b
            }
            if (!(head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte())) {
                tmp.delete()
                return Result.failure(java.io.IOException("Downloaded file is not a valid yt-dlp zipapp"))
            }

            // Atomic-ish replace
            val target = LycheeRuntime.ytdlpFile
            val next = File(target.parentFile, "yt-dlp.next")
            if (next.exists()) next.delete()
            java.nio.file.Files.move(
                tmp.toPath(), next.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
            if (target.exists()) target.delete()
            if (!next.renameTo(target)) {
                return Result.failure(java.io.IOException("Failed to replace yt-dlp binary"))
            }
            storeVersion(context, info.tag)
            Result.success(info.tag)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Reset to the version bundled inside the APK. */
    fun resetToBundled(context: Context): Result<String> {
        return try {
            LycheeRuntime.reinstallYtdlp(context)
            context.getSharedPreferences("lychee_runtime", Context.MODE_PRIVATE)
                .edit().remove("ytdlp_version").apply()
            Result.success(LycheeRuntime.BUNDLED_YTDLP_VERSION)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
