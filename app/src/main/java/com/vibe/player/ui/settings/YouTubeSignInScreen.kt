package com.vibe.player.ui.settings

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.vibe.player.container

/**
 * In-app YouTube login. Shows youtube.com in a WebView; once Google's session cookies appear the
 * "Use this account" button lights up and exports them for yt-dlp.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSignInScreen(nav: NavController, onDone: (String) -> Unit) {
    val auth = LocalContext.current.container.youTubeAuth
    var loading by remember { mutableStateOf(true) }
    var signedIn by remember { mutableStateOf(auth.webViewLooksSignedIn()) }
    var currentUrl by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text("Sign in to YouTube", style = MaterialTheme.typography.titleMedium)
                Text(if (signedIn) "Signed in — tap Use this account" else "Log in below, then tap Use this account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = { if (auth.saveFromWebView()) { onDone("YouTube account saved. Downloads will use it from now on."); nav.popBackStack() } }, enabled = signedIn) { Text("Use this account") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    // Google blocks sign-in from obvious embedded WebViews; a standard mobile Chrome UA is accepted.
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) { loading = true; currentUrl = url.orEmpty() }
                        override fun onPageFinished(view: WebView, url: String?) {
                            loading = false; currentUrl = url.orEmpty()
                            CookieManager.getInstance().flush()
                            signedIn = auth.webViewLooksSignedIn()
                        }
                    }
                    loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fwww.youtube.com%2F")
                }
            },
        )
    }
}
