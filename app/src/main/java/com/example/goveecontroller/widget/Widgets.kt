package com.example.goveecontroller.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.goveecontroller.R
import com.example.goveecontroller.ble.GoveeBleManager

/** Actions handled by the providers → forwarded to WidgetService. */
const val ACTION_TOGGLE = "com.example.goveecontroller.TOGGLE"
const val ACTION_BRIGHT_UP = "com.example.goveecontroller.BRIGHT_UP"
const val ACTION_BRIGHT_DOWN = "com.example.goveecontroller.BRIGHT_DOWN"
const val ACTION_REFRESH = "com.example.goveecontroller.REFRESH"

private fun prefs(c: Context) =
    c.getSharedPreferences("govee", Context.MODE_PRIVATE)

private fun pending(c: Context, action: String, cls: Class<*>): PendingIntent {
    val i = Intent(c, cls).setAction(action)
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
    return PendingIntent.getBroadcast(c, action.hashCode(), i, flags)
}

object WidgetUpdater {
    fun refreshAll(c: Context) {
        refreshPower(c)
        refreshBrightness(c)
    }

    fun refreshPower(c: Context) {
        val on = prefs(c).getBoolean(GoveeBleManager.KEY_POWER, true)
        val mgr = AppWidgetManager.getInstance(c)
        val ids = mgr.getAppWidgetIds(ComponentName(c, PowerWidgetProvider::class.java))
        ids.forEach { id ->
            val v = RemoteViews(c.packageName, R.layout.widget_power).apply {
                setTextViewText(R.id.widget_power_state, if (on) "ON" else "OFF")
                setImageViewResource(
                    R.id.widget_power_icon,
                    if (on) R.drawable.ic_power_on else R.drawable.ic_power_off,
                )
                setContentDescription(R.id.widget_power_btn, if (on) "Turn off" else "Turn on")
                setOnClickPendingIntent(R.id.widget_power_btn, pending(c, ACTION_TOGGLE, PowerWidgetProvider::class.java))
            }
            mgr.updateAppWidget(id, v)
        }
    }

    fun refreshBrightness(c: Context) {
        val p = prefs(c).getInt(GoveeBleManager.KEY_BRIGHT, 80)
        val mgr = AppWidgetManager.getInstance(c)
        val ids = mgr.getAppWidgetIds(ComponentName(c, BrightnessWidgetProvider::class.java))
        ids.forEach { id ->
            val v = RemoteViews(c.packageName, R.layout.widget_brightness).apply {
                setTextViewText(R.id.widget_bright_value, "$p%")
                setProgressBar(R.id.widget_bright_bar, 100, p, false)
                setContentDescription(R.id.widget_bright_down, "Dim")
                setContentDescription(R.id.widget_bright_up, "Brighten")
                setOnClickPendingIntent(R.id.widget_bright_down, pending(c, ACTION_BRIGHT_DOWN, BrightnessWidgetProvider::class.java))
                setOnClickPendingIntent(R.id.widget_bright_up, pending(c, ACTION_BRIGHT_UP, BrightnessWidgetProvider::class.java))
            }
            mgr.updateAppWidget(id, v)
        }
    }
}

class PowerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) =
        WidgetUpdater.refreshPower(c)

    override fun onReceive(c: Context, intent: Intent) {
        super.onReceive(c, intent)
        if (intent.action == ACTION_TOGGLE) {
            c.sendBroadcast(Intent(c, WidgetInlineReceiver::class.java).setAction(ACTION_TOGGLE))
        }
    }
}

class BrightnessWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) =
        WidgetUpdater.refreshBrightness(c)

    override fun onReceive(c: Context, intent: Intent) {
        super.onReceive(c, intent)
        when (intent.action) {
            ACTION_BRIGHT_UP, ACTION_BRIGHT_DOWN ->
                c.sendBroadcast(Intent(c, WidgetInlineReceiver::class.java).setAction(intent.action))
        }
    }
}
