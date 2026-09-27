package com.devfahim00.lychee.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devfahim00.lychee.R
import com.devfahim00.lychee.core.DownloadStatus
import com.devfahim00.lychee.core.DownloadTask
import com.devfahim00.lychee.ui.components.GlassCard
import com.devfahim00.lychee.ui.theme.LycheePink
import com.devfahim00.lychee.ui.theme.LycheeViolet
import com.devfahim00.lychee.ui.theme.TextPrimary
import com.devfahim00.lychee.ui.theme.TextSecondary
import com.devfahim00.lychee.ui.theme.TextTertiary
import com.devfahim00.lychee.util.Fmt
import java.io.File

@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val tasks by vm.tasks.collectAsState()
    val context = LocalContext.current

    var logTask by remember { mutableStateOf<DownloadTask?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Downloads",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            if (tasks.any { it.status != DownloadStatus.ACTIVE && it.status != DownloadStatus.QUEUED }) {
                TextButton(onClick = { vm.clearFinished() }) {
                    Text("Clear finished", color = LycheePink)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (tasks.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(110.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Nothing here yet",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Paste a link on the Home tab to start\nyour first download 🍒",
                    color = TextTertiary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 19.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val ordered = tasks.sortedWith(
                    compareBy<DownloadTask> {
                        when (it.status) {
                            DownloadStatus.ACTIVE -> 0
                            DownloadStatus.QUEUED -> 1
                            else -> 2
                        }
                    }.thenByDescending { it.createdAt }
                )
                items(ordered, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onCancel = { vm.cancelDownload(task.id) },
                        onRemove = { vm.removeDownload(task.id) },
                        onOpen = { path -> openFile(context, path) },
                        onViewLog = { logTask = task }
                    )
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    logTask?.let { task ->
        LogDialog(task = task, onDismiss = { logTask = null })
    }
}

@Composable
private fun TaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onOpen: (String) -> Unit,
    onViewLog: () -> Unit
) {
    GlassCard(Modifier.fillMaxWidth(), cornerRadius = 20.dp, specular = task.status == DownloadStatus.ACTIVE) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusColor = when (task.status) {
                    DownloadStatus.ACTIVE -> LycheePink
                    DownloadStatus.QUEUED -> TextTertiary
                    DownloadStatus.COMPLETED -> Color(0xFF5EE6A8)
                    DownloadStatus.ERROR -> Color(0xFFFF6B81)
                    DownloadStatus.CANCELED -> Color(0xFF9A8FB0)
                }
                Icon(
                    imageVector = when (task.status) {
                        DownloadStatus.ACTIVE, DownloadStatus.QUEUED ->
                            if (task.audioOnly) Icons.Filled.Audiotrack else Icons.Filled.Download
                        DownloadStatus.COMPLETED -> Icons.Filled.CheckCircle
                        DownloadStatus.ERROR -> Icons.Filled.Error
                        DownloadStatus.CANCELED -> Icons.Filled.Cancel
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title.ifBlank { task.url },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when (task.status) {
                        DownloadStatus.ACTIVE -> buildString {
                            if (task.stage.isNotBlank()) append(task.stage)
                            val s = Fmt.speed(task.speedBps)
                            if (s.isNotBlank()) append(" • $s")
                            val e = Fmt.eta(task.etaSeconds)
                            if (e.isNotBlank()) append(" • ETA $e")
                        }
                        DownloadStatus.QUEUED -> "Queued"
                        DownloadStatus.COMPLETED -> "Saved" + (task.filePath?.let { " • ${File(it).name}" } ?: "")
                        DownloadStatus.ERROR -> task.error ?: "Failed"
                        DownloadStatus.CANCELED -> "Canceled"
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            color = if (task.status == DownloadStatus.ERROR) Color(0xFFFF8A9B) else TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                when (task.status) {
                    DownloadStatus.ACTIVE, DownloadStatus.QUEUED -> {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Cancel, "Cancel", tint = Color(0xFFFF8A9B))
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        Row {
                            if (task.filePath != null) {
                                IconButton(onClick = { task.filePath?.let(onOpen) }) {
                                    Icon(Icons.Filled.OpenInNew, "Open", tint = LycheePink)
                                }
                            }
                            IconButton(onClick = onRemove) {
                                Icon(Icons.Filled.Delete, "Remove", tint = TextTertiary)
                            }
                        }
                    }
                    DownloadStatus.ERROR, DownloadStatus.CANCELED -> {
                        Row {
                            IconButton(onClick = onViewLog) {
                                Icon(Icons.Filled.Error, "Log", tint = TextTertiary)
                            }
                            IconButton(onClick = onRemove) {
                                Icon(Icons.Filled.Delete, "Remove", tint = TextTertiary)
                            }
                        }
                    }
                }
            }

            if (task.status == DownloadStatus.ACTIVE || task.status == DownloadStatus.QUEUED) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    if (task.progress >= 0) {
                        Box(
                            Modifier
                                .fillMaxWidth((task.progress / 100f).coerceIn(0.02f, 1f))
                                .height(6.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(LycheePink, LycheeViolet)),
                                    CircleShape
                                )
                        )
                    }
                    if (task.status == DownloadStatus.QUEUED) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.15f)
                                .height(6.dp)
                                .background(TextTertiary.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }
                if (task.progress >= 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("${task.progress.toInt()}%", color = TextTertiary, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun LogDialog(task: DownloadTask, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        GlassCard(Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
            Column(Modifier.padding(18.dp)) {
                Text(
                    "Log — ${task.title.ifBlank { task.url }}",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    (task.error?.let { e -> "Error: $e\n\n" } ?: "") + task.log.ifBlank { "No output captured" },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .height(280.dp)
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", color = LycheePink)
                }
            }
        }
    }
}

private fun Modifier.verticalScrollLog(): Modifier = this

private fun openFile(context: android.content.Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "File not found: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = when (file.extension.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open file", Toast.LENGTH_SHORT).show()
    }
}
