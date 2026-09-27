package com.devfahim00.lychee.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lychee_settings")

data class Settings(
    // General / storage
    val dirPath: String = "/storage/emulated/0/Download/Lychee",
    val template: String = "%(title)s [%(id)s].%(ext)s",
    val playlistTemplate: String = "%(playlist_title)s/%(playlist_index)03d - %(title)s [%(id)s].%(ext)s",
    val restrictFilenames: Boolean = false,
    // Download defaults
    val videoQualityCap: Int = 1080,          // 0 = best/unlimited
    val audioFormat: String = "m4a",          // m4a | mp3 | opus | flac | wav
    val concurrentFragments: Int = 4,
    val useAria2c: Boolean = false,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = true,
    val writeSubtitles: Boolean = false,
    val subtitleLanguages: String = "en.*",
    val embedSubtitles: Boolean = true,
    val removeSponsorBlock: Boolean = false,
    // Cookies
    val cookiesEnabled: Boolean = false,
    val cookiesEnabledDomains: Set<String> = emptySet(),
    // Impersonation (see https://github.com/yt-dlp/yt-dlp#impersonation)
    val impersonateEnabled: Boolean = false,
    val impersonateTarget: String = "chrome",
    // yt-dlp updates (see https://github.com/yt-dlp/yt-dlp#update)
    val updateChannel: String = "stable",     // stable | nightly | master
    val autoCheckUpdates: Boolean = true,
    // Network
    val proxyUrl: String = "",
    val extraArgs: String = ""
)

object Prefs {

    private val DIR_PATH = stringPreferencesKey("dir_path")
    private val TEMPLATE = stringPreferencesKey("template")
    private val PLAYLIST_TEMPLATE = stringPreferencesKey("playlist_template")
    private val RESTRICT_FILENAMES = booleanPreferencesKey("restrict_filenames")
    private val VIDEO_QUALITY_CAP = intPreferencesKey("video_quality_cap")
    private val AUDIO_FORMAT = stringPreferencesKey("audio_format")
    private val CONCURRENT_FRAGMENTS = intPreferencesKey("concurrent_fragments")
    private val USE_ARIA2C = booleanPreferencesKey("use_aria2c")
    private val EMBED_METADATA = booleanPreferencesKey("embed_metadata")
    private val EMBED_THUMBNAIL = booleanPreferencesKey("embed_thumbnail")
    private val WRITE_SUBTITLES = booleanPreferencesKey("write_subtitles")
    private val SUBTITLE_LANGUAGES = stringPreferencesKey("subtitle_languages")
    private val EMBED_SUBTITLES = booleanPreferencesKey("embed_subtitles")
    private val REMOVE_SPONSORBLOCK = booleanPreferencesKey("remove_sponsorblock")
    private val COOKIES_ENABLED = booleanPreferencesKey("cookies_enabled")
    private val COOKIES_DOMAINS = stringSetPreferencesKey("cookies_enabled_domains")
    private val IMPERSONATE_ENABLED = booleanPreferencesKey("impersonate_enabled")
    private val IMPERSONATE_TARGET = stringPreferencesKey("impersonate_target")
    private val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
    private val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
    private val PROXY_URL = stringPreferencesKey("proxy_url")
    private val EXTRA_ARGS = stringPreferencesKey("extra_args")

    private fun fromPreferences(p: Preferences): Settings = Settings(
        dirPath = p[DIR_PATH] ?: "/storage/emulated/0/Download/Lychee",
        template = p[TEMPLATE] ?: "%(title)s [%(id)s].%(ext)s",
        playlistTemplate = p[PLAYLIST_TEMPLATE]
            ?: "%(playlist_title)s/%(playlist_index)03d - %(title)s [%(id)s].%(ext)s",
        restrictFilenames = p[RESTRICT_FILENAMES] ?: false,
        videoQualityCap = p[VIDEO_QUALITY_CAP] ?: 1080,
        audioFormat = p[AUDIO_FORMAT] ?: "m4a",
        concurrentFragments = p[CONCURRENT_FRAGMENTS] ?: 4,
        useAria2c = p[USE_ARIA2C] ?: false,
        embedMetadata = p[EMBED_METADATA] ?: true,
        embedThumbnail = p[EMBED_THUMBNAIL] ?: true,
        writeSubtitles = p[WRITE_SUBTITLES] ?: false,
        subtitleLanguages = p[SUBTITLE_LANGUAGES] ?: "en.*",
        embedSubtitles = p[EMBED_SUBTITLES] ?: true,
        removeSponsorBlock = p[REMOVE_SPONSORBLOCK] ?: false,
        cookiesEnabled = p[COOKIES_ENABLED] ?: false,
        cookiesEnabledDomains = p[COOKIES_DOMAINS] ?: emptySet(),
        impersonateEnabled = p[IMPERSONATE_ENABLED] ?: false,
        impersonateTarget = p[IMPERSONATE_TARGET] ?: "chrome",
        updateChannel = p[UPDATE_CHANNEL] ?: "stable",
        autoCheckUpdates = p[AUTO_CHECK_UPDATES] ?: true,
        proxyUrl = p[PROXY_URL] ?: "",
        extraArgs = p[EXTRA_ARGS] ?: ""
    )

    fun settings(context: Context): Flow<Settings> =
        context.dataStore.data.map(::fromPreferences)

    suspend fun edit(context: Context, transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val n = transform(fromPreferences(p))
            p[DIR_PATH] = n.dirPath
            p[TEMPLATE] = n.template
            p[PLAYLIST_TEMPLATE] = n.playlistTemplate
            p[RESTRICT_FILENAMES] = n.restrictFilenames
            p[VIDEO_QUALITY_CAP] = n.videoQualityCap
            p[AUDIO_FORMAT] = n.audioFormat
            p[CONCURRENT_FRAGMENTS] = n.concurrentFragments
            p[USE_ARIA2C] = n.useAria2c
            p[EMBED_METADATA] = n.embedMetadata
            p[EMBED_THUMBNAIL] = n.embedThumbnail
            p[WRITE_SUBTITLES] = n.writeSubtitles
            p[SUBTITLE_LANGUAGES] = n.subtitleLanguages
            p[EMBED_SUBTITLES] = n.embedSubtitles
            p[REMOVE_SPONSORBLOCK] = n.removeSponsorBlock
            p[COOKIES_ENABLED] = n.cookiesEnabled
            p[COOKIES_DOMAINS] = n.cookiesEnabledDomains
            p[IMPERSONATE_ENABLED] = n.impersonateEnabled
            p[IMPERSONATE_TARGET] = n.impersonateTarget
            p[UPDATE_CHANNEL] = n.updateChannel
            p[AUTO_CHECK_UPDATES] = n.autoCheckUpdates
            p[PROXY_URL] = n.proxyUrl
            p[EXTRA_ARGS] = n.extraArgs
        }
    }
}
