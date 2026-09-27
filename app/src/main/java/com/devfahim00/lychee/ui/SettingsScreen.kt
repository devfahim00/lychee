package com.devfahim00.lychee.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devfahim00.lychee.core.Settings
import com.devfahim00.lychee.core.Updater
import com.devfahim00.lychee.ui.components.GlassCard
import com.devfahim00.lychee.ui.components.GlassTextField
import com.devfahim00.lychee.ui.components.GradientButton
import com.devfahim00.lychee.ui.theme.LycheePink
import com.devfahim00.lychee.ui.theme.TextPrimary
import com.devfahim00.lychee.ui.theme.TextSecondary
import com.devfahim00.lychee.ui.theme.TextTertiary

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val engine by vm.engineStatus.collectAsState()

    val updateAvailable = engine.updateInfo?.let { info ->
        engine.ytdlpVersion.removeSuffix(" (bundled)") != info.tag
    } ?: false

    var showChannelPicker by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showImpersonatePicker by remember { mutableStateOf(false) }
    var showQualityPicker by remember { mutableStateOf(false) }

    fun update(transform: (Settings) -> Settings) = vm.updateSettings(transform)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(14.dp))

        // ---------- yt-dlp engine ----------
        SectionTitle("yt-dlp engine")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                InfoRow("yt-dlp version", engine.ytdlpVersion.ifBlank { "checking…" })
                InfoRow(
                    "Impersonation (curl_cffi)",
                    when (engine.impersonationAvailable) {
                        true -> "available ✓"
                        false -> "unavailable ✗"
                        null -> "checking…"
                    }
                )
                InfoRow("QuickJS (yt-dlp-ejs)", if (engine.quickjsAvailable) "available ✓" else "missing")

                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable { showChannelPicker = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.CloudDownload, null, tint = LycheePink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Update channel", color = TextPrimary, fontSize = 14.sp)
                        Text(
                            "Nightly recommended by yt-dlp for regular users",
                            color = TextTertiary, fontSize = 11.sp
                        )
                    }
                    Text(Updater.Channels.label(settings.updateChannel), color = TextSecondary, fontSize = 13.sp)
                    Icon(Icons.Filled.ArrowDropDown, null, tint = TextTertiary)
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton(
                        onClick = { vm.checkForUpdate() },
                        text = if (engine.checkingUpdate) "Checking…" else "Check",
                        modifier = Modifier.weight(1f),
                        enabled = !engine.checkingUpdate && !engine.updating
                    )
                    GradientButton(
                        onClick = { vm.performUpdate() },
                        text = if (engine.updating) "Updating…" else "Update now",
                        modifier = Modifier.weight(1f),
                        enabled = engine.updateInfo != null && !engine.updating && updateAvailable
                    )
                }
                engine.updateResult?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = TextSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = { vm.resetToBundled() }) {
                    Icon(Icons.Filled.Restore, null, modifier = Modifier.size(16.dp), tint = TextTertiary)
                    Spacer(Modifier.width(6.dp))
                    Text("Reset to bundled version", color = TextTertiary, fontSize = 12.sp)
                }
            }
        }

        // ---------- Impersonation ----------
        Spacer(Modifier.height(18.dp))
        SectionTitle("Impersonation")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                ToggleRow(
                    title = "Enable impersonation",
                    subtitle = "Browser TLS fingerprint spoofing (curl_cffi)",
                    checked = settings.impersonateEnabled,
                    onChecked = { update { it.copy(impersonateEnabled = !it.impersonateEnabled) } }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable { showImpersonatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.Security, null, tint = LycheePink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Target client",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(settings.impersonateTarget, color = TextSecondary, fontSize = 13.sp)
                    Icon(Icons.Filled.ArrowDropDown, null, tint = TextTertiary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Note: forcing impersonation for all requests may impact speed and stability (yt-dlp docs). Leave off unless a site requires it.",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // ---------- Download defaults ----------
        Spacer(Modifier.height(18.dp))
        SectionTitle("Download defaults")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                PickerRow("Default quality", qualityLabel(settings.videoQualityCap)) { showQualityPicker = true }
                PickerRow("Audio format", settings.audioFormat.uppercase()) { showAudioPicker = true }
                ToggleRow("Use aria2c downloader", "16 connections per server (experimental)", settings.useAria2c) {
                    update { it.copy(useAria2c = !it.useAria2c) }
                }
                ToggleRow("Embed metadata", null, settings.embedMetadata) {
                    update { it.copy(embedMetadata = !it.embedMetadata) }
                }
                ToggleRow("Embed thumbnail", null, settings.embedThumbnail) {
                    update { it.copy(embedThumbnail = !it.embedThumbnail) }
                }
                ToggleRow("Download subtitles", null, settings.writeSubtitles) {
                    update { it.copy(writeSubtitles = !it.writeSubtitles) }
                }
                if (settings.writeSubtitles) {
                    GlassTextField(
                        value = settings.subtitleLanguages,
                        onValueChange = { s -> update { it.copy(subtitleLanguages = s) } },
                        placeholder = "Subtitle languages (e.g. en.*,bn)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Embed subtitles into video", null, settings.embedSubtitles) {
                        update { it.copy(embedSubtitles = !it.embedSubtitles) }
                    }
                }
                ToggleRow("Remove SponsorBlock segments", "Cuts out sponsor segments", settings.removeSponsorBlock) {
                    update { it.copy(removeSponsorBlock = !it.removeSponsorBlock) }
                }
                ToggleRow("Restrict filenames", "ASCII-only safe filenames", settings.restrictFilenames) {
                    update { it.copy(restrictFilenames = !it.restrictFilenames) }
                }
            }
        }

        // ---------- Storage ----------
        Spacer(Modifier.height(18.dp))
        SectionTitle("Storage")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Icon(Icons.Filled.Folder, null, tint = LycheePink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(8.dp))
                GlassTextField(
                    value = settings.dirPath,
                    onValueChange = { s -> update { it.copy(dirPath = s) } },
                    placeholder = "Download directory",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Filename template", color = TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                GlassTextField(
                    value = settings.template,
                    onValueChange = { s -> update { it.copy(template = s) } },
                    placeholder = "Output template",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Playlist template", color = TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                GlassTextField(
                    value = settings.playlistTemplate,
                    onValueChange = { s -> update { it.copy(playlistTemplate = s) } },
                    placeholder = "Playlist output template",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ---------- Network ----------
        Spacer(Modifier.height(18.dp))
        SectionTitle("Network")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GlassTextField(
                    value = settings.proxyUrl,
                    onValueChange = { s -> update { it.copy(proxyUrl = s) } },
                    placeholder = "Proxy (e.g. socks5://127.0.0.1:9050)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                GlassTextField(
                    value = settings.extraArgs,
                    onValueChange = { s -> update { it.copy(extraArgs = s) } },
                    placeholder = "Extra yt-dlp arguments",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Advanced: appended verbatim to every yt-dlp call. Quote values containing spaces.",
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
        }

        // ---------- About ----------
        Spacer(Modifier.height(18.dp))
        SectionTitle("About")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                InfoRow("App", "Lychee 0.1.0")
                InfoRow("Engine", "yt-dlp + Python 3.14")
                InfoRow("License", "GPL-3.0")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Built on the shoulders of yt-dlp (Unlicense), YTDLnis & youtubedl-android (GPL-3.0/Apache-2.0), ffmpeg, aria2c, QuickJS and curl_cffi (MIT).\n\nUse responsibly and respect the terms of service of the platforms you download from.",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }

    if (showChannelPicker) {
        OptionPickerDialog(
            title = "Update channel",
            options = listOf(
                Triple("Stable", "Monthly releases, most tested", "stable"),
                Triple("Nightly", "Recommended by yt-dlp for regular users", "nightly"),
                Triple("Master", "Bleeding edge after each push", "master")
            ),
            selected = settings.updateChannel,
            onSelect = { v -> update { it.copy(updateChannel = v) } },
            onDismiss = { showChannelPicker = false }
        )
    }
    if (showAudioPicker) {
        OptionPickerDialog(
            title = "Audio format",
            options = listOf("m4a", "mp3", "opus", "flac", "wav").map {
                Triple(it.uppercase(), "", it)
            },
            selected = settings.audioFormat,
            onSelect = { v -> update { it.copy(audioFormat = v) } },
            onDismiss = { showAudioPicker = false }
        )
    }
    if (showQualityPicker) {
        OptionPickerDialog(
            title = "Default quality",
            options = listOf(
                Triple("Best", "No height limit", "0"),
                Triple("2160p", "4K", "2160"),
                Triple("1440p", "2K", "1440"),
                Triple("1080p", "Full HD", "1080"),
                Triple("720p", "HD", "720"),
                Triple("480p", "SD", "480")
            ),
            selected = settings.videoQualityCap.toString(),
            onSelect = { v -> update { it.copy(videoQualityCap = v.toIntOrNull() ?: 1080) } },
            onDismiss = { showQualityPicker = false }
        )
    }
    if (showImpersonatePicker) {
        OptionPickerDialog(
            title = "Impersonation target",
            options = listOf(
                Triple("chrome", "Latest Chrome (recommended)", "chrome"),
                Triple("edge", "Latest Edge", "edge"),
                Triple("safari", "Latest Safari", "safari"),
                Triple("safari-ios", "Latest Safari iOS", "safari-ios"),
                Triple("firefox", "Latest Firefox", "firefox"),
                Triple("chrome-131", "Chrome 131 (pin version)", "chrome-131"),
                Triple("safari-17-0", "Safari 17.0 (pin version)", "safari-17-0")
            ),
            selected = settings.impersonateTarget,
            onSelect = { v -> update { it.copy(impersonateTarget = v) } },
            onDismiss = { showImpersonatePicker = false }
        )
    }
}

private fun qualityLabel(cap: Int): String = if (cap <= 0) "Best" else "${cap}p"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = TextTertiary,
        fontSize = 11.sp,
        letterSpacing = 1.6.sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChecked: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(title, color = TextPrimary, fontSize = 14.sp)
            if (subtitle != null) {
                Text(subtitle, color = TextTertiary, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onChecked() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = LycheePink,
                checkedThumbColor = Color.White
            )
        )
    }
}

@Composable
private fun PickerRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextSecondary, fontSize = 13.sp)
        Icon(Icons.Filled.ArrowDropDown, null, tint = TextTertiary)
    }
}

@Composable
private fun OptionPickerDialog(
    title: String,
    options: List<Triple<String, String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        GlassCard(Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
            Column(Modifier.padding(18.dp)) {
                Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                options.forEach { (label, subtitle, value) ->
                    val isSelected = value == selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.14f)
                                else Color.White.copy(alpha = 0.04f)
                            )
                            .clickable { onSelect(value); onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(
                                    if (isSelected) LycheePink else Color.Transparent,
                                    CircleShape
                                )
                                .padding(2.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(label, color = TextPrimary, fontSize = 14.sp)
                            if (subtitle.isNotBlank()) {
                                Text(subtitle, color = TextTertiary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
