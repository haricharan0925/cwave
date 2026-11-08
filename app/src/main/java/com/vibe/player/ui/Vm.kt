package com.vibe.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibe.player.AppContainer
import com.vibe.player.container

/** Obtains a ViewModel wired to the app's dependency container without a DI framework. */
@Composable
inline fun <reified T : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> T): T {
    val container = LocalContext.current.container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatDurationLong(ms: Long): String {
    val totalMin = ms / 60000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "$h hr $m min"
        totalMin > 0 -> "$m min"
        else -> "${ms / 1000} sec"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

fun pluralize(n: Int, one: String, many: String = one + "s") = if (n == 1) "1 $one" else "$n $many"
