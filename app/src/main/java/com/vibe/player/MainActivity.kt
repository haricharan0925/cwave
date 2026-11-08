package com.vibe.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vibe.player.ui.VibeRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a fresh start: after a rotation the same intent comes back and must not replay the file.
        if (savedInstanceState == null) handleOpen(intent)
        setContent { VibeRoot() }
    }

    /** singleTask: a second "open with" while running arrives here instead of a new activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpen(intent)
    }

    private fun handleOpen(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { container.externalOpen.value = it }
    }
}
