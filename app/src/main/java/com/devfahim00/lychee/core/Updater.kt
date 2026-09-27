package com.devfahim00.lychee.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
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
     * Download and install the given release. The download is validated and
     * retried, the release asset is normalized into a clean zipapp, verified
     * by actually running it, and the previous version is restored if the
     * new one fails to start.
     */
    fun performUpdate(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit = {}): Result<String> {
        return try {
            LycheeRuntime.init(context)
            val tmp = File(context.cacheDir, "ytdlp-download.tmp")
            val normalized = File(context.cacheDir, "ytdlp-normalized.pyz")
            if (normalized.exists()) normalized.delete()

            downloadRelease(info, tmp, onProgress)
            try {
                normalizeZipapp(tmp, normalized)

                // Swap in with a backup so a broken build can be rolled back
                val target = LycheeRuntime.ytdlpFile
                val backup = File(target.parentFile, "yt-dlp.bak")
                if (backup.exists()) backup.delete()
                val hadPrevious = target.exists() && target.renameTo(backup)
                if (!normalized.renameTo(target)) {
                    if (hadPrevious) backup.renameTo(target)
                    return Result.failure(IOException("failed to replace the yt-dlp binary"))
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
                    return Result.failure(IOException("updated yt-dlp failed to start; previous version restored"))
                }

                backup.delete()
                storeVersion(context, info.tag)
                Result.success(info.tag)
            } finally {
                tmp.delete()
                normalized.delete()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download the release asset with retries and strict validation.
     * Some mobile networks and transparent proxies serve an HTML page or
     * truncate the transfer while still reporting HTTP 200 — those cases
     * are detected here and reported explicitly instead of surfacing as a
     * confusing "not an archive" error later.
     */
    private fun downloadRelease(info: UpdateInfo, dest: File, onProgress: (Float) -> Unit) {
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                if (dest.exists()) dest.delete()
                if (attempt > 1) Thread.sleep(750L)
                val conn = URL(info.downloadUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                conn.setRequestProperty("User-Agent", "Lychee-App")
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) throw IOException("HTTP $code")
                    val contentType = conn.contentType ?: ""
                    if (contentType.contains("text/html", ignoreCase = true)) {
                        throw IOException("GitHub returned a web page instead of the engine file")
                    }
                    val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else info.sizeBytes
                    var downloaded = 0L
                    conn.inputStream.use { input ->
                        FileOutputStream(dest).use { output ->
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
                    validateDownload(dest, info)
                    return
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
                dest.delete()
            }
        }
        throw IOException(
            "download failed after 3 attempts — ${lastError?.message ?: "unknown error"}",
            lastError
        )
    }

    /** Reject truncated transfers and HTML/JSON responses that arrive with HTTP 200. */
    private fun validateDownload(file: File, info: UpdateInfo) {
        val size = file.length()
        if (info.sizeBytes > 0 && size != info.sizeBytes) {
            throw IOException("incomplete download: received $size of ${info.sizeBytes} bytes")
        }
        if (size < 1_000_000) {
            throw IOException("incomplete download: received only $size bytes")
        }
        FileInputStream(file).use { input ->
            val head = ByteArray(64)
            var n = 0
            while (n < head.size) {
                val read = input.read(head, n, head.size - n)
                if (read < 0) break
                n += read
            }
            val text = String(head, 0, n, Charsets.ISO_8859_1)
                .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            if (text.startsWith("<") || text.startsWith("{")) {
                throw IOException(
                    "received a text page instead of the engine binary — " +
                        "the network may be blocking GitHub downloads"
                )
            }
        }
    }

    /**
     * Convert the downloaded release asset into a clean zipapp.
     *
     * The asset is a zip archive with a "#!/usr/bin/env python3" stub
     * prepended. Not every Android java.util.zip implementation tolerates
     * archives with prepended data, so the stub is located through the zip
     * end-of-central-directory record and stripped. The result starts with
     * the "PK" local header magic and has fully self-consistent offsets,
     * so any zip reader — and the bundled python — can use it directly.
     */
    private fun normalizeZipapp(src: File, dest: File) {
        val prefixLen = findZipPrefixLength(src)
            ?: throw IOException("downloaded file is not a zip archive (no end record found)")

        RandomAccessFile(src, "r").use { raf ->
            raf.seek(prefixLen)
            FileOutputStream(dest).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = raf.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }

        var hasPackage = false
        var hasRootMain = false
        ZipFile(dest).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.startsWith("yt_dlp/")) hasPackage = true
                if (name == "__main__.py") hasRootMain = true
            }
        }
        if (!hasPackage) throw IOException("archive does not contain the yt_dlp package")

        // Defensive: every released zipapp ships a root __main__.py. If it is
        // ever missing, rebuild the archive with an equivalent entry point.
        if (!hasRootMain) rebuildWithEntryPointShim(dest)

        if (dest.length() < 1_000_000) {
            throw IOException("archive is unexpectedly small (${dest.length()} bytes)")
        }
    }

    /**
     * Length in bytes of the data prepended in front of the zip archive
     * (0 for a clean zip). Computed from the end-of-central-directory
     * record: the stated central-directory offset versus the position it
     * actually occupies in the file.
     */
    private fun findZipPrefixLength(file: File): Long? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val fileSize = raf.length()
                if (fileSize < 22L) return null
                val scanLen = minOf(fileSize, 65536L + 22L).toInt()
                raf.seek(fileSize - scanLen)
                val buf = ByteArray(scanLen)
                raf.readFully(buf)

                var eocdIdx = -1
                var i = scanLen - 22
                while (i >= 0) {
                    if (buf[i] == 0x50.toByte() && buf[i + 1] == 0x4b.toByte() &&
                        buf[i + 2] == 0x05.toByte() && buf[i + 3] == 0x06.toByte()
                    ) {
                        val commentLen =
                            ((buf[i + 21].toLong() and 0xff) shl 8) or
                                (buf[i + 20].toLong() and 0xff)
                        if (i + 22L + commentLen <= scanLen) {
                            eocdIdx = i
                            break
                        }
                    }
                    i--
                }
                if (eocdIdx < 0) return null

                fun u4(off: Int): Long =
                    (buf[off].toLong() and 0xff) or
                        ((buf[off + 1].toLong() and 0xff) shl 8) or
                        ((buf[off + 2].toLong() and 0xff) shl 16) or
                        ((buf[off + 3].toLong() and 0xff) shl 24)

                val cdSize = u4(eocdIdx + 12)
                val cdOffset = u4(eocdIdx + 16)
                val eocdFileOffset = (fileSize - scanLen) + eocdIdx
                if (cdSize <= 0L || cdSize > eocdFileOffset) return null
                val actualCdOffset = eocdFileOffset - cdSize
                val prefix = actualCdOffset - cdOffset
                if (prefix < 0L || prefix > actualCdOffset) return null
                prefix
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Defensive path: re-zip the archive and add a root __main__.py entry point. */
    private fun rebuildWithEntryPointShim(clean: File) {
        val rebuilt = File(clean.parentFile, "ytdlp-rebuilt.pyz")
        if (rebuilt.exists()) rebuilt.delete()
        ZipFile(clean).use { zip ->
            ZipOutputStream(FileOutputStream(rebuilt)).use { out ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    out.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(out) }
                    out.closeEntry()
                }
                out.putNextEntry(ZipEntry("__main__.py"))
                out.write(
                    ("import sys\n" +
                        "import yt_dlp\n" +
                        "sys.exit(yt_dlp.main())\n").toByteArray(Charsets.UTF_8)
                )
                out.closeEntry()
            }
        }
        if (!clean.delete() || !rebuilt.renameTo(clean)) {
            rebuilt.delete()
            throw IOException("failed to finalize the downloaded archive")
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
