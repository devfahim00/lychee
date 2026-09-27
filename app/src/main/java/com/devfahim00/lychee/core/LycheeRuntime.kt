package com.devfahim00.lychee.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.system.Os
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Progress update parsed from yt-dlp output.
 */
data class Progress(
    val percent: Float,
    val etaSeconds: Long,
    val speedBps: Long,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val line: String
)

/**
 * Core yt-dlp runtime for Lychee.
 *
 * Runs the yt-dlp zipapp as a subprocess of the bundled Termux-built Python 3.14
 * (io.github.deniscerri:ytdlnis_packages.python), which ships with curl_cffi
 * (TLS impersonation), cffi, brotli, pycryptodomex, mutagen and websockets.
 * ffmpeg/ffprobe and aria2c come from io.github.junkfood02.youtubedl-android.
 * QuickJS (libqjs.so) is registered via --js-runtimes for yt-dlp-ejs,
 * which is required for full YouTube support.
 */
object LycheeRuntime {
    class CanceledException : Exception()
    class EngineException(message: String, cause: Throwable? = null) : java.lang.RuntimeException(message, cause)

    data class ExecResult(
        val exitCode: Int,
        val out: String,
        val err: String,
        val canceled: Boolean,
        val lastFilePath: String?
    )

    private const val BASE_NAME = "lychee"
    private const val PACKAGES_DIR = "packages"
    const val YTDLP_DIR_NAME = "yt-dlp"
    const val YTDLP_BIN_NAME = "yt-dlp"
    const val BUNDLED_YTDLP_VERSION = "2026.08.19"

    private const val PYTHON_LIB = "libpython.zip.so"
    private const val FFMPEG_LIB = "libffmpeg.zip.so"
    private const val ARIA2C_LIB = "libaria2c.zip.so"

    private var initialized = false
    private lateinit var prefs: SharedPreferences

    lateinit var pythonBin: File private set
    lateinit var qjsBin: File private set
    lateinit var ffmpegBin: File private set
    lateinit var ffprobeBin: File private set
    lateinit var aria2cBin: File private set
    lateinit var ytdlpFile: File private set
    lateinit var pythonUsrDir: File private set
    lateinit var sslCertFile: File private set

    private val idProcessMap = ConcurrentHashMap<String, Process>()

    val isInitialized: Boolean get() = initialized

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val appCtx = context.applicationContext
        prefs = appCtx.getSharedPreferences("lychee_runtime", Context.MODE_PRIVATE)

        val nativeDir = File(appCtx.applicationInfo.nativeLibraryDir)
        val baseDir = File(appCtx.noBackupFilesDir, BASE_NAME)
        if (!baseDir.exists()) baseDir.mkdirs()
        val packagesDir = File(baseDir, PACKAGES_DIR)

        // Extract bundled python environment (symlink-aware)
        val pythonDir = File(packagesDir, "python")
        extractIfNeeded(File(nativeDir, PYTHON_LIB), pythonDir, "python_lib_version")
        pythonUsrDir = File(pythonDir, "usr")
        sslCertFile = File(pythonUsrDir, "etc/tls/cert.pem")

        // ffmpeg + aria2c shared library trees
        extractIfNeeded(File(nativeDir, FFMPEG_LIB), File(packagesDir, "ffmpeg"), "ffmpeg_lib_version")
        extractIfNeeded(File(nativeDir, ARIA2C_LIB), File(packagesDir, "aria2c"), "aria2c_lib_version")

        // yt-dlp zipapp
        val ytdlpDir = File(baseDir, YTDLP_DIR_NAME)
        if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
        ytdlpFile = File(ytdlpDir, YTDLP_BIN_NAME)
        if (!ytdlpFile.exists()) {
            try {
                appCtx.resources.openRawResource(
                    appCtx.resources.getIdentifier("ytdlp", "raw", appCtx.packageName)
                ).use { input ->
                    FileOutputStream(ytdlpFile).use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                ytdlpDir.deleteRecursively()
                throw EngineException("failed to initialize yt-dlp", e)
            }
        }

        pythonBin = File(nativeDir, "libpython.so")
        qjsBin = File(nativeDir, "libqjs.so")
        ffmpegBin = File(nativeDir, "libffmpeg.so")
        ffprobeBin = File(nativeDir, "libffprobe.so")
        aria2cBin = File(nativeDir, "libaria2c.so")

        initialized = true
    }

    fun buildEnvironment(context: Context): Map<String, String> {
        val appCtx = context.applicationContext
        init(appCtx)
        val nativeDir = File(appCtx.applicationInfo.nativeLibraryDir)
        val baseDir = File(appCtx.noBackupFilesDir, BASE_NAME)
        val packagesDir = File(baseDir, PACKAGES_DIR)

        val ldPaths = mutableListOf<String>()
        listOf("python", "ffmpeg", "aria2c").forEach { pkg ->
            val usrLib = File(File(packagesDir, pkg), "usr/lib")
            if (usrLib.exists()) ldPaths.add(usrLib.absolutePath)
        }
        // Make curl_cffi bundled libs & python extension modules resolvable
        val sitePackages = File(pythonUsrDir, "lib/python3.14/site-packages")
        ldPaths.add(sitePackages.absolutePath)
        ldPaths.add(File(sitePackages, "curl_cffi.libs").absolutePath)
        ldPaths.add(nativeDir.absolutePath)

        val env = mutableMapOf<String, String>()
        env["LD_LIBRARY_PATH"] = ldPaths.distinct().joinToString(":")
        env["PYTHONHOME"] = pythonUsrDir.absolutePath
        env["HOME"] = pythonUsrDir.absolutePath
        env["SSL_CERT_FILE"] = sslCertFile.absolutePath
        env["TMPDIR"] = appCtx.cacheDir.absolutePath
        env["PATH"] = (System.getenv("PATH") ?: "/system/bin") + ":" + nativeDir.absolutePath
        return env
    }

    /**
     * Execute yt-dlp with the given arguments.
     * Base arguments (ffmpeg location, JS runtimes, newline progress) are added by the caller.
     */
    fun execute(
        context: Context,
        args: List<String>,
        processId: String? = null,
        onLine: ((line: String) -> Unit)? = null,
        onProgress: ((Progress) -> Unit)? = null
    ): ExecResult {
        val env = buildEnvironment(context)
        val command = mutableListOf(pythonBin.absolutePath, ytdlpFile.absolutePath)
        command.addAll(args)

        val process = try {
            ProcessBuilder(command).apply {
                environment().clear()
                environment().putAll(env)
                redirectErrorStream(false)
            }.start()
        } catch (e: IOException) {
            throw EngineException("failed to start yt-dlp process", e)
        }

        if (processId != null) {
            idProcessMap[processId] = process
        }

        val outBuffer = StringBuffer()
        val errBuffer = StringBuffer()
        var lastFilePath: String? = null
        val parser = ProgressParser()

        val stdoutThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        synchronized(outBuffer) { outBuffer.append(line).append('\n') }
                        onLine?.invoke(line)
                        parser.parse(line)?.let { onProgress?.invoke(it) }
                        parser.extractPath(line)?.let { lastFilePath = it }
                    }
                }
            } catch (_: IOException) {
            }
        }
        val stderrThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        synchronized(errBuffer) { errBuffer.append(line).append('\n') }
                        onLine?.invoke(line)
                    }
                }
            } catch (_: IOException) {
            }
        }
        stdoutThread.start()
        stderrThread.start()

        val exitCode = try {
            stdoutThread.join()
            stderrThread.join()
            process.waitFor()
        } catch (e: InterruptedException) {
            process.destroy()
            if (processId != null) idProcessMap.remove(processId)
            throw e
        }

        val canceled = processId != null && !idProcessMap.containsKey(processId)
        // ConcurrentHashMap forbids null keys — only clean up when a process id was given
        if (processId != null) idProcessMap.remove(processId)

        return ExecResult(
            exitCode = exitCode,
            out = outBuffer.toString(),
            err = errBuffer.toString(),
            canceled = canceled,
            lastFilePath = lastFilePath
        )
    }

    /** Run a short python script with the bundled interpreter. Returns stdout. */
    fun runPython(context: Context, code: String): String {
        val env = buildEnvironment(context)
        val process = try {
            ProcessBuilder(pythonBin.absolutePath, "-c", code).apply {
                environment().clear()
                environment().putAll(env)
                redirectErrorStream(true)
            }.start()
        } catch (e: IOException) {
            throw EngineException("failed to start python process", e)
        }
        val output = StringBuilder()
        try {
            BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    output.append(line).append('\n')
                }
            }
            process.waitFor()
        } catch (_: IOException) {
        }
        return output.toString()
    }

    fun cancelProcess(id: String): Boolean {
        val p = idProcessMap.remove(id) ?: return false
        return try {
            p.destroy()
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Re-copy the bundled yt-dlp zipapp from APK raw resources. */
    fun reinstallYtdlp(context: Context) {
        val appCtx = context.applicationContext
        init(appCtx)
        val ytdlpDir = File(File(appCtx.noBackupFilesDir, BASE_NAME), YTDLP_DIR_NAME)
        if (!ytdlpDir.exists()) ytdlpDir.mkdirs()
        val binary = File(ytdlpDir, YTDLP_BIN_NAME)
        if (binary.exists()) binary.delete()
        try {
            appCtx.resources.openRawResource(
                appCtx.resources.getIdentifier("ytdlp", "raw", appCtx.packageName)
            ).use { input ->
                FileOutputStream(binary).use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            throw EngineException("failed to reinstall yt-dlp", e)
        }
    }

    /** Check curl_cffi (impersonation) availability with the bundled python. */
    fun impersonationAvailable(context: Context): Boolean {
        return try {
            val out = runPython(context, "import curl_cffi;print(curl_cffi.__version__)")
            out.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("UsableSpace")
    private fun extractIfNeeded(zipFile: File, targetDir: File, versionKey: String) {
        if (!zipFile.exists()) return // optional package
        val currentVersion = zipFile.length().toString()
        val installedVersion = prefs.getString(versionKey, null)
        if (!targetDir.exists() || installedVersion != currentVersion) {
            targetDir.deleteRecursively()
            targetDir.mkdirs()
            try {
                unzip(zipFile, targetDir)
                prefs.edit().putString(versionKey, currentVersion).apply()
            } catch (e: Exception) {
                targetDir.deleteRecursively()
                throw EngineException("failed to extract ${zipFile.name}", e)
            }
        }
    }

    /** Symlink-preserving unzip (Termux trees contain symlinks). */
    private fun unzip(sourceFile: File, targetDirectory: File) {
        ZipFile(sourceFile).use { zipFile ->
            val entries = zipFile.entries
            while (entries.hasMoreElements()) {
                val entry: ZipArchiveEntry = entries.nextElement()
                val entryDestination = File(targetDirectory, entry.name)
                if (!entryDestination.canonicalPath.startsWith(targetDirectory.canonicalPath + File.separator)) {
                    throw IllegalAccessException("Entry is outside of the target dir: " + entry.name)
                }
                if (entry.isDirectory) {
                    entryDestination.mkdirs()
                } else if (entry.isUnixSymlink) {
                    zipFile.getInputStream(entry).use { input ->
                        val symlink = input.readBytes().toString(StandardCharsets.UTF_8)
                        entryDestination.parentFile?.mkdirs()
                        try {
                            Os.symlink(symlink, entryDestination.absolutePath)
                        } catch (_: Exception) {
                            // Fall back to copying the target content if symlink fails
                        }
                    }
                } else {
                    entryDestination.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        FileOutputStream(entryDestination).use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }
}

/**
 * Parses yt-dlp progress output lines. Expects --newline so every progress update is its own line.
 */
class ProgressParser {

    private val downloadPattern = Pattern.compile(
        "\\[download\\]\\s+(\\d+(?:\\.\\d+)?)%\\s+of\\s+~?\\s*([\\d.]+)(\\w+)" +
            "(?:\\s+at\\s+([\\d.]+)(\\w+)/s)?(?:\\s+ETA\\s+(\\S+))?"
    )
    private val ariaPattern = Pattern.compile(
        "\\[#\\w{6}\\s.*\\((\\d+(?:\\.\\d+)?)%\\).*?((\\d+)MiB)*(\\d+)s*\\]"
    )
    private val destinationPattern = Pattern.compile("^\\[download\\] Destination: (.+)$")
    private val mergingPattern = Pattern.compile("^\\[Merger\\] Merging formats into \\\"(.+?)\\\"")
    private val audioDestPattern = Pattern.compile("^\\[ExtractAudio\\] Destination: (.+)$")

    var percent: Float = -1f
        private set
    var etaSeconds: Long = -1L
        private set
    var speedBps: Long = -1L
        private set
    var totalBytes: Long = -1L
        private set
    var downloadedBytes: Long = -1L
        private set

    fun parse(line: String): Progress? {
        val m = downloadPattern.matcher(line)
        if (m.find()) {
            percent = m.group(1)?.toFloat() ?: percent
            totalBytes = parseByteSize(m.group(2), m.group(3)) ?: totalBytes
            speedBps = parseByteSize(m.group(4), m.group(5)) ?: speedBps
            etaSeconds = parseEta(m.group(6)) ?: etaSeconds
            if (totalBytes > 0 && percent >= 0) {
                downloadedBytes = (totalBytes * percent / 100.0).toLong()
            }
            return Progress(percent, etaSeconds, speedBps, totalBytes, downloadedBytes, line)
        }
        val a = ariaPattern.matcher(line)
        if (a.find()) {
            percent = a.group(1)?.toFloat() ?: percent
            return Progress(percent, etaSeconds, speedBps, totalBytes, downloadedBytes, line)
        }
        return null
    }

    fun extractPath(line: String): String? {
        destinationPattern.matcher(line).let { if (it.find()) return it.group(1) }
        mergingPattern.matcher(line).let { if (it.find()) return it.group(1) }
        audioDestPattern.matcher(line).let { if (it.find()) return it.group(1) }
        return null
    }

    private fun parseByteSize(value: String?, unit: String?): Long? {
        if (value == null || unit == null) return null
        val v = value.toDoubleOrNull() ?: return null
        val mult = when (unit) {
            "B" -> 1.0
            "KiB" -> 1024.0
            "MiB" -> 1024.0 * 1024
            "GiB" -> 1024.0 * 1024 * 1024
            "TiB" -> 1024.0 * 1024 * 1024 * 1024
            "KB" -> 1000.0
            "MB" -> 1000.0 * 1000
            "GB" -> 1000.0 * 1000 * 1000
            else -> return null
        }
        return (v * mult).toLong()
    }

    private fun parseEta(eta: String?): Long? {
        if (eta == null) return null
        if (eta == "Unknown" || eta == "N/A") return -1L
        val parts = eta.split(":")
        if (parts.isEmpty()) return null
        return try {
            var seconds = 0L
            for (p in parts) {
                seconds = seconds * 60 + p.toLong()
            }
            seconds
        } catch (_: NumberFormatException) {
            null
        }
    }
}
