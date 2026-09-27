package com.devfahim00.lychee.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.devfahim00.lychee.core.VideoFormat
import com.devfahim00.lychee.core.VideoInfo
import com.devfahim00.lychee.ui.theme.LycheePink
import com.devfahim00.lychee.ui.theme.LycheeViolet
import com.devfahim00.lychee.ui.theme.TextPrimary
import com.devfahim00.lychee.ui.theme.TextSecondary
import com.devfahim00.lychee.ui.theme.TextTertiary
import com.devfahim00.lychee.util.Fmt

/**
 * Glass format-selection bottom sheet. Produces a yt-dlp format selector string.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSheet(
    info: VideoInfo,
    onDismiss: () -> Unit,
    onConfirm: (formatSelector: String, audioOnly: Boolean, playlist: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedVideo by remember { mutableStateOf<String?>(null) } // "best" or formatId
    var audioOnly by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf(false) }

    // Group video formats by quality bucket
    val videoFormats = remember(info) {
        info.formats.filter { !it.isAudioOnly }.sortedWith(
            compareByDescending<VideoFormat> { it.height ?: 0 }.thenByDescending { it.fps ?: 0.0 }
        )
    }
    val audioFormats = remember(info) {
        info.formats.filter { it.isAudioOnly }.sortedByDescending { it.abr ?: it.tbr ?: 0.0 }
    }
    val combinedFormats = videoFormats.filter { !it.isVideoOnly }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xF7161126),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // header: thumbnail + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 108.dp, height = 62.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    info.thumbnail?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.size(width = 108.dp, height = 62.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        info.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    val meta = listOfNotNull(
                        info.uploader,
                        Fmt.duration(info.duration).ifEmpty { null },
                        info.extractor
                    ).joinToString(" • ")
                    Text(meta, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (info.isPlaylist) {
                Spacer(Modifier.height(14.dp))
                SheetToggleRow(
                    title = "Download whole playlist",
                    subtitle = "${info.playlistCount} items",
                    checked = playlist,
                    onChecked = { playlist = it }
                )
            }

            Spacer(Modifier.height(18.dp))

            // Recommended / best
            SectionLabel("Recommended")
            Spacer(Modifier.height(8.dp))
            FormatRow(
                icon = { Icon(Icons.Filled.HighQuality, null, tint = Color(0xFFB69CFF)) },
                title = "Best available",
                subtitle = "Optimal video + audio",
                selected = selectedVideo == null && !audioOnly,
                onClick = { selectedVideo = null; audioOnly = false }
            )

            // Video formats
            if (videoFormats.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionLabel("Video")
                Spacer(Modifier.height(8.dp))
                videoFormats.take(12).forEach { f ->
                    val label = buildString {
                        append(f.height?.let { "${it}p" } ?: f.resolution ?: "video")
                        f.fps?.let { if (it > 30) append(" ${it.toInt()}fps") }
                        f.ext?.let { append(" • $it") }
                        if (f.isVideoOnly) append(" • video only")
                    }
                    val sub = buildString {
                        Fmt.bytes(f.sizeBytes).ifEmpty { null }?.let { append(it) }
                        f.tbr?.let { if (isEmpty()) append("${it.toInt()} kbps") else append(" • ${it.toInt()} kbps") }
                        if (isEmpty()) append(f.formatNote ?: "")
                    }
                    FormatRow(
                        icon = { Icon(Icons.Filled.Videocam, null, tint = LycheePink) },
                        title = label,
                        subtitle = sub,
                        selected = selectedVideo == f.formatId,
                        onClick = { selectedVideo = f.formatId; audioOnly = false }
                    )
                }
            }

            // Audio only
            Spacer(Modifier.height(18.dp))
            SectionLabel("Audio only")
            Spacer(Modifier.height(8.dp))
            val bestAudioLabel = audioFormats.firstOrNull()?.let { f ->
                buildString {
                    append("Best audio")
                    f.ext?.let { append(" • $it") }
                    f.abr?.let { append(" • ${it.toInt()} kbps") }
                }
            } ?: "Best audio"
            FormatRow(
                icon = { Icon(Icons.Filled.Audiotrack, null, tint = Color(0xFF6DFFB8)) },
                title = bestAudioLabel,
                subtitle = "Extract & convert to audio file",
                selected = audioOnly,
                onClick = { audioOnly = !audioOnly; if (audioOnly) selectedVideo = null }
            )

            Spacer(Modifier.height(22.dp))

            GradientButton(
                onClick = {
                    val selector = when {
                        audioOnly -> "ba/b"
                        selectedVideo == null -> "bv*+ba/b"
                        else -> {
                            val fmt = videoFormats.firstOrNull { it.formatId == selectedVideo }
                            if (fmt?.isVideoOnly == true) "$selectedVideo+ba/b" else selectedVideo!!
                        }
                    }
                    onConfirm(selector, audioOnly, playlist)
                },
                text = "Start download",
                modifier = Modifier.fillMaxWidth(),
                leading = { Icon(Icons.Filled.MusicNote.takeIf { audioOnly } ?: Icons.Filled.Videocam, null) }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = TextTertiary,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun FormatRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(
                if (selected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f)
            )
            .border(
                width = 1.dp,
                brush = if (selected) {
                    Brush.linearGradient(listOf(LycheePink, LycheeViolet))
                } else {
                    Brush.linearGradient(
                        listOf(Color.White.copy(0.18f), Color.White.copy(0.05f))
                    )
                },
                shape = shape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, null, tint = LycheePink, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun SheetToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = LycheePink,
                checkedThumbColor = Color.White
            )
        )
    }
}
