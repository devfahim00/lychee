package com.devfahim00.lychee.util

import android.content.Intent
import android.net.Uri
import java.util.Locale

object Fmt {

    fun duration(seconds: Long?): String {
        if (seconds == null || seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun bytes(bytes: Long): String {
        if (bytes <= 0) return ""
        return when {
            bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)
            bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
            bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    fun speed(bps: Long): String = if (bps <= 0) "" else bytes(bps) + "/s"

    fun eta(seconds: Long): String {
        if (seconds <= 0) return ""
        return duration(seconds)
    }

    fun looksLikeUrl(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.contains(' ')) return false
        return t.startsWith("http://") || t.startsWith("https://") ||
            (t.startsWith("www.") && !t.contains(' ')) ||
            (!t.contains(' ') && t.contains('.') && !t.startsWith(".") && !t.endsWith("."))
    }

    fun openUrl(intent: Intent): String? {
        return when (intent.action) {
            Intent.ACTION_SEND ->
                intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            else -> null
        }?.trim()?.lines()?.firstOrNull { it.isNotBlank() }
    }
}
