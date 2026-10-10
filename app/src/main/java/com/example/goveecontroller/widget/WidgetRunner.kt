package com.example.goveecontroller.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.goveecontroller.ble.GoveeBleManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared executor: link remembered strip, run one action, unlink.
 * Used by WidgetService (foreground, generous timeout) and by the
 * providers' goAsync fallback (background, tight budget).
 */
object WidgetRunner {
    private const val TAG = "GoveeGlow"

    suspend fun execute(c: Context, action: String, linkTimeoutMs: Long): Boolean {
        val prefs = c.getSharedPreferences("govee", Context.MODE_PRIVATE)
        if (prefs.getString("last_mac", null) == null) {
            Log.d(TAG, "runner: no remembered strip")
            return false
        }
        val mgr = GoveeBleManager(c.applicationContext)
        try {
            mgr.autoConnect()
            val linked = withTimeoutOrNull(linkTimeoutMs) { mgr.connected.first { it } } != null
            if (!linked) {
                Log.d(TAG, "runner: link timeout")
                return false
            }
            when (action) {
                ACTION_TOGGLE -> {
                    val now = !prefs.getBoolean(GoveeBleManager.KEY_POWER, true)
                    repeat(2) {
                        mgr.power(now)
                        delay(350)
                    }
                    Log.d(TAG, "runner: power -> $now")
                }
                ACTION_BRIGHT_UP, ACTION_BRIGHT_DOWN -> {
                    val cur = prefs.getInt(GoveeBleManager.KEY_BRIGHT, 80)
                    val next = (cur + if (action == ACTION_BRIGHT_UP) 15 else -15).coerceIn(1, 100)
                    repeat(2) {
                        mgr.brightness(next)
                        delay(350)
                    }
                    Log.d(TAG, "runner: brightness -> $next")
                }
                else -> {
                    val handled = AutomationRunner.runScheduled(c, mgr, action)
                    if (!handled) {
                        Log.d(TAG, "runner: unknown action $action")
                        return false
                    }
                }
            }
            delay(600)
            WidgetUpdater.refreshAll(c)
            return true
        } finally {
            mgr.disconnect()
        }
    }
}

/** Inline fallback so widget taps work even when FGS start is denied. */
class WidgetInlineReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == AutomationRunner.PREFIX + "__replant") {
            AutomationRunner.scheduleAll(c)
            return
        }
        if (action != ACTION_TOGGLE && action != ACTION_BRIGHT_UP && action != ACTION_BRIGHT_DOWN &&
            !action.startsWith(AutomationRunner.PREFIX)
        ) return
        val pending = goAsync()
        Thread {
            try {
                runBlocking { WidgetRunner.execute(c, action, 9_000) }
            } catch (e: Exception) {
                Log.d("GoveeGlow", "inline runner failed: ${e.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }
}
