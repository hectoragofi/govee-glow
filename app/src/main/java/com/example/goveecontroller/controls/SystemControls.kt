package com.example.goveecontroller.controls

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.RangeTemplate
import android.service.controls.templates.ToggleTemplate
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.goveecontroller.R
import com.example.goveecontroller.ble.GoveeBleManager
import com.example.goveecontroller.widget.ACTION_BRIGHT_DOWN
import com.example.goveecontroller.widget.ACTION_BRIGHT_UP
import com.example.goveecontroller.widget.ACTION_TOGGLE
import com.example.goveecontroller.widget.WidgetRunner
import java.util.concurrent.Flow
import java.util.function.Consumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "GoveeGlow"
private const val CONTROL_POWER = "glow_power"
private const val CONTROL_BRIGHT = "glow_bright"

private fun prefs(c: Context) = c.getSharedPreferences("govee", Context.MODE_PRIVATE)

/** Swipe-down Quick Settings tile: tap toggles the strip. */
class GlowTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        val on = prefs(this).getBoolean(GoveeBleManager.KEY_POWER, true)
        qsTile?.apply {
            state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Glow Strip"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val locked = isLocked
        if (locked) {
            unlockAndRun { runToggle() }
        } else {
            runToggle()
        }
    }

    private fun runToggle() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_UNAVAILABLE
        tile.updateTile()
        scope.launch {
            val ok = WidgetRunner.execute(this@GlowTileService, ACTION_TOGGLE, 12_000)
            val on = prefs(this@GlowTileService).getBoolean(GoveeBleManager.KEY_POWER, true)
            tile.state = if (ok) {
                if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            } else {
                Tile.STATE_UNAVAILABLE
            }
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        // System prompt for adding tiles isn't reachable from this API level;
        // users add it via the QS edit sheet (see Lab card text).
    }
}

/**
 * System Device Controls (power-menu / "Devices" panel): power toggle +
 * brightness slider. Appears next to Google Home devices at system level.
 */
@RequiresApi(Build.VERSION_CODES.R)
class GlowControlsService : ControlsProviderService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun powerControl(): Control {
        val on = prefs(this).getBoolean(GoveeBleManager.KEY_POWER, true)
        val name = prefs(this).getString("last_name", "Glow Strip") ?: "Glow Strip"
        return Control.StatefulBuilder(CONTROL_POWER, android.app.PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        ))
            .setTitle("Glow Strip")
            .setSubtitle(if (on) "$name · ON" else "$name · OFF")
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setStatus(Control.STATUS_OK)
            .setControlTemplate(ToggleTemplate(CONTROL_POWER, ControlButton(on, if (on) "On" else "Off")))
            .build()
    }

    private fun brightControl(): Control {
        val p = prefs(this).getInt(GoveeBleManager.KEY_BRIGHT, 80).toFloat()
        return Control.StatefulBuilder(CONTROL_BRIGHT, android.app.PendingIntent.getActivity(
            this, 1,
            packageManager.getLaunchIntentForPackage(packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        ))
            .setTitle("Glow Brightness")
            .setSubtitle("${p.toInt()}%")
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setStatus(Control.STATUS_OK)
            .setControlTemplate(RangeTemplate(CONTROL_BRIGHT, 1f, 100f, p, 1f, "%.0f%%"))
            .build()
    }

    override fun createPublisherFor(controlIds: List<String>): Flow.Publisher<Control> =
        Flow.Publisher { subscriber ->
            subscriber.onSubscribe(object : Flow.Subscription {
                override fun request(n: Long) {
                    controlIds.forEach {
                        when (it) {
                            CONTROL_POWER -> subscriber.onNext(powerControl())
                            CONTROL_BRIGHT -> subscriber.onNext(brightControl())
                        }
                    }
                    subscriber.onComplete()
                }
                override fun cancel() {}
            })
        }

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> =
        createPublisherFor(listOf(CONTROL_POWER, CONTROL_BRIGHT))

    override fun performControlAction(
        controlId: String,
        action: ControlAction,
        consumer: Consumer<Int>,
    ) {
        scope.launch {
            val ok = when {
                controlId == CONTROL_POWER && action is BooleanAction ->
                    WidgetRunner.execute(
                        this@GlowControlsService,
                        ACTION_TOGGLE, 12_000,
                    )
                controlId == CONTROL_BRIGHT && action is FloatAction -> {
                    // Snap straight to the exact value.
                    val target = action.newValue.toInt().coerceIn(1, 100)
                    val mgr = GoveeBleManager(applicationContext)
                    try {
                        mgr.autoConnect()
                        val linked = withTimeoutOrNull(12_000) {
                            mgr.connected.first { it }
                        } != null
                        if (linked) {
                            mgr.brightness(target)
                            delay(500)
                        }
                        linked
                    } finally {
                        mgr.disconnect()
                    }
                }
                else -> false
            }
            consumer.accept(if (ok) ControlAction.RESPONSE_OK else ControlAction.RESPONSE_FAIL)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
