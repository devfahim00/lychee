package com.devfahim00.lychee.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devfahim00.lychee.R
import com.devfahim00.lychee.core.DownloadStatus
import com.devfahim00.lychee.ui.components.BrandText
import com.devfahim00.lychee.ui.components.FormatSheet
import com.devfahim00.lychee.ui.components.GlassCard
import com.devfahim00.lychee.ui.components.GlassTextField
import com.devfahim00.lychee.ui.components.GradientButton
import com.devfahim00.lychee.ui.theme.LycheePink
import com.devfahim00.lychee.ui.theme.LycheeViolet
import com.devfahim00.lychee.ui.theme.TextSecondary
import com.devfahim00.lychee.ui.theme.TextTertiary
import com.devfahim00.lychee.util.Fmt

@Composable
fun HomeScreen(vm: AppViewModel, sharedUrl: String?) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val fetchState by vm.fetchState.collectAsState()
    val tasks by vm.tasks.collectAsState()

    var url by remember { mutableStateOf("") }
    var showSheet by remember { mutableStateOf(false) }

    LaunchedEffect(sharedUrl) {
        if (!sharedUrl.isNullOrBlank() && url.isBlank()) {
            url = sharedUrl
        }
    }

    val activeTasks = tasks.filter {
        it.status == DownloadStatus.ACTIVE || it.status == DownloadStatus.QUEUED
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))

        // Brand header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFFF7BA3), Color(0xFFB14CFF)),
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(58.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            BrandText("Lychee", 30)
        }

        Spacer(Modifier.height(20.dp))

        // Storage permission banner
        if (!Environment.isExternalStorageManager()) {
            GlassCard(Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Color(0xFFFFC46B))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Allow access to save files",
                            color = Color(0xFFFFE3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    TextButton(onClick = {
                        runCatching {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    }) { Text("Grant", color = LycheePink, fontWeight = FontWeight.SemiBold) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // URL card
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GlassTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "Paste a video or audio link",
                    modifier = Modifier.fillMaxWidth(),
                    leading = {
                        Icon(Icons.Filled.Link, null, tint = TextTertiary)
                    },
                    trailing = {
                        Row {
                            if (url.isNotEmpty()) {
                                IconButton(onClick = { url = "" }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Close, "Clear", tint = TextTertiary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            IconButton(onClick = {
                                val text = clipboard.getText()?.text ?: ""
                                val line = text.lines().firstOrNull { Fmt.looksLikeUrl(it) }
                                url = line ?: text.trim()
                            }) {
                                Icon(Icons.Filled.ContentPaste, "Paste", tint = LycheePink)
                            }
                        }
                    }
                )

                Spacer(Modifier.height(14.dp))

                when (val fs = fetchState) {
                    is FetchState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = LycheePink
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Fetching video info…", color = TextSecondary, fontSize = 14.sp)
                        }
                    }
                    is FetchState.Error -> {
                        Text(
                            "⚠ ${fs.message}",
                            color = Color(0xFFFF8A9B),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    else -> Unit
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GradientButton(
                        onClick = {
                            if (Fmt.looksLikeUrl(url)) {
                                vm.fetchInfo(url)
                            } else {
                                Toast.makeText(context, "Please paste a valid link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = url.isNotBlank() && fetchState !is FetchState.Loading,
                        text = "Fetch formats",
                        modifier = Modifier.weight(1f),
                        leading = { Icon(Icons.Filled.RocketLaunch, null, modifier = Modifier.size(18.dp)) }
                    )
                    GradientButton(
                        onClick = {
                            if (Fmt.looksLikeUrl(url)) {
                                vm.startDownload(
                                    com.devfahim00.lychee.core.DownloadOptions(
                                        url = url.trim(),
                                        formatSelector = "bv*+ba/b",
                                        audioOnly = false,
                                        playlist = false,
                                        title = "Download"
                                    )
                                )
                                Toast.makeText(context, "Download queued", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please paste a valid link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = url.isNotBlank() && fetchState !is FetchState.Loading,
                        text = "Quick",
                        modifier = Modifier.weight(0.55f),
                        leading = { Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
        }

        // Active downloads preview
        if (activeTasks.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Active downloads",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            activeTasks.take(3).forEach { task ->
                MiniTaskRow(task)
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showSheet) {
        val info = (fetchState as? FetchState.Success)?.info
        if (info != null) {
            FormatSheet(
                info = info,
                onDismiss = { showSheet = false },
                onConfirm = { selector, audioOnly, playlist ->
                    showSheet = false
                    vm.startDownload(
                        com.devfahim00.lychee.core.DownloadOptions(
                            url = info.webpageUrl ?: url.trim(),
                            formatSelector = selector,
                            audioOnly = audioOnly,
                            playlist = playlist,
                            title = info.title
                        )
                    )
                    vm.resetFetch()
                    url = ""
                    Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    LaunchedEffect(fetchState) {
        if (fetchState is FetchState.Success && !showSheet) {
            showSheet = true
        }
    }
}

@Composable
private fun MiniTaskRow(task: com.devfahim00.lychee.core.DownloadTask) {
    GlassCard(Modifier.fillMaxWidth(), cornerRadius = 16.dp, specular = false) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                task.title.ifBlank { task.url },
                color = Color(0xFFF6F0FA),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val progress = task.progress
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                ) {
                    if (progress >= 0) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress / 100f)
                                .height(5.dp)
                                .background(
                                    Brush.horizontalGradient(listOf(LycheePink, LycheeViolet)),
                                    CircleShape
                                )
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (progress >= 0) "${progress.toInt()}%" else task.stage.ifBlank { "queued" },
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
