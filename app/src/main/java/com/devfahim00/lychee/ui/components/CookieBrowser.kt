package com.devfahim00.lychee.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devfahim00.lychee.core.CookieStore
import com.devfahim00.lychee.ui.theme.BgTop
import com.devfahim00.lychee.ui.theme.LycheePink
import com.devfahim00.lychee.ui.theme.TextPrimary
import com.devfahim00.lychee.ui.theme.TextSecondary
import com.devfahim00.lychee.ui.theme.TextTertiary

private const val CHROME_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0.0.0 Safari/537.36"

/**
 * Built-in browser used to import cookies for a website: the user signs in,
 * then saves the cookies Lychee should send with its requests.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CookieBrowserDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onSaved: (domain: String) -> Unit
) {
    val context = LocalContext.current
    var urlText by remember { mutableStateOf(initialUrl) }
    var currentDomain by remember { mutableStateOf(CookieStore.baseDomain(initialUrl)) }
    var currentCookieCount by remember { mutableIntStateOf(0) }
    var progress by remember { mutableIntStateOf(0) }
    // domain -> (cookie name -> value), accumulated as pages finish loading
    val captured = remember { mutableMapOf<String, MutableMap<String, String>>() }
    var webView by remember { mutableStateOf<WebView?>(null) }

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("http")) t else "https://$t"
    }

    fun captureCookies(url: String) {
        if (!url.startsWith("http")) return
        val base = CookieStore.baseDomain(url)
        val cookieHeader = runCatching {
            CookieManager.getInstance().getCookie(url)
        }.getOrNull() ?: return
        val map = captured.getOrPut(base) { mutableMapOf() }
        cookieHeader.split(";").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val name = pair.take(idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isNotEmpty()) map[name] = value
            }
        }
        if (base == currentDomain) currentCookieCount = map.size
    }

    fun saveCurrent() {
        val cookies = captured[currentDomain] ?: return
        if (cookies.isEmpty()) return
        CookieStore.saveJar(context, currentDomain, cookies)
        onSaved(currentDomain)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(BgTop)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ---- top bar ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close", tint = TextSecondary)
                }
                GlassTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = "Enter website address",
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary,
                        fontSize = 13.sp
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            webView?.loadUrl(normalizeUrl(urlText))
                        }
                    ),
                    leading = { Icon(Icons.Filled.Public, null, tint = TextTertiary, modifier = Modifier.size(16.dp)) },
                    trailing = {
                        IconButton(
                            onClick = { webView?.loadUrl(normalizeUrl(urlText)) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.ArrowForward, "Go", tint = LycheePink, modifier = Modifier.size(18.dp))
                        }
                    }
                )
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Filled.Refresh, "Reload", tint = TextSecondary)
                }
            }

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = LycheePink,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
            }

            // ---- web content ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = CHROME_UA
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest
                                ): Boolean = false

                                override fun onPageStarted(
                                    view: WebView,
                                    url: String,
                                    favicon: android.graphics.Bitmap?
                                ) {
                                    currentDomain = CookieStore.baseDomain(url)
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    captureCookies(url)
                                    progress = 0
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            loadUrl(initialUrl)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // ---- bottom save bar ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Cookies for", color = TextTertiary, fontSize = 11.sp)
                    Text(
                        currentDomain,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        if (currentCookieCount > 0) "$currentCookieCount cookies captured" else "Browse and sign in first",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.size(8.dp))
                GradientButton(
                    onClick = { saveCurrent() },
                    text = "Save",
                    enabled = currentCookieCount > 0
                )
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                webView?.apply {
                    runCatching {
                        stopLoading()
                        loadUrl("about:blank")
                        clearHistory()
                    }
                }
            }
        }
    }
}
