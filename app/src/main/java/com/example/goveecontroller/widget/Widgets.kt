package com.example.goveecontroller.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.goveecontroller.MainActivity
import com.example.goveecontroller.R
import com.example.goveecontroller.ble.GoveeBleManager

/** Actions handled by the providers → forwarded to WidgetInlineReceiver. */
const val ACTION_TOGGLE = "com.example.goveecontroller.TOGGLE"
const val ACTION_BRIGHT_UP = "com.example.goveecontroller.BRIGHT_UP"
const val ACTION_BRIGHT_DOWN = "com.example.goveecontroller.BRIGHT_DOWN"
const val ACTION_NOOP = "com.example.goveecontroller.WIDGET_NOOP"

private const val DEFAULT_BRIGHT = 80
private const val BUSY_WINDOW_MS = 15_000L

private fun prefs(c: Context) =
    c.getSharedPreferences("govee", Context.MODE_PRIVATE)

private fun pending(c: Context, action: String, cls: Class<*>): PendingIntent {
    val i = Intent(c, cls).setAction(action)
    return PendingIntent.getBroadcast(
        c,
        action.hashCode(),
        i,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun openApp(c: Context): PendingIntent {
    val i = Intent(c, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    return PendingIntent.getActivity(
        c,
        1,
        i,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Transient busy/offline state shared by taps and the executor. */
object WidgetState {
    const val ERROR_OFFLINE = "offline"
    private const val KEY_BUSY_SINCE = "widget_busy_since"
    private const val KEY_ERROR = "widget_error"

    fun hasStrip(c: Context): Boolean =
        prefs(c).getString("last_mac", null) != null

    fun isBusy(c: Context): Boolean {
        val since = prefs(c).getLong(KEY_BUSY_SINCE, 0L)
        return since > 0L && SystemClock.elapsedRealtime() - since < BUSY_WINDOW_MS
    }

    fun error(c: Context): String? = prefs(c).getString(KEY_ERROR, null)

    fun begin(c: Context) {
        prefs(c).edit()
            .putLong(KEY_BUSY_SINCE, SystemClock.elapsedRealtime())
            .remove(KEY_ERROR)
            .apply()
    }

    fun finish(c: Context, error: String? = null) {
        val edit = prefs(c).edit().putLong(KEY_BUSY_SINCE, 0L)
        if (error == null) edit.remove(KEY_ERROR) else edit.putString(KEY_ERROR, error)
        edit.apply()
    }

    fun clearError(c: Context) {
        prefs(c).edit().remove(KEY_ERROR).apply()
    }
}

object WidgetUpdater {
    fun refreshAll(c: Context) {
        refreshPower(c)
        refreshBrightness(c)
    }

    fun refreshPower(c: Context) {
        val p = prefs(c)
        val on = p.getBoolean(GoveeBleManager.KEY_POWER, true)
        val bright = p.getInt(GoveeBleManager.KEY_BRIGHT, DEFAULT_BRIGHT).coerceIn(0, 100)
        val hasStrip = WidgetState.hasStrip(c)
        val busy = WidgetState.isBusy(c)
        val offline = WidgetState.error(c) == WidgetState.ERROR_OFFLINE

        val title = when {
            !hasStrip -> c.getString(R.string.widget_no_strip)
            busy -> c.getString(R.string.widget_connecting)
            offline -> c.getString(R.string.widget_strip_offline)
            on -> c.getString(R.string.widget_lights_on)
            else -> c.getString(R.string.widget_lights_off)
        }
        val detail = when {
            !hasStrip -> c.getString(R.string.widget_open_to_pair)
            busy -> c.getString(R.string.widget_linking_detail)
            offline -> c.getString(R.string.widget_tap_retry)
            on -> c.getString(R.string.widget_brightness_detail, bright)
            else -> c.getString(R.string.widget_tap_turn_on)
        }
        val active = on && hasStrip
        val circle = when {
            !hasStrip -> R.drawable.widget_circle_off
            offline -> R.drawable.widget_circle_error
            active -> R.drawable.widget_circle_on
            else -> R.drawable.widget_circle_off
        }
        val icon = if (active || (busy && on) || offline) {
            R.drawable.ic_widget_power_on
        } else {
            R.drawable.ic_widget_power_off
        }
        val rootClick = when {
            !hasStrip -> openApp(c)
            busy -> pending(c, ACTION_NOOP, PowerWidgetProvider::class.java)
            else -> pending(c, ACTION_TOGGLE, PowerWidgetProvider::class.java)
        }

        val views = RemoteViews(c.packageName, R.layout.widget_power).apply {
            setInt(R.id.widget_power_root, "setBackgroundResource",
                if (active) R.drawable.widget_card_on else R.drawable.widget_card_off)
            setInt(R.id.widget_power_circle, "setBackgroundResource", circle)
            setImageViewResource(R.id.widget_power_icon, icon)
            setTextViewText(R.id.widget_power_state, title)
            setTextViewText(R.id.widget_power_detail, detail)
            setTextViewText(R.id.widget_power_detail_error, detail)
            setViewVisibility(
                R.id.widget_power_detail,
                if (offline) View.GONE else View.VISIBLE,
            )
            setViewVisibility(
                R.id.widget_power_detail_error,
                if (offline) View.VISIBLE else View.GONE,
            )
            setContentDescription(R.id.widget_power_root, "$title. $detail")
            setContentDescription(R.id.widget_power_open, c.getString(R.string.widget_open_app))
            setOnClickPendingIntent(R.id.widget_power_root, rootClick)
            setOnClickPendingIntent(R.id.widget_power_open, openApp(c))
        }

        val mgr = AppWidgetManager.getInstance(c)
        mgr.getAppWidgetIds(ComponentName(c, PowerWidgetProvider::class.java))
            .forEach { mgr.updateAppWidget(it, views) }
    }

    fun refreshBrightness(c: Context) {
        val p = prefs(c)
        val on = p.getBoolean(GoveeBleManager.KEY_POWER, true)
        val bright = p.getInt(GoveeBleManager.KEY_BRIGHT, DEFAULT_BRIGHT).coerceIn(0, 100)
        val hasStrip = WidgetState.hasStrip(c)
        val busy = WidgetState.isBusy(c)
        val offline = WidgetState.error(c) == WidgetState.ERROR_OFFLINE

        val label = when {
            !hasStrip -> R.string.widget_label_no_strip
            busy -> R.string.widget_label_connecting
            offline -> R.string.widget_label_offline
            on -> R.string.widget_label_brightness
            else -> R.string.widget_label_lights_off
        }
        val labelText = c.getString(label)
        val labelActive = when {
            offline -> R.id.widget_bright_label_error
            busy -> R.id.widget_bright_label_accent
            else -> R.id.widget_bright_label
        }
        val valueText = if (hasStrip) "$bright%" else c.getString(R.string.widget_value_none)
        val valueActive = if (busy || (on && hasStrip)) {
            R.id.widget_bright_value
        } else {
            R.id.widget_bright_value_muted
        }
        val barAlpha = when {
            !hasStrip -> 0.3f
            offline -> 0.5f
            busy -> 0.7f
            on -> 1f
            else -> 0.6f
        }
        val controlAlpha = if (busy || !hasStrip) 0.35f else 1f
        val description = when {
            !hasStrip -> c.getString(R.string.widget_bright_a11y_no_strip)
            offline -> c.getString(R.string.widget_bright_a11y_offline, bright)
            on -> c.getString(R.string.widget_bright_a11y_on, bright)
            else -> c.getString(R.string.widget_bright_a11y_off, bright)
        }
        val downClick = when {
            !hasStrip -> openApp(c)
            busy -> pending(c, ACTION_NOOP, BrightnessWidgetProvider::class.java)
            else -> pending(c, ACTION_BRIGHT_DOWN, BrightnessWidgetProvider::class.java)
        }
        val upClick = when {
            !hasStrip -> openApp(c)
            busy -> pending(c, ACTION_NOOP, BrightnessWidgetProvider::class.java)
            else -> pending(c, ACTION_BRIGHT_UP, BrightnessWidgetProvider::class.java)
        }

        val views = RemoteViews(c.packageName, R.layout.widget_brightness).apply {
            setInt(R.id.widget_bright_root, "setBackgroundResource",
                if (on && hasStrip) R.drawable.widget_card_on else R.drawable.widget_card_off)
            setTextViewText(R.id.widget_bright_label, labelText)
            setTextViewText(R.id.widget_bright_label_accent, labelText)
            setTextViewText(R.id.widget_bright_label_error, labelText)
            for (id in intArrayOf(
                R.id.widget_bright_label,
                R.id.widget_bright_label_accent,
                R.id.widget_bright_label_error,
            )) {
                setViewVisibility(id, if (id == labelActive) View.VISIBLE else View.GONE)
            }
            setTextViewText(R.id.widget_bright_value, valueText)
            setTextViewText(R.id.widget_bright_value_muted, valueText)
            setViewVisibility(
                R.id.widget_bright_value,
                if (valueActive == R.id.widget_bright_value) View.VISIBLE else View.GONE,
            )
            setViewVisibility(
                R.id.widget_bright_value_muted,
                if (valueActive == R.id.widget_bright_value_muted) View.VISIBLE else View.GONE,
            )
            setProgressBar(R.id.widget_bright_bar, 100, if (hasStrip) bright else 0, false)
            setFloat(R.id.widget_bright_bar, "setAlpha", barAlpha)
            setFloat(R.id.widget_bright_down, "setAlpha", controlAlpha)
            setFloat(R.id.widget_bright_up, "setAlpha", controlAlpha)
            setContentDescription(R.id.widget_bright_root, description)
            setContentDescription(
                R.id.widget_bright_down,
                c.getString(
                    if (!hasStrip) R.string.widget_open_app
                    else if (busy) R.string.widget_linking_detail
                    else R.string.widget_dim,
                ),
            )
            setContentDescription(
                R.id.widget_bright_up,
                c.getString(
                    if (!hasStrip) R.string.widget_open_app
                    else if (busy) R.string.widget_linking_detail
                    else R.string.widget_brighten,
                ),
            )
            setOnClickPendingIntent(R.id.widget_bright_root, openApp(c))
            setOnClickPendingIntent(R.id.widget_bright_down, downClick)
            setOnClickPendingIntent(R.id.widget_bright_up, upClick)
        }

        val mgr = AppWidgetManager.getInstance(c)
        mgr.getAppWidgetIds(ComponentName(c, BrightnessWidgetProvider::class.java))
            .forEach { mgr.updateAppWidget(it, views) }
    }
}

class PowerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) =
        WidgetUpdater.refreshPower(c)

    override fun onReceive(c: Context, intent: Intent) {
        super.onReceive(c, intent)
        if (intent.action != ACTION_TOGGLE) return
        if (!WidgetState.hasStrip(c) || WidgetState.isBusy(c)) {
            WidgetUpdater.refreshAll(c)
            return
        }
        WidgetState.begin(c)
        WidgetUpdater.refreshAll(c)
        c.sendBroadcast(Intent(c, WidgetInlineReceiver::class.java).setAction(ACTION_TOGGLE))
    }
}

class BrightnessWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) =
        WidgetUpdater.refreshBrightness(c)

    override fun onReceive(c: Context, intent: Intent) {
        super.onReceive(c, intent)
        val action = intent.action ?: return
        if (action != ACTION_BRIGHT_UP && action != ACTION_BRIGHT_DOWN) return
        if (!WidgetState.hasStrip(c) || WidgetState.isBusy(c)) {
            WidgetUpdater.refreshAll(c)
            return
        }
        WidgetState.begin(c)
        WidgetUpdater.refreshAll(c)
        c.sendBroadcast(Intent(c, WidgetInlineReceiver::class.java).setAction(action))
    }
}
