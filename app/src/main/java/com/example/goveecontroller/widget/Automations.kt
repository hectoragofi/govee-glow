package com.example.goveecontroller.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.util.Log
import com.example.goveecontroller.ble.GoveeBleManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * Automations: fixed-time schedules + sunset/sunrise rules.
 * Stored as JSON in prefs; fired by exact alarms → WidgetInlineReceiver.
 */
object AutomationRunner {
    const val PREFIX = "com.example.goveecontroller.AUTO#"
    private const val TAG = "GoveeGlow"
    private const val KEY_SCHED = "schedules"
    private const val KEY_SUN_ON = "sun_on"
    private const val KEY_SUN_ON_OFFSET = "sun_on_offset"
    private const val KEY_SUN_OFF = "sun_off"
    private const val KEY_SUN_OFF_OFFSET = "sun_off_offset"
    private const val KEY_LAT = "sun_lat"
    private const val KEY_LON = "sun_lon"

    data class Schedule(
        val id: String,
        val label: String,
        val hour: Int,
        val minute: Int,
        val days: Set<Int>, // 1=Mon..7=Sun
        val action: String, // on | off | bright:N | color:#RRGGBB
        val enabled: Boolean,
    )

    private fun prefs(c: Context) = c.getSharedPreferences("govee", Context.MODE_PRIVATE)

    // ---- store ----

    fun load(c: Context): List<Schedule> {
        val out = mutableListOf<Schedule>()
        val arr = try {
            JSONArray(prefs(c).getString(KEY_SCHED, "[]"))
        } catch (_: Exception) {
            return out
        }
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val days = mutableSetOf<Int>()
                val da = o.optJSONArray("days")
                if (da != null) for (j in 0 until da.length()) days.add(da.getInt(j))
                out.add(
                    Schedule(
                        o.getString("id"), o.optString("label", ""),
                        o.getInt("hour"), o.getInt("minute"), days,
                        o.getString("action"), o.optBoolean("enabled", true),
                    ),
                )
            } catch (_: Exception) {}
        }
        return out
    }

    fun save(c: Context, list: List<Schedule>) {
        val arr = JSONArray()
        list.forEach { s ->
            val o = JSONObject()
                .put("id", s.id).put("label", s.label)
                .put("hour", s.hour).put("minute", s.minute)
                .put("action", s.action).put("enabled", s.enabled)
            val da = JSONArray()
            s.days.forEach { da.put(it) }
            o.put("days", da)
            arr.put(o)
        }
        prefs(c).edit().putString(KEY_SCHED, arr.toString()).apply()
        scheduleAll(c)
    }

    // ---- sun settings ----

    var Context.sunOn: Boolean
        get() = prefs(this).getBoolean(KEY_SUN_ON, false)
        set(v) = prefs(this).edit().putBoolean(KEY_SUN_ON, v).apply()
    var Context.sunOnOffset: Int
        get() = prefs(this).getInt(KEY_SUN_ON_OFFSET, 0)
        set(v) = prefs(this).edit().putInt(KEY_SUN_ON_OFFSET, v).apply()
    var Context.sunOff: Boolean
        get() = prefs(this).getBoolean(KEY_SUN_OFF, false)
        set(v) = prefs(this).edit().putBoolean(KEY_SUN_OFF, v).apply()
    var Context.sunOffOffset: Int
        get() = prefs(this).getInt(KEY_SUN_OFF_OFFSET, 0)
        set(v) = prefs(this).edit().putInt(KEY_SUN_OFF_OFFSET, v).apply()

    fun hasLocation(c: Context): Boolean =
        prefs(c).contains(KEY_LAT)

    fun locationLabel(c: Context): String {
        val p = prefs(c)
        if (!p.contains(KEY_LAT)) return "No location yet"
        return "%.2f, %.2f".format(p.getFloat(KEY_LAT, 0f), p.getFloat(KEY_LON, 0f))
    }

    @SuppressLint("MissingPermission")
    fun captureLocation(c: Context): Boolean {
        try {
            val lm = c.getSystemService(LocationManager::class.java) ?: return false
            val loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: return false
            prefs(c).edit()
                .putFloat(KEY_LAT, loc.latitude.toFloat())
                .putFloat(KEY_LON, loc.longitude.toFloat())
                .apply()
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    // ---- run ----

    /** Executes one fired schedule. Returns false if id unknown/disabled. */
    suspend fun runScheduled(c: Context, mgr: GoveeBleManager, intentAction: String): Boolean {
        val id = intentAction.removePrefix(PREFIX)
        if (id == "__sunset") {
            Log.d(TAG, "automation firing: sunset -> ON")
            mgr.power(true)
            return true
        }
        if (id == "__sunrise") {
            Log.d(TAG, "automation firing: sunrise -> OFF")
            mgr.power(false)
            return true
        }
        val s = load(c).find { it.id == id && it.enabled } ?: return false
        Log.d(TAG, "automation firing: ${s.label.ifEmpty { id }} -> ${s.action}")
        when {
            s.action == "on" -> mgr.power(true)
            s.action == "off" -> mgr.power(false)
            s.action.startsWith("bright:") ->
                mgr.brightness(s.action.removePrefix("bright:").toIntOrNull() ?: 80)
            s.action.startsWith("color:#") -> {
                val hex = s.action.removePrefix("color:#")
                if (hex.length == 6) {
                    val r = hex.substring(0, 2).toInt(16)
                    val g = hex.substring(2, 4).toInt(16)
                    val b = hex.substring(4, 6).toInt(16)
                    mgr.color(r, g, b)
                }
            }
            else -> return false
        }
        return true
    }

    // ---- (re)schedule all alarms ----

    fun scheduleAll(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val now = ZonedDateTime.now()
        // fixed schedules
        load(c).filter { it.enabled && it.days.isNotEmpty() }.forEach { s ->
            nextOccurrence(s, now)?.let { t ->
                fireAt(c, am, "$PREFIX${s.id}", t, s.id.hashCode())
            }
        }
        // sun rules
        val p = prefs(c)
        if (p.contains(KEY_LAT)) {
            val lat = p.getFloat(KEY_LAT, 0f).toDouble()
            val lon = p.getFloat(KEY_LON, 0f).toDouble()
            if (c.sunOn) {
                sunTime(LocalDate.now(), lat, lon, sunrise = false)?.let {
                    fireAt(c, am, "${PREFIX}__sunset", it.plusMinutes(c.sunOnOffset.toLong()), 9001)
                }
            }
            if (c.sunOff) {
                sunTime(LocalDate.now(), lat, lon, sunrise = true)?.let {
                    fireAt(c, am, "${PREFIX}__sunrise", it.plusMinutes(c.sunOffOffset.toLong()), 9002)
                }
            }
        }
        // daily replant (recompute sun times after midnight)
        val tomorrow = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(5)
        fireAt(c, am, "${PREFIX}__replant", tomorrow, 9000)
    }

    private fun fireAt(c: Context, am: AlarmManager, action: String, t: ZonedDateTime, code: Int) {
        if (!t.isAfter(ZonedDateTime.now().minusMinutes(1))) {
            // roll sun events that already passed today to tomorrow
            if (action.endsWith("__sunset") || action.endsWith("__sunrise")) {
                return fireAt(c, am, action, t.plusDays(1), code)
            }
            return
        }
        if (action == "${PREFIX}__replant") {
            // replant just reschedules; handled in receiver
        }
        val i = Intent(c, WidgetInlineReceiver::class.java).setAction(action)
        val pi = PendingIntent.getBroadcast(
            c, code, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.toInstant().toEpochMilli(), pi)
            } else {
                @Suppress("DEPRECATION")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.toInstant().toEpochMilli(), pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, t.toInstant().toEpochMilli(), pi)
        }
    }

    private fun nextOccurrence(s: Schedule, now: ZonedDateTime): ZonedDateTime? {
        for (d in 0..7) {
            val day = now.toLocalDate().plusDays(d.toLong())
            val dow = day.dayOfWeek.value // 1=Mon
            if (dow !in s.days) continue
            val t = ZonedDateTime.of(LocalDateTime.of(day, java.time.LocalTime.of(s.hour, s.minute)), now.zone)
            if (t.isAfter(now)) return t
        }
        return null
    }

    /** Next upcoming events (for UI preview). */
    fun upcoming(c: Context): List<Pair<String, ZonedDateTime>> {
        val out = mutableListOf<Pair<String, ZonedDateTime>>()
        val now = ZonedDateTime.now()
        load(c).filter { it.enabled }.forEach { s ->
            nextOccurrence(s, now)?.let { out.add((s.label.ifEmpty { s.action }) to it) }
        }
        val p = prefs(c)
        if (p.contains(KEY_LAT)) {
            val lat = p.getFloat(KEY_LAT, 0f).toDouble()
            val lon = p.getFloat(KEY_LON, 0f).toDouble()
            if (c.sunOn) sunTime(LocalDate.now(), lat, lon, false)
                ?.plusMinutes(c.sunOnOffset.toLong())?.let { out.add("Sunset → ON" to it) }
            if (c.sunOff) sunTime(LocalDate.now(), lat, lon, true)
                ?.plusMinutes(c.sunOffOffset.toLong())?.let { out.add("Sunrise → OFF" to it) }
        }
        return out.sortedBy { it.second }.take(5)
    }

    // ---- solar math (NOAA sunrise equation) ----

    fun sunTime(date: LocalDate, lat: Double, lon: Double, sunrise: Boolean): ZonedDateTime? {
        val zone = ZoneId.systemDefault()
        val n = date.dayOfYear
        val lngHour = lon / 15.0
        val t = n + (if (sunrise) 6.0 - lngHour else 18.0 - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634
        l = ((l % 360) + 360) % 360
        var ra = Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(l))))
        ra = ((ra % 360) + 360) % 360
        val lQuad = Math.floor(l / 90) * 90
        val raQuad = Math.floor(ra / 90) * 90
        ra += lQuad - raQuad
        ra /= 15.0
        val sinDec = 0.39782 * sinD(l)
        val cosDec = Math.cos(Math.asin(sinDec))
        val zenith = Math.toRadians(90.833)
        val cosH = (Math.cos(zenith) - sinDec * sinD(lat)) / (cosDec * Math.cos(Math.toRadians(lat)))
        if (cosH > 1 || cosH < -1) return null // polar day/night
        val h = if (sunrise) 360 - Math.toDegrees(Math.acos(cosH)) else Math.toDegrees(Math.acos(cosH))
        val hh = h / 15.0
        val tt = hh + ra - 0.06571 * t - 6.622
        var ut = tt - lngHour
        ut = ((ut % 24) + 24) % 24
        val offsetH = zone.rules.getOffset(date.atStartOfDay(zone).toInstant()).totalSeconds / 3600.0
        var local = ut + offsetH
        var d = date
        if (local >= 24) { local -= 24; d = d.plusDays(1) }
        if (local < 0) { local += 24; d = d.minusDays(1) }
        val hh2 = local.toInt()
        val mm = ((local - hh2) * 60).toInt()
        return ZonedDateTime.of(d, java.time.LocalTime.of(hh2 % 24, mm.coerceIn(0, 59)), zone)
    }

    private fun sinD(d: Double) = Math.sin(Math.toRadians(d))
}

/** Reschedules alarms after reboot / clock change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_TIME_CHANGED ||
            intent.action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            AutomationRunner.scheduleAll(c)
        }
    }
}
