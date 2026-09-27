package com.devfahim00.lychee.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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

private enum class Section(
    val title: String,
    val icon: ImageVector,
    val subtitle: String
) {
    ENGINE("yt-dlp engine", Icons.Filled.SystemUpdate, "Version, updates & components"),
    DOWNLOADS("Download defaults", Icons.Filled.Tune, "Quality, audio format & processing"),
    IMPERSONATION("Impersonation", Icons.Filled.Security, "Browser fingerprint spoofing"),
    STORAGE("Storage", Icons.Filled.Folder, "Save location & filenames"),
    NETWORK("Network", Icons.Filled.Language, "Proxy & advanced arguments"),
    ABOUT("About", Icons.Filled.Info, "Version & licenses")
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsState()
    val engine by vm.engineStatus.collectAsState()

    var section by remember { mutableStateOf<Section?>(null) }

    BackHandler(enabled = section != null) { section = null }

    fun update(transform: (Settings) -> Settings) = vm.updateSettings(transform)

    AnimatedContent(
        targetState = section,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 5 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { it / 5 } + fadeOut())
            }
        },
        label = "settingsSection"
    ) { current ->
        when (current) {
            null -> SettingsRoot(
                settings = settings,
                engine = engine,
                onOpen = { section = it }
            )
            Section.ENGINE -> EnginePage(
                settings = settings,
                engine = engine,
                onBack = { section = null },
                update = ::update,
                vm = vm
            )
            Section.DOWNLOADS -> DownloadsPage(
                settings = settings,
                onBack = { section = null },
                update = ::update
            )
            Section.IMPERSONATION -> ImpersonationPage(
                settings = settings,
                onBack = { section = null },
                update = ::update
            )
            Section.STORAGE -> StoragePage(
                settings = settings,
                onBack = { section = null },
                update = ::update
            )
            Section.NETWORK -> NetworkPage(
                settings = settings,
                onBack = { section = null },
                update = ::update
            )
            Section.ABOUT -> AboutPage(onBack = { section = null })
        }
    }
}

// ---------------------------------------------------------------- root ----

@Composable
private fun SettingsRoot(
    settings: Settings,
    engine: com.devfahim00.lychee.ui.EngineStatus,
    onOpen: (Section) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.height(16.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 6.dp)) {
                Section.entries.forEachIndexed { index, s ->
                    val value = when (s) {
                        Section.ENGINE -> engine.ytdlpVersion.ifBlank { "checking…" }
                        Section.DOWNLOADS -> qualityLabel(settings.videoQualityCap)
                        Section.IMPERSONATION -> if (settings.impersonateEnabled) settings.impersonateTarget else "Off"
                        Section.STORAGE -> null
                        Section.NETWORK -> null
                        Section.ABOUT -> "0.2.0"
                    }
                    SettingsRow(
                        icon = s.icon,
                        title = s.title,
                        subtitle = s.subtitle,
                        value = value,
                        onClick = { onOpen(s) }
                    )
                    if (index != Section.entries.size - 1) {
                        SettingsRowDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String?,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = LycheePink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextTertiary, fontSize = 12.sp)
        }
        if (value != null) {
            Text(
                value,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TextTertiary)
    }
}

@Composable
private fun SettingsRowDivider() {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f))
    )
}

// ------------------------------------------------------------- sub-page ----

@Composable
private fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
            }
            Spacer(Modifier.width(6.dp))
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(14.dp))
        content()
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun PageSectionLabel(text: String) {
    Text(
        text,
        color = TextTertiary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp
    )
}

// ----------------------------------------------------------- engine page ----

@Composable
private fun EnginePage(
    settings: Settings,
    engine: com.devfahim00.lychee.ui.EngineStatus,
    onBack: () -> Unit,
    update: ((Settings) -> Settings) -> Unit,
    vm: AppViewModel
) {
    val updateAvailable = engine.updateInfo?.let { info ->
        engine.ytdlpVersion.removeSuffix(" (bundled)") != info.tag
    } ?: false

    var showChannelPicker by remember { mutableStateOf(false) }

    SettingsSubPage("yt-dlp engine", onBack) {
        PageSectionLabel("Version")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoRow("yt-dlp", engine.ytdlpVersion.ifBlank { "checking…" })
                InfoRow(
                    "Impersonation",
                    when (engine.impersonationAvailable) {
                        true -> "available"
                        false -> "unavailable"
                        null -> "checking…"
                    }
                )
                InfoRow("JavaScript runtime", if (engine.quickjsAvailable) "QuickJS" else "not found")
            }
        }

        Spacer(Modifier.height(20.dp))
        PageSectionLabel("Updates")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable { showChannelPicker = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Filled.CloudDownload, null, tint = LycheePink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Update channel", color = TextPrimary, fontSize = 14.sp)
                        Text(
                            "Nightly is recommended by yt-dlp",
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
                        text = if (engine.checkingUpdate) "Checking…" else "Check for updates",
                        modifier = Modifier.weight(1f),
                        enabled = !engine.checkingUpdate && !engine.updating
                    )
                }
                if (updateAvailable) {
                    Spacer(Modifier.height(8.dp))
                    GradientButton(
                        onClick = { vm.performUpdate() },
                        text = if (engine.updating) "Updating…" else "Update now",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = engine.updateInfo != null && !engine.updating
                    )
                }
                engine.updateResult?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
                TextButton(onClick = { vm.resetToBundled() }) {
                    Icon(Icons.Filled.Restore, null, modifier = Modifier.size(16.dp), tint = TextTertiary)
                    Spacer(Modifier.width(6.dp))
                    Text("Restore bundled version", color = TextTertiary, fontSize = 12.sp)
                }
            }
        }
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
}

// --------------------------------------------------------- downloads page ----

@Composable
private fun DownloadsPage(
    settings: Settings,
    onBack: () -> Unit,
    update: ((Settings) -> Settings) -> Unit
) {
    var showAudioPicker by remember { mutableStateOf(false) }
    var showQualityPicker by remember { mutableStateOf(false) }

    SettingsSubPage("Download defaults", onBack) {
        PageSectionLabel("Quality & format")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerRow("Default quality", qualityLabel(settings.videoQualityCap)) { showQualityPicker = true }
                PickerRow("Audio format", settings.audioFormat.uppercase()) { showAudioPicker = true }
                ToggleRow("Parallel fragments", "Download N fragments at once", settings.concurrentFragments > 1) {
                    val next = if (settings.concurrentFragments > 1) 1 else 4
                    update { it.copy(concurrentFragments = next) }
                }
                ToggleRow("Use aria2c downloader", "16 connections per server (experimental)", settings.useAria2c) {
                    update { it.copy(useAria2c = !it.useAria2c) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        PageSectionLabel("Post-processing")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow("Embed metadata", "Title, artist and other tags", settings.embedMetadata) {
                    update { it.copy(embedMetadata = !it.embedMetadata) }
                }
                ToggleRow("Embed thumbnail", "Attach cover art when possible", settings.embedThumbnail) {
                    update { it.copy(embedThumbnail = !it.embedThumbnail) }
                }
                ToggleRow("Remove sponsor segments", "Skip sponsored sections", settings.removeSponsorBlock) {
                    update { it.copy(removeSponsorBlock = !it.removeSponsorBlock) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        PageSectionLabel("Subtitles")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow("Download subtitles", null, settings.writeSubtitles) {
                    update { it.copy(writeSubtitles = !it.writeSubtitles) }
                }
                if (settings.writeSubtitles) {
                    Column {
                        Text("Languages", color = TextPrimary, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        GlassTextField(
                            value = settings.subtitleLanguages,
                            onValueChange = { s -> update { it.copy(subtitleLanguages = s) } },
                            placeholder = "e.g. en.*,bn",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ToggleRow("Embed into video", null, settings.embedSubtitles) {
                        update { it.copy(embedSubtitles = !it.embedSubtitles) }
                    }
                }
            }
        }
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
}

// ---------------------------------------------------- impersonation page ----

@Composable
private fun ImpersonationPage(
    settings: Settings,
    onBack: () -> Unit,
    update: ((Settings) -> Settings) -> Unit
) {
    var showImpersonatePicker by remember { mutableStateOf(false) }

    SettingsSubPage("Impersonation", onBack) {
        PageSectionLabel("Browser impersonation")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ToggleRow(
                    title = "Enable impersonation",
                    subtitle = "Send requests with a browser TLS fingerprint",
                    checked = settings.impersonateEnabled,
                    onChecked = { update { it.copy(impersonateEnabled = !it.impersonateEnabled) } }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable { showImpersonatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
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
                Text(
                    "Use only when a site blocks default requests. Forcing it for everything can slow downloads down.",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
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

// ---------------------------------------------------------- storage page ----

@Composable
private fun StoragePage(
    settings: Settings,
    onBack: () -> Unit,
    update: ((Settings) -> Settings) -> Unit
) {
    SettingsSubPage("Storage", onBack) {
        PageSectionLabel("Save location")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField(
                    value = settings.dirPath,
                    onValueChange = { s -> update { it.copy(dirPath = s) } },
                    placeholder = "Download directory",
                    leading = { Icon(Icons.Filled.Folder, null, tint = TextTertiary) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        PageSectionLabel("Filenames")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text("Video template", color = TextPrimary, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    GlassTextField(
                        value = settings.template,
                        onValueChange = { s -> update { it.copy(template = s) } },
                        placeholder = "Output template",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column {
                    Text("Playlist template", color = TextPrimary, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    GlassTextField(
                        value = settings.playlistTemplate,
                        onValueChange = { s -> update { it.copy(playlistTemplate = s) } },
                        placeholder = "Playlist output template",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ToggleRow("Restrict filenames", "ASCII-only safe filenames", settings.restrictFilenames) {
                    update { it.copy(restrictFilenames = !it.restrictFilenames) }
                }
            }
        }
    }
}

// ---------------------------------------------------------- network page ----

@Composable
private fun NetworkPage(
    settings: Settings,
    onBack: () -> Unit,
    update: ((Settings) -> Settings) -> Unit
) {
    SettingsSubPage("Network", onBack) {
        PageSectionLabel("Proxy")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GlassTextField(
                    value = settings.proxyUrl,
                    onValueChange = { s -> update { it.copy(proxyUrl = s) } },
                    placeholder = "e.g. socks5://127.0.0.1:9050",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        PageSectionLabel("Advanced")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                GlassTextField(
                    value = settings.extraArgs,
                    onValueChange = { s -> update { it.copy(extraArgs = s) } },
                    placeholder = "Extra yt-dlp arguments",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Appended verbatim to every yt-dlp call. Quote values containing spaces.",
                    color = TextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// ------------------------------------------------------------ about page ----

@Composable
private fun AboutPage(onBack: () -> Unit) {
    SettingsSubPage("About", onBack) {
        PageSectionLabel("App")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoRow("Name", "Lychee")
                InfoRow("Version", "0.2.0")
                InfoRow("License", "GPL-3.0")
            }
        }

        Spacer(Modifier.height(20.dp))
        PageSectionLabel("Open source")
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoRow("yt-dlp", "Unlicense")
                InfoRow("YTDLnis", "GPL-3.0")
                InfoRow("youtubedl-android", "Apache-2.0 / GPL-3.0")
                InfoRow("ffmpeg • aria2c", "GPL / OpenSSL")
                InfoRow("curl_cffi • QuickJS", "MIT")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Please respect the terms of service of the platforms you download from.",
            color = TextTertiary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

// ------------------------------------------------------------- shared UI ----

private fun qualityLabel(cap: Int): String = if (cap <= 0) "Best" else "${cap}p"

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
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
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
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
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp)
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
        GlassCard(
            Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            baseColor = Color(0xF7181328)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                options.forEach { (label, subtitle, value) ->
                    val isSelected = value == selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.14f)
                                else Color.White.copy(alpha = 0.04f)
                            )
                            .clickable { onSelect(value); onDismiss() }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
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
