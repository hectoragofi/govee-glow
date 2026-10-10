package com.example.goveecontroller.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.goveecontroller.ble.GoveeBleManager
import com.example.goveecontroller.widget.AutomationRunner
import com.example.goveecontroller.widget.AutomationRunner.sunOff
import com.example.goveecontroller.widget.AutomationRunner.sunOffOffset
import com.example.goveecontroller.widget.AutomationRunner.sunOn
import com.example.goveecontroller.widget.AutomationRunner.sunOnOffset
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutoScreen() {
    val ctx = LocalContext.current
    var schedules by remember { mutableStateOf(AutomationRunner.load(ctx)) }
    var showAdd by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    fun reload() {
        schedules = AutomationRunner.load(ctx)
        refresh++
    }

    val alarmOk = remember {
        val am = ctx.getSystemService(AlarmManager::class.java)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am?.canScheduleExactAlarms() == true
    }

    LazyColumn(
        Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!alarmOk) {
            item {
                ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Exact alarms off", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Android needs this for on-time automations.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = android.net.Uri.parse("package:${ctx.packageName}")
                                },
                            )
                        }) { Text("Allow") }
                    }
                }
            }
        }
        item { SunCard { reload() } }
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Schedules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showAdd = true }) {
                            Icon(Icons.Default.Add, "Add schedule")
                        }
                    }
                    if (schedules.isEmpty()) {
                        Text(
                            "Nothing yet — tap + for lights-on at bedtime, off at sunrise, …",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    schedules.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (s.label.ifEmpty { actionName(s.action) }) +
                                        " · %02d:%02d".format(s.hour, s.minute),
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    daySummary(s.days),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = s.enabled,
                                onCheckedChange = { on ->
                                    AutomationRunner.save(ctx, schedules.map {
                                        if (it.id == s.id) it.copy(enabled = on) else it
                                    })
                                    reload()
                                },
                            )
                            IconButton(onClick = {
                                AutomationRunner.save(ctx, schedules.filter { it.id != s.id })
                                reload()
                            }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                    val upcoming = remember(refresh) { AutomationRunner.upcoming(ctx) }
                    if (upcoming.isNotEmpty()) {
                        Text("Up next", style = MaterialTheme.typography.titleMedium)
                        upcoming.forEach { (label, t) ->
                            Text(
                                "• $label — ${t.format(DateTimeFormatter.ofPattern("EEE HH:mm"))}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddScheduleDialog(
            onDismiss = { showAdd = false },
            onSave = { label, h, m, days, action ->
                val list = AutomationRunner.load(ctx) + AutomationRunner.Schedule(
                    UUID.randomUUID().toString(), label, h, m, days, action, true,
                )
                AutomationRunner.save(ctx, list)
                reload()
                showAdd = false
            },
        )
    }
}

private fun actionName(a: String): String = when {
    a == "on" -> "Turn on"
    a == "off" -> "Turn off"
    a.startsWith("bright:") -> "Brightness ${a.removePrefix("bright:")}%"
    a.startsWith("color:#") -> "Color ${a.removePrefix("color:")}"
    else -> a
}

private fun daySummary(days: Set<Int>): String {
    if (days.size == 7) return "Daily"
    if (days == setOf(1, 2, 3, 4, 5)) return "Weekdays"
    if (days == setOf(6, 7)) return "Weekends"
    return (1..7).joinToString(" ") { if (it in days) DAY_LETTERS[it - 1] else "·" }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddScheduleDialog(onDismiss: () -> Unit, onSave: (String, Int, Int, Set<Int>, String) -> Unit) {
    val clock = rememberTimePickerState(initialHour = 22, initialMinute = 0, is24Hour = true)
    var label by remember { mutableStateOf("") }
    val days = remember { mutableStateListOf<Int>().apply { addAll(1..7) } }
    var action by remember { mutableStateOf("off") }
    var bright by remember { mutableFloatStateOf(30f) }
    val colors = listOf("#FF6B35", "#FF2E88", "#8B5CF6", "#00E5CC", "#A3E635", "#FFFFFF")
    var colorHex by remember { mutableStateOf(colors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New schedule") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = label, onValueChange = { label = it },
                        label = { Text("Name (optional)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TimePicker(clock)
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { d ->
                            FilterChip(
                                selected = d in days,
                                onClick = { if (d in days) days.remove(d) else days.add(d) },
                                label = { Text(DAY_LETTERS[d - 1]) },
                            )
                        }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = action == "on", onClick = { action = "on" }, label = { Text("On") })
                        FilterChip(selected = action == "off", onClick = { action = "off" }, label = { Text("Off") })
                        FilterChip(selected = action.startsWith("bright:"), onClick = { action = "bright:${bright.toInt()}" }, label = { Text("Dim") })
                        FilterChip(selected = action.startsWith("color:"), onClick = { action = "color:$colorHex" }, label = { Text("Color") })
                    }
                }
                if (action.startsWith("bright:")) {
                    item {
                        Text("Brightness ${bright.toInt()}%")
                        Slider(value = bright, onValueChange = {
                            bright = it
                            action = "bright:${it.toInt()}"
                        }, valueRange = 1f..100f)
                    }
                }
                if (action.startsWith("color:")) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            colors.forEach { hex ->
                                val c = Color(android.graphics.Color.parseColor(hex))
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(c)
                                        .clickable {
                                            colorHex = hex
                                            action = "color:$hex"
                                        }
                                        .then(
                                            if (colorHex == hex) Modifier.border(
                                                2.dp, MaterialTheme.colorScheme.primary, CircleShape,
                                            ) else Modifier,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty(),
                onClick = { onSave(label.trim(), clock.hour, clock.minute, days.toSet(), action) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SunCard(onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var located by remember { mutableStateOf(AutomationRunner.hasLocation(ctx)) }
    var on by remember { mutableStateOf(ctx.sunOn) }
    var onOff by remember { mutableIntStateOf(ctx.sunOnOffset) }
    var off by remember { mutableStateOf(ctx.sunOff) }
    var offOff by remember { mutableIntStateOf(ctx.sunOffOffset) }

    val locPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && AutomationRunner.captureLocation(ctx)) {
            located = true
            AutomationRunner.scheduleAll(ctx)
            onChanged()
        }
    }

    fun needLoc(): Boolean {
        if (AutomationRunner.hasLocation(ctx)) return true
        val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (has && AutomationRunner.captureLocation(ctx)) {
            located = true
            return true
        }
        locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        return false
    }

    ElevatedCard(shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sunset & sunrise", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                if (located) "Location ${AutomationRunner.locationLabel(ctx)} — times computed on-device."
                else "Needs your rough location (stays on this phone).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!located) {
                FilledTonalButton(onClick = { needLoc() }) { Text("Use my location") }
            }
            OffsetRow("Lights on at sunset", onOff) { onOff = it; ctx.sunOnOffset = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sunset → ON", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(checked = on, onCheckedChange = {
                    if (it && !needLoc()) return@Switch
                    on = it; ctx.sunOn = it
                    AutomationRunner.scheduleAll(ctx); onChanged()
                })
            }
            OffsetRow("Lights off at sunrise", offOff) { offOff = it; ctx.sunOffOffset = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sunrise → OFF", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Switch(checked = off, onCheckedChange = {
                    if (it && !needLoc()) return@Switch
                    off = it; ctx.sunOff = it
                    AutomationRunner.scheduleAll(ctx); onChanged()
                })
            }
            val today = remember(located, on, off) {
                if (!located) null else try {
                    val p = ctx.getSharedPreferences("govee", Context.MODE_PRIVATE)
                    val lat = p.getFloat("sun_lat", 0f).toDouble()
                    val lon = p.getFloat("sun_lon", 0f).toDouble()
                    val rise = AutomationRunner.sunTime(LocalDate.now(), lat, lon, true)
                    val set = AutomationRunner.sunTime(LocalDate.now(), lat, lon, false)
                    "Today: ↑ ${rise?.toLocalTime()?.toString() ?: "--"}  ↓ ${set?.toLocalTime()?.toString() ?: "--"}"
                } catch (_: Exception) {
                    null
                }
            }
            if (today != null) Text(today, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OffsetRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        FilledTonalButton(onClick = { onChange((value - 15).coerceAtLeast(-120)) }) { Text("−15") }
        Spacer(Modifier.width(8.dp))
        Text(
            if (value == 0) "exact" else "%+d min".format(value),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(72.dp),
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = { onChange((value + 15).coerceAtMost(120)) }) { Text("+15") }
    }
}
