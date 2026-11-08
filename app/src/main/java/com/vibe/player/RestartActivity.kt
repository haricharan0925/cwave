package com.vibe.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process

/**
 * Runs in its own process (see manifest) so it survives killing the main one. Used after a backup
 * import has replaced the database: kill main → relaunch → exit. Same idea as ProcessPhoenix.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainPid = intent.getIntExtra(EXTRA_PID, 0)
        if (mainPid != 0 && mainPid != Process.myPid()) Process.killProcess(mainPid)
        val launch = packageManager.getLaunchIntentForPackage(packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launch)
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val EXTRA_PID = "pid"
        fun trigger(context: Context) {
            context.startActivity(
                Intent(context, RestartActivity::class.java)
                    .putExtra(EXTRA_PID, Process.myPid())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
    }
}
