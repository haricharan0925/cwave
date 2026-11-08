package com.vibe.player.downloads

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class YouTubeAccount(val signedIn: Boolean, val label: String?, val savedAt: Long)

/**
 * Optional YouTube sign-in. The user logs into youtube.com in an in-app WebView; we copy the session
 * cookies into a Netscape-format file that yt-dlp reads via --cookies. Nothing is sent anywhere except
 * to YouTube itself. Signing out deletes the file and the WebView cookies.
 */
class YouTubeAuth(private val context: Context) {

    val cookieFile: File get() = File(context.filesDir, "yt_cookies.txt")
    private val metaFile get() = File(context.filesDir, "yt_cookies.meta")

    private val _account = MutableStateFlow(load())
    val account: StateFlow<YouTubeAccount> = _account.asStateFlow()

    private val domains = listOf("https://www.youtube.com", "https://youtube.com", "https://music.youtube.com", "https://accounts.google.com", "https://google.com", "https://www.google.com")

    /** True when the WebView session looks logged in (YouTube sets SID/SAPISID only for signed-in users). */
    fun webViewLooksSignedIn(): Boolean {
        val c = CookieManager.getInstance().getCookie("https://www.youtube.com") ?: return false
        return c.contains("SAPISID=") || c.contains("__Secure-3PAPISID=")
    }

    /** Export the WebView's cookies to the yt-dlp file. Returns false if no signed-in session was found. */
    fun saveFromWebView(label: String? = null): Boolean {
        if (!webViewLooksSignedIn()) return false
        val cm = CookieManager.getInstance()
        cm.flush()
        val lines = StringBuilder("# Netscape HTTP Cookie File\n# Exported by Cwave\n")
        val seen = HashSet<String>()
        for (url in domains) {
            val host = url.removePrefix("https://")
            val domain = if (host.startsWith("www.")) "." + host.removePrefix("www.") else ".$host"
            val raw = cm.getCookie(url) ?: continue
            for (part in raw.split(';')) {
                val kv = part.trim(); val eq = kv.indexOf('=')
                if (eq <= 0) continue
                val name = kv.substring(0, eq).trim(); val value = kv.substring(eq + 1).trim()
                val key = "$domain|$name"
                if (!seen.add(key)) continue
                // domain, include-subdomains, path, secure, expiry (0 = session; yt-dlp accepts it), name, value
                val secure = if (name.startsWith("__Secure-") || name.startsWith("__Host-")) "TRUE" else "FALSE"
                lines.append("$domain\tTRUE\t/\t$secure\t${System.currentTimeMillis() / 1000 + 365L * 86400}\t$name\t$value\n")
            }
        }
        cookieFile.writeText(lines.toString())
        metaFile.writeText("${label ?: "YouTube account"}\n${System.currentTimeMillis()}")
        // YouTube rotates the cookies of any session left open, which silently kills the exported copy
        // ("The page needs to be reloaded"). Closing the WebView's session leaves yt-dlp the only user of it.
        cm.removeAllCookies(null); cm.flush()
        _account.value = load()
        return true
    }

    /** YouTube rejected the saved session (rotated/expired cookies): forget it so downloads stop failing on it. */
    fun dropStaleSession() {
        cookieFile.delete(); metaFile.delete()
        _account.value = load()
    }

    fun signOut() {
        cookieFile.delete(); metaFile.delete()
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null); cm.flush()
        _account.value = load()
    }

    fun cookieArgIfAny(): String? = cookieFile.takeIf { it.exists() && it.length() > 64 }?.absolutePath

    private fun load(): YouTubeAccount {
        if (!cookieFile.exists()) return YouTubeAccount(false, null, 0)
        val meta = runCatching { metaFile.readLines() }.getOrDefault(emptyList())
        return YouTubeAccount(true, meta.getOrNull(0), meta.getOrNull(1)?.toLongOrNull() ?: cookieFile.lastModified())
    }
}
