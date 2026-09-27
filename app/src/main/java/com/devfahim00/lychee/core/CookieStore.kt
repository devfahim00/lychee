package com.devfahim00.lychee.core

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Cookie jar storage for Lychee.
 *
 * Cookies are kept per website in Netscape format files (the format
 * yt-dlp expects for --cookies). A merged jar is produced on demand from
 * every enabled website whenever a download or info fetch starts.
 */
object CookieStore {

    private const val MERGED_NAME = "_merged.txt"
    private const val HEADER =
        "# Netscape HTTP Cookie File\n# https://curl.se/docs/http-cookies.html\n"

    private fun dir(context: Context): File =
        File(context.filesDir, "cookies").apply { mkdirs() }

    private fun safeName(domain: String): String =
        domain.replace(Regex("[^A-Za-z0-9.-]"), "_")

    fun jarFile(context: Context, domain: String): File =
        File(dir(context), safeName(domain) + ".txt")

    /** Saved websites, sorted alphabetically. */
    fun listDomains(context: Context): List<String> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".txt") && !f.name.startsWith("_") }
            ?.map { it.name.removeSuffix(".txt") }
            ?.sorted()
            ?: emptyList()

    fun countCookies(context: Context, domain: String): Int = try {
        jarFile(context, domain).readLines().count {
            it.isNotBlank() && !it.startsWith("#")
        }
    } catch (_: Exception) {
        0
    }

    fun delete(context: Context, domain: String) {
        jarFile(context, domain).delete()
    }

    /** Best-effort base domain, e.g. https://m.youtube.com/watch -> youtube.com */
    fun baseDomain(url: String): String {
        val normalized = if (url.startsWith("http")) url else "https://$url"
        val host = runCatching { Uri.parse(normalized).host }.getOrNull() ?: return url
        var h = host.lowercase()
        for (prefix in listOf("www.", "m.", "mobile.", "music.", "accounts.", "account.", "login.", "auth.")) {
            if (h.startsWith(prefix)) {
                h = h.removePrefix(prefix)
                break
            }
        }
        return h
    }

    /** Write a Netscape-format jar from name/value pairs captured in the browser. */
    fun saveJar(context: Context, domain: String, cookies: Map<String, String>) {
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val sb = StringBuilder(HEADER)
        for ((name, value) in cookies) {
            if (name.isBlank()) continue
            sb.append('.')
                .append(domain)
                .append("\tTRUE\t/\tTRUE\t")
                .append(expiry)
                .append('\t')
                .append(name)
                .append('\t')
                .append(value)
                .append('\n')
        }
        jarFile(context, domain).writeText(sb.toString())
    }

    /**
     * Build a single merged cookies file from all enabled websites, ready to
     * be passed to yt-dlp via --cookies. Returns null when cookies are off
     * or no website is enabled.
     */
    fun mergedCookieFile(context: Context, settings: Settings): File? {
        if (!settings.cookiesEnabled) return null
        val enabled = settings.cookiesEnabledDomains
        if (enabled.isEmpty()) return null
        val domains = listDomains(context).filter { it in enabled }
        if (domains.isEmpty()) return null

        val seen = HashSet<String>()
        val sb = StringBuilder(HEADER)
        for (domain in domains) {
            val file = jarFile(context, domain)
            if (!file.exists()) continue
            val lines = try {
                file.readLines()
            } catch (_: Exception) {
                continue
            }
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val parts = line.split('\t')
                if (parts.size < 7) continue
                // dedupe on domain + name + path
                if (seen.add(parts[0] + "|" + parts[5] + "|" + parts[2])) {
                    sb.append(line).append('\n')
                }
            }
        }
        if (seen.isEmpty()) return null
        val merged = File(dir(context), MERGED_NAME)
        merged.writeText(sb.toString())
        return merged
    }
}
