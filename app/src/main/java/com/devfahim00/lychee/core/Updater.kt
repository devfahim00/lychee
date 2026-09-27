package com.devfahim00.lychee.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * yt-dlp updater following https://github.com/yt-dlp/yt-dlp#update
 *
 * Release channels:
 *  - stable:  yt-dlp/yt-dlp (default)
 *  - nightly: yt-dlp/yt-dlp-nightly-builds (recommended by yt-dlp for regular users)
 *  - master:  yt-dlp/yt-dlp-master-builds
 *
 * The "yt-dlp" release asset is a zipapp with a short shebang header prepended
 * ("#!/usr/bin/env python3\n" + zip data). The updater normalizes it into a
 * clean zip file so it can be executed by the bundled python interpreter.
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

    /**
     * Fetch the latest release info for the channel.
     *
     * Primary: GitHub REST API.
     * Fallback: the rate-limit-free github.com "releases/latest" redirect,
     * which works even when the anonymous API quota for the current network
     * is exhausted (common on shared mobile carrier IPs).
     */
    fun fetchLatest(channel: String): UpdateInfo {
        val apiError = try {
            return fetchLatestViaApi(channel)
        } catch (e: Exception) {
            e
        }
        return try {
            fetchLatestViaRedirect(channel)
        } catch (_: Exception) {
            throw IOException(
                "GitHub unreachable (rate limit?) — ${apiError.message ?: "unknown error"}",
                apiError
            )
        }
    }

    private fun fetchLatestViaApi(channel: String): UpdateInfo {
        val repo = Channels.repo(channel)
        val url = URL("https://api.github.com/repos/$repo/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Lychee-App")
        try {
            if (conn.responseCode !in 200..299) {
                throw IOException("GitHub API error ${conn.responseCode}")
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
            return UpdateInfo(
                channel, tag, name,
                downloadUrl ?: throw IOException("yt-dlp asset not found"),
                size
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Rate-limit-free fallback: "https://github.com/<repo>/releases/latest"
     * answers with a 302 whose Location header contains the latest tag.
     */
    private fun fetchLatestViaRedirect(channel: String): UpdateInfo {
        val repo = Channels.repo(channel)
        val conn = URL("https://github.com/$repo/releases/latest").openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("User-Agent", "Lychee-App")
        try {
            val code = conn.responseCode
            if (code !in 300..399) {
                throw IOException("GitHub returned HTTP $code")
            }
            val location = conn.getHeaderField("Location")
            if (location.isNullOrBlank() || !location.contains("/tag/")) {
                throw IOException("unexpected redirect")
            }
            val tag = location.substringAfterLast("/")
            if (tag.isBlank()) throw IOException("unexpected redirect")
            val downloadUrl = "https://github.com/$repo/releases/download/$tag/${LycheeRuntime.YTDLP_BIN_NAME}"
            return UpdateInfo(channel, tag, tag, downloadUrl, 0L)
        } finally {
            conn.disconnect()
        }
    }

    fun isUpdateAvailable(context: Context, info: UpdateInfo): Boolean {
        val current = installedVersion(context).removeSuffix(" (bundled)")
        return current != info.tag
    }

    /**
     * Download and install the given release. The release asset is
     * normalized into a clean zipapp, verified by actually running it, and
     * the previous version is restored if the new one fails to start.
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
                    return Result.failure(IOException("Download failed: HTTP ${conn.responseCode}"))
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

            // The release asset carries a shebang header before the zip data;
            // rewrite it as a clean zipapp and sanity-check its contents.
            val normalized = File(context.cacheDir, "ytdlp-normalized.pyz")
            if (normalized.exists()) normalized.delete()
            if (!normalizeZipapp(tmp, normalized)) {
                tmp.delete()
                normalized.delete()
                return Result.failure(IOException("Downloaded release is not a usable yt-dlp archive"))
            }
            tmp.delete()

            // Swap in with a backup so a broken build can be rolled back
            val target = LycheeRuntime.ytdlpFile
            val backup = File(target.parentFile, "yt-dlp.bak")
            if (backup.exists()) backup.delete()
            val hadPrevious = target.exists() && target.renameTo(backup)
            if (!normalized.renameTo(target)) {
                if (hadPrevious) backup.renameTo(target)
                return Result.failure(IOException("Failed to replace yt-dlp binary"))
            }

            // Verify the new zipapp actually starts
            val versionProbe = runCatching {
                LycheeRuntime.execute(context, listOf("--no-warnings", "--version"))
            }.getOrNull()
            val versionOk = versionProbe != null && versionProbe.exitCode == 0 &&
                versionProbe.out.isNotBlank()
            if (!versionOk) {
                target.delete()
                if (hadPrevious) backup.renameTo(target)
                return Result.failure(IOException("Updated yt-dlp failed to start; previous version restored"))
            }

            backup.delete()
            storeVersion(context, info.tag)
            Result.success(info.tag)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rebuild the downloaded artifact as a clean zip file (starts with "PK").
     * The release asset is "shebang line + zip", and java.util.zip.ZipFile
     * locates the archive via its end-of-central-directory record, so the
     * prepended header is tolerated transparently.
     */
    private fun normalizeZipapp(src: File, dest: File): Boolean {
        return try {
            ZipFile(src).use { zip ->
                var hasPackage = false
                var hasRootMain = false
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    if (name.startsWith("yt_dlp/")) hasPackage = true
                    if (name == "__main__.py") hasRootMain = true
                }
                if (!hasPackage) return false

                ZipOutputStream(FileOutputStream(dest)).use { out ->
                    val copy = zip.entries()
                    while (copy.hasMoreElements()) {
                        val entry = copy.nextElement()
                        if (entry.isDirectory) continue
                        out.putNextEntry(ZipEntry(entry.name))
                        zip.getInputStream(entry).use { it.copyTo(out) }
                        out.closeEntry()
                    }
                    if (!hasRootMain) {
                        out.putNextEntry(ZipEntry("__main__.py"))
                        out.write(
                            ("import sys\n" +
                                "import yt_dlp\n" +
                                "sys.exit(yt_dlp.main())\n").toByteArray(Charsets.UTF_8)
                        )
                        out.closeEntry()
                    }
                }
            }
            dest.length() > 1_000_000
        } catch (_: Exception) {
            false
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
