package com.example.goveecontroller.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.goveecontroller.ble.GoveeBleManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shared executor: link remembered strip, run one action, unlink.
 * Widget taps use the goAsync receiver below; the QS tile and device
 * controls call it with a longer link budget.
 */
object WidgetRunner {
    private const val TAG = "GoveeGlow"

    suspend fun execute(c: Context, action: String, linkTimeoutMs: Long): Boolean {
        val app = c.applicationContext
        val prefs = app.getSharedPreferences("govee", Context.MODE_PRIVATE)
        if (prefs.getString("last_mac", null) == null) {
            Log.d(TAG, "runner: no remembered strip")
            return false
        }

        WidgetState.begin(app)
        WidgetUpdater.refreshAll(app)
        val mgr = GoveeBleManager(app)
        var failure: String? = null
        try {
            mgr.autoConnect(force = true)
            val linked = withTimeoutOrNull(linkTimeoutMs) { mgr.connected.first { it } } != null
            if (!linked) {
                Log.d(TAG, "runner: link timeout")
                failure = WidgetState.ERROR_OFFLINE
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
                    if (!AutomationRunner.runScheduled(app, mgr, action)) {
                        Log.d(TAG, "runner: unknown action $action")
                        return false
                    }
                }
            }
            delay(600)
            return true
        } catch (e: CancellationException) {
            Log.d(TAG, "runner cancelled")
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "runner failed: ${e.message}")
            failure = WidgetState.ERROR_OFFLINE
            return false
        } finally {
            WidgetState.finish(app, failure)
            mgr.disconnect()
            WidgetUpdater.refreshAll(app)
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
                runBlocking { WidgetRunner.execute(c, action, 7_000) }
            } catch (e: Exception) {
                Log.d("GoveeGlow", "inline runner failed: ${e.message}")
                WidgetState.finish(c, WidgetState.ERROR_OFFLINE)
                WidgetUpdater.refreshAll(c)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
