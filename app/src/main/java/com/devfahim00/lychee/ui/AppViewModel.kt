package com.devfahim00.lychee.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devfahim00.lychee.core.ArgBuilder
import com.devfahim00.lychee.core.CookieStore
import com.devfahim00.lychee.core.DownloadEngine
import com.devfahim00.lychee.core.DownloadOptions
import com.devfahim00.lychee.core.InfoParser
import com.devfahim00.lychee.core.LycheeRuntime
import com.devfahim00.lychee.core.DownloadTask
import com.devfahim00.lychee.core.Prefs
import com.devfahim00.lychee.core.Settings
import com.devfahim00.lychee.core.Updater
import com.devfahim00.lychee.core.VideoInfo
import com.devfahim00.lychee.service.DownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FetchState {
    data object Idle : FetchState
    data object Loading : FetchState
    data class Success(val info: VideoInfo) : FetchState
    data class Error(val message: String) : FetchState
}

data class EngineStatus(
    val runtimeReady: Boolean = false,
    val ytdlpVersion: String = "",
    val impersonationAvailable: Boolean? = null,
    val quickjsAvailable: Boolean = false,
    val checkingUpdate: Boolean = false,
    val updateInfo: Updater.UpdateInfo? = null,
    val updateResult: String? = null,
    val updating: Boolean = false
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val settings: StateFlow<Settings> = Prefs.settings(app)
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val tasks: StateFlow<List<DownloadTask>> = DownloadEngine.tasks

    private val _fetchState = MutableStateFlow<FetchState>(FetchState.Idle)
    val fetchState: StateFlow<FetchState> = _fetchState

    private val _engineStatus = MutableStateFlow(EngineStatus())
    val engineStatus: StateFlow<EngineStatus> = _engineStatus

    init {
        refreshEngineStatus()
    }

    fun refreshEngineStatus() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val ready = runCatching { LycheeRuntime.init(app) }.isSuccess
            val version = if (ready) {
                runCatching {
                    val res = LycheeRuntime.execute(app, listOf("--version"))
                    res.out.trim().lineSequence().firstOrNull { it.isNotBlank() } ?: ""
                }.getOrDefault("")
            } else ""
            val impersonation = if (ready) {
                runCatching { LycheeRuntime.impersonationAvailable(app) }.getOrNull()
            } else null
            val quickjs = if (ready) LycheeRuntime.qjsBin.exists() else false
            val storedVersion = Updater.installedVersion(app)
            _engineStatus.value = EngineStatus(
                runtimeReady = ready,
                ytdlpVersion = version.ifBlank { storedVersion },
                impersonationAvailable = impersonation,
                quickjsAvailable = quickjs
            )
        }
    }

    fun fetchInfo(url: String) {
        val app = getApplication<Application>()
        _fetchState.value = FetchState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = settings.value
                val cookieFile = CookieStore.mergedCookieFile(app, s)
                val args = mutableListOf<String>()
                args.addAll(ArgBuilder.baseArgs(app, s, cookieFile))
                args.addAll(ArgBuilder.infoArgs())
                args.add(url.trim())
                val result = LycheeRuntime.execute(app, args)
                if (result.canceled) {
                    _fetchState.value = FetchState.Idle
                    return@launch
                }
                if (result.exitCode != 0) {
                    val err = result.err.lines().lastOrNull { it.isNotBlank() }
                        ?: "Failed to fetch video info (exit ${result.exitCode})"
                    _fetchState.value = FetchState.Error(err.take(300))
                    return@launch
                }
                val jsonLine = result.out.lineSequence()
                    .firstOrNull { it.trimStart().startsWith("{") }
                if (jsonLine == null) {
                    _fetchState.value = FetchState.Error("Unexpected yt-dlp output")
                    return@launch
                }
                val info = InfoParser.parse(jsonLine)
                _fetchState.value = FetchState.Success(info)
            } catch (e: Exception) {
                _fetchState.value = FetchState.Error(e.message ?: "Failed to fetch info")
            }
        }
    }

    fun resetFetch() {
        _fetchState.value = FetchState.Idle
    }

    fun startDownload(options: DownloadOptions) {
        val app = getApplication<Application>()
        DownloadEngine.enqueue(
            context = app,
            options = options,
            settings = settings.value
        ) {
            runCatching { DownloadService.start(app) }
        }
    }

    fun cancelDownload(taskId: String) = DownloadEngine.cancel(taskId)

    fun removeDownload(taskId: String) = DownloadEngine.remove(taskId)

    fun clearFinished() = DownloadEngine.clearFinished()

    fun updateSettings(transform: (Settings) -> Settings) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            Prefs.edit(app, transform)
        }
    }

    fun checkForUpdate() {
        val app = getApplication<Application>()
        _engineStatus.value = _engineStatus.value.copy(checkingUpdate = true, updateResult = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val channel = settings.value.updateChannel
                val info = Updater.fetchLatest(channel)
                val available = Updater.isUpdateAvailable(app, info)
                _engineStatus.value = _engineStatus.value.copy(
                    checkingUpdate = false,
                    updateInfo = info,
                    updateResult = if (available) {
                        "Update available: ${info.tag} (current: ${Updater.installedVersion(app)})"
                    } else {
                        "yt-dlp is up to date (${info.tag})"
                    }
                )
            } catch (e: Exception) {
                _engineStatus.value = _engineStatus.value.copy(
                    checkingUpdate = false,
                    updateResult = "Update check failed: ${e.message}"
                )
            }
        }
    }

    fun performUpdate() {
        val app = getApplication<Application>()
        val info = _engineStatus.value.updateInfo ?: return
        _engineStatus.value = _engineStatus.value.copy(updating = true, updateResult = "Downloading ${info.tag}…")
        viewModelScope.launch(Dispatchers.IO) {
            val result = Updater.performUpdate(app, info)
            val message = result.fold(
                onSuccess = { "Updated yt-dlp to $it" },
                onFailure = { "Update failed: ${it.message}" }
            )
            withContext(Dispatchers.Main) {
                _engineStatus.value = _engineStatus.value.copy(updating = false, updateResult = message)
            }
            refreshEngineStatus()
        }
    }

    fun resetToBundled() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val result = Updater.resetToBundled(app)
            val message = result.fold(
                onSuccess = { "Restored bundled yt-dlp $it" },
                onFailure = { "Reset failed: ${it.message}" }
            )
            _engineStatus.value = _engineStatus.value.copy(updateResult = message)
            refreshEngineStatus()
        }
    }
}
