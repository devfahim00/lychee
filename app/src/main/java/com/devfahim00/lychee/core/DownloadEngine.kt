package com.devfahim00.lychee.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Builds yt-dlp argument lists from app settings, following the yt-dlp docs:
 *  - UPDATE:        https://github.com/yt-dlp/yt-dlp#update
 *  - RECOMMENDED:   https://github.com/yt-dlp/yt-dlp#strongly-recommended (ffmpeg/ffprobe + yt-dlp-ejs via QuickJS)
 *  - IMPERSONATION: https://github.com/yt-dlp/yt-dlp#impersonation (curl_cffi, --impersonate)
 */
object ArgBuilder {

    fun baseArgs(context: Context, settings: Settings, cookieFile: File? = null): MutableList<String> {
        val args = mutableListOf<String>()
        // Progress one-line-per-update for reliable parsing
        args.add("--newline")
        // ffmpeg/ffprobe are strongly recommended for merging + post-processing
        args.add("--ffmpeg-location")
        args.add(LycheeRuntime.ffmpegBin.absolutePath)
        // Register QuickJS so yt-dlp-ejs can run (required for full YouTube support)
        if (LycheeRuntime.qjsBin.exists()) {
            args.add("--js-runtimes")
            args.add("quickjs:${LycheeRuntime.qjsBin.absolutePath}")
        }
        // Cookies imported from the built-in browser
        if (cookieFile != null && cookieFile.exists()) {
            args.add("--cookies")
            args.add(cookieFile.absolutePath)
        }
        // Impersonation (must be supported per user requirements)
        if (settings.impersonateEnabled) {
            args.add("--impersonate")
            args.add(settings.impersonateTarget.ifBlank { "chrome" })
        }
        if (settings.proxyUrl.isNotBlank()) {
            args.add("--proxy")
            args.add(settings.proxyUrl.trim())
        }
        if (settings.restrictFilenames) {
            args.add("--restrict-filenames")
        }
        return args
    }

    fun infoArgs(): List<String> {
        return listOf(
            "--dump-single-json",
            "--no-playlist",
            "--no-warnings"
        )
    }

    fun downloadArgs(settings: Settings, options: DownloadOptions): MutableList<String> {
        val args = mutableListOf<String>()
        args.add("--no-playlist")
        if (options.playlist) {
            args.remove("--no-playlist")
            args.add("--yes-playlist")
        }

        // Output template
        val template = if (options.playlist) settings.playlistTemplate else settings.template
        args.add("-o")
        args.add(File(settings.dirPath, template).absolutePath)

        // Format selection
        if (options.formatSelector.isNotBlank()) {
            args.add("-f")
            args.add(options.formatSelector)
        }

        if (options.audioOnly) {
            args.add("-x")
            args.add("--audio-format")
            args.add(settings.audioFormat)
        } else if (options.formatSelector.contains('+')) {
            // merging separate video+audio streams: prefer mp4 container
            args.add("--merge-output-format")
            args.add("mp4")
        }

        // Downloader / speed
        if (settings.useAria2c) {
            args.add("--downloader")
            args.add("libaria2c.so")
            args.add("--downloader-args")
            args.add("aria2c:-x 16 -k 1M --summary-interval=1")
        } else if (settings.concurrentFragments > 1) {
            args.add("--concurrent-fragments")
            args.add(settings.concurrentFragments.toString())
        }

        // Post-processing
        if (settings.embedMetadata) {
            args.add("--embed-metadata")
        }
        if (settings.embedThumbnail) {
            args.add("--embed-thumbnail")
        }
        if (settings.writeSubtitles) {
            args.add("--write-subs")
            if (settings.embedSubtitles) {
                args.add("--embed-subs")
            }
            args.add("--sub-langs")
            args.add(settings.subtitleLanguages.ifBlank { "en.*" })
        }
        if (settings.removeSponsorBlock) {
            args.add("--sponsorblock-remove")
            args.add("sponsor")
        }

        // Extra user args (advanced)
        if (settings.extraArgs.isNotBlank()) {
            args.addAll(tokenizeArgs(settings.extraArgs))
        }
        return args
    }

    /** Simple whitespace tokenizer honoring double quotes. */
    fun tokenizeArgs(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in input) {
            when {
                c == '"' -> inQuotes = !inQuotes
                (c == ' ' || c == '\n' || c == '\t') && !inQuotes -> {
                    if (sb.isNotEmpty()) {
                        tokens.add(sb.toString()); sb.clear()
                    }
                }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) tokens.add(sb.toString())
        return tokens
    }
}

/**
 * Serializes downloads through a small executor and exposes state to UI + service.
 */
object DownloadEngine {

    private const val MAX_CONCURRENT = 2
    private const val MAX_LOG_CHARS = 16000
    private const val HISTORY_FILE = "history.json"
    private const val HISTORY_LIMIT = 100

    private val executor = Executors.newFixedThreadPool(MAX_CONCURRENT)
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks

    private var appContext: Context? = null
    private val historyLoaded = AtomicBoolean(false)
    private val lock = Any()

    fun ensureStarted(context: Context) {
        synchronized(lock) {
            if (appContext == null) {
                appContext = context.applicationContext
                loadHistory()
            }
        }
    }

    fun enqueue(
        context: Context,
        options: DownloadOptions,
        settings: Settings,
        startService: () -> Unit
    ) {
        ensureStarted(context)
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            url = options.url,
            title = options.title,
            status = DownloadStatus.QUEUED,
            audioOnly = options.audioOnly
        )
        _tasks.value = _tasks.value + task
        startService()
        executor.submit {
            runTask(task.id, options, settings)
        }
    }

    private fun runTask(taskId: String, options: DownloadOptions, settings: Settings) {
        val ctx = appContext ?: return
        updateTask(taskId) { it.copy(status = DownloadStatus.ACTIVE, stage = "Starting") }
        val args = mutableListOf<String>()
        args.addAll(ArgBuilder.baseArgs(ctx, settings, CookieStore.mergedCookieFile(ctx, settings)))
        args.addAll(ArgBuilder.downloadArgs(settings, options))
        args.add("--no-cache-dir")
        args.add(options.url)

        try {
            val result = LycheeRuntime.execute(
                context = ctx,
                args = args,
                processId = taskId,
                onLine = { line ->
                    updateTask(taskId) { t ->
                        t.copy(
                            log = (t.log + line + "\n").takeLast(MAX_LOG_CHARS),
                            stage = detectStage(line, t.stage)
                        )
                    }
                },
                onProgress = { p ->
                    updateTask(taskId) { t ->
                        t.copy(
                            progress = p.percent,
                            etaSeconds = p.etaSeconds,
                            speedBps = p.speedBps,
                            stage = if (p.percent >= 0f) "Downloading" else t.stage
                        )
                    }
                }
            )

            when {
                result.canceled -> updateTask(taskId) {
                    it.copy(status = DownloadStatus.CANCELED, stage = "", finishedAt = System.currentTimeMillis())
                }
                result.exitCode == 0 -> {
                    val outPath = result.lastFilePath ?: guessFile(ctx, settings, options)
                    updateTask(taskId) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            stage = "Done",
                            progress = 100f,
                            filePath = outPath,
                            finishedAt = System.currentTimeMillis()
                        )
                    }
                }
                else -> {
                    val err = extractError(result.err.ifBlank { result.out })
                    updateTask(taskId) {
                        it.copy(
                            status = DownloadStatus.ERROR,
                            stage = "",
                            error = err,
                            finishedAt = System.currentTimeMillis()
                        )
                    }
                }
            }
        } catch (e: LycheeRuntime.CanceledException) {
            updateTask(taskId) {
                it.copy(status = DownloadStatus.CANCELED, stage = "", finishedAt = System.currentTimeMillis())
            }
        } catch (e: Exception) {
            updateTask(taskId) {
                it.copy(
                    status = DownloadStatus.ERROR,
                    stage = "",
                    error = e.message ?: "Unknown error",
                    finishedAt = System.currentTimeMillis()
                )
            }
        } finally {
            saveHistory()
        }
    }

    fun cancel(taskId: String) {
        LycheeRuntime.cancelProcess(taskId)
        updateTask(taskId) {
            if (it.status == DownloadStatus.QUEUED) {
                it.copy(status = DownloadStatus.CANCELED, stage = "", finishedAt = System.currentTimeMillis())
            } else it
        }
    }

    fun remove(taskId: String) {
        _tasks.value = _tasks.value.filterNot { it.id == taskId }
        saveHistory()
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filter {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.ACTIVE
        }
        saveHistory()
    }

    private fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == taskId) transform(it) else it }
    }

    private fun detectStage(line: String, current: String): String = when {
        line.startsWith("[Merger]") -> "Merging streams"
        line.startsWith("[ExtractAudio]") -> "Extracting audio"
        line.startsWith("[Metadata]") || line.startsWith("[EmbedMetadata]") -> "Embedding metadata"
        line.startsWith("[EmbedThumbnail]") -> "Embedding thumbnail"
        line.startsWith("[Fixup") || line.startsWith("[video_remuxer]") -> "Fixing container"
        line.startsWith("[SubtitlesConvertor]") || line.startsWith("[SubtitlesEmbed]") -> "Processing subtitles"
        line.startsWith("[SponsorBlock]") || line.startsWith("[ModifyChapters]") -> "Removing segments"
        line.startsWith("[download]") && line.contains("Destination") -> "Downloading"
        else -> current
    }

    private fun extractError(output: String): String {
        val lines = output.lines().filter { it.startsWith("ERROR") || it.startsWith("error:") }
        return lines.lastOrNull()?.take(500) ?: output.lines().lastOrNull { it.isNotBlank() }?.take(500) ?: "Unknown error"
    }

    /** When yt-dlp did not reveal the path, look for the newest file in the download dir. */
    private fun guessFile(ctx: Context, settings: Settings, options: DownloadOptions): String? {
        return try {
            val dir = File(settings.dirPath)
            if (!dir.exists()) return null
            dir.walkTopDown()
                .filter { it.isFile && it.extension != "part" && it.extension != "ytdl" }
                .maxByOrNull { it.lastModified() }
                ?.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    // ---- persistence ----

    private fun historyFile(ctx: Context): File = File(ctx.filesDir, HISTORY_FILE)

    private fun loadHistory() {
        val ctx = appContext ?: return
        if (!historyLoaded.compareAndSet(false, true)) return
        try {
            val file = historyFile(ctx)
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            val restored = mutableListOf<DownloadTask>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                restored.add(
                    DownloadTask(
                        id = o.getString("id"),
                        url = o.getString("url"),
                        title = o.optString("title", "Untitled"),
                        status = runCatching { DownloadStatus.valueOf(o.optString("status", "COMPLETED")) }
                            .getOrDefault(DownloadStatus.COMPLETED),
                        progress = if (o.optString("progress").isNotEmpty()) o.optDouble("progress", -1.0).toFloat() else -1f,
                        filePath = o.optStringOrNull("filePath"),
                        error = o.optStringOrNull("error"),
                        createdAt = o.optLong("createdAt"),
                        finishedAt = o.optLong("finishedAt"),
                        audioOnly = o.optBoolean("audioOnly", false)
                    )
                )
            }
            _tasks.value = restored.takeLast(HISTORY_LIMIT)
        } catch (_: Exception) {
        }
    }

    private fun saveHistory() {
        val ctx = appContext ?: return
        try {
            val persistable = _tasks.value
                .filter { it.status != DownloadStatus.QUEUED && it.status != DownloadStatus.ACTIVE }
                .takeLast(HISTORY_LIMIT)
            val arr = JSONArray()
            for (t in persistable) {
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("url", t.url)
                        .put("title", t.title)
                        .put("status", t.status.name)
                        .put("progress", t.progress.toDouble())
                        .put("filePath", t.filePath ?: "")
                        .put("error", t.error ?: "")
                        .put("createdAt", t.createdAt)
                        .put("finishedAt", t.finishedAt)
                        .put("audioOnly", t.audioOnly)
                )
            }
            historyFile(ctx).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val v = optString(key, "") ?: return null
        return v.ifEmpty { null }
    }
}
