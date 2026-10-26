package com.example.goveecontroller.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.goveecontroller.widget.AutomationRunner
import com.example.goveecontroller.widget.AutomationRunner.sunOff
import com.example.goveecontroller.widget.AutomationRunner.sunOffOffset
import com.example.goveecontroller.widget.AutomationRunner.sunOn
import com.example.goveecontroller.widget.AutomationRunner.sunOnOffset
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val DAY_LETTERS = listOf("M", "T", "W", "T", "F", "S", "S")
private val UPCOMING_FORMAT = DateTimeFormatter.ofPattern("EEE HH:mm")

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
    val upcoming = remember(refresh) { AutomationRunner.upcoming(ctx) }

    GlowList {
        item {
            ScreenHeader(
                eyebrow = "Auto",
                title = "Set it and forget it.",
                subtitle = "Fixed-time schedules and sunrise rules, fired by Android alarms even when the screen is off.",
            )
        }
        if (!alarmOk) {
            item { AlarmBanner(ctx) }
        }
        item { SunCard(onChanged = { reload() }) }
        item {
            SchedulesCard(
                schedules = schedules,
                upcoming = upcoming,
                onAdd = { showAdd = true },
                onToggle = { schedule, enabled ->
                    AutomationRunner.save(
                        ctx,
                        schedules.map { if (it.id == schedule.id) it.copy(enabled = enabled) else it },
                    )
                    reload()
                },
                onDelete = { schedule ->
                    AutomationRunner.save(ctx, schedules.filter { it.id != schedule.id })
                    reload()
                },
            )
        }
    }

    if (showAdd) {
        ScheduleSheet(
            onDismiss = { showAdd = false },
            onSave = { label, hour, minute, days, action ->
                val list = AutomationRunner.load(ctx) + AutomationRunner.Schedule(
                    id = UUID.randomUUID().toString(),
                    label = label,
                    hour = hour,
                    minute = minute,
                    days = days,
                    action = action,
                    enabled = true,
                )
                AutomationRunner.save(ctx, list)
                reload()
                showAdd = false
            },
        )
    }
}

@Composable
private fun AlarmBanner(ctx: Context) {
    GlowCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        borderColor = Color.Transparent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AlarmOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Exact alarms are off",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    "Android may delay automations by minutes without this permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                )
            }
        }
        Button(
            onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                    },
                )
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onErrorContainer,
                contentColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) { Text("Allow") }
    }
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
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted && AutomationRunner.captureLocation(ctx)) {
            located = true
            return true
        }
        locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        return false
    }

    val sunTimes: Pair<LocalTime?, LocalTime?>? = remember(located) {
        if (!located) null else try {
            val prefs = ctx.getSharedPreferences("govee", Context.MODE_PRIVATE)
            val lat = prefs.getFloat("sun_lat", 0f).toDouble()
            val lon = prefs.getFloat("sun_lon", 0f).toDouble()
            val rise = AutomationRunner.sunTime(LocalDate.now(), lat, lon, true)?.toLocalTime()
            val set = AutomationRunner.sunTime(LocalDate.now(), lat, lon, false)?.toLocalTime()
            rise to set
        } catch (_: Exception) {
            null
        }
    }

    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sunset & sunrise", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (located) "Location ${AutomationRunner.locationLabel(ctx)}. Computed on this phone."
                    else "Needs a rough location. It never leaves the phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        if (sunTimes != null) {
            SunArc(rise = sunTimes.first, set = sunTimes.second)
        } else if (!located) {
            Button(
                onClick = { needLoc() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Use my location") }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        SunRule(
            title = "Sunset turns lights on",
            detail = "Runs at sunset plus any shift you set.",
            checked = on,
            offset = onOff,
            onCheckedChange = { want ->
                if (!want || needLoc()) {
                    on = want
                    ctx.sunOn = want
                    AutomationRunner.scheduleAll(ctx)
                    onChanged()
                }
            },
            onOffsetChange = {
                onOff = it
                ctx.sunOnOffset = it
            },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        SunRule(
            title = "Sunrise turns lights off",
            detail = "Same idea, on the morning side.",
            checked = off,
            offset = offOff,
            onCheckedChange = { want ->
                if (!want || needLoc()) {
                    off = want
                    ctx.sunOff = want
                    AutomationRunner.scheduleAll(ctx)
                    onChanged()
                }
            },
            onOffsetChange = {
                offOff = it
                ctx.sunOffOffset = it
            },
        )
    }
}

@Composable
private fun SunRule(
    title: String,
    detail: String,
    checked: Boolean,
    offset: Int,
    onCheckedChange: (Boolean) -> Unit,
    onOffsetChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Shift",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onOffsetChange((offset - 15).coerceAtLeast(-120)) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Remove, "15 minutes earlier", Modifier.size(18.dp))
                    }
                    Text(
                        if (offset == 0) "Exact" else "%+d min".format(offset),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(76.dp),
                    )
                    IconButton(
                        onClick = { onOffsetChange((offset + 15).coerceAtMost(120)) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Add, "15 minutes later", Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SchedulesCard(
    schedules: List<AutomationRunner.Schedule>,
    upcoming: List<Pair<String, java.time.ZonedDateTime>>,
    onAdd: () -> Unit,
    onToggle: (AutomationRunner.Schedule, Boolean) -> Unit,
    onDelete: (AutomationRunner.Schedule) -> Unit,
) {
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Schedules", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (schedules.isEmpty()) "None yet"
                    else "${schedules.count { it.enabled }} of ${schedules.size} armed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = onAdd, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Add, "Add schedule")
            }
        }

        if (schedules.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Set lights on at bedtime, off after sunrise, or a dim color for movie nights.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onAdd,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add the first one")
                }
            }
        }

        schedules.forEachIndexed { index, schedule ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
            ScheduleRow(
                schedule = schedule,
                onToggle = { onToggle(schedule, it) },
                onDelete = { onDelete(schedule) },
            )
        }

        if (upcoming.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Text("Up next", style = MaterialTheme.typography.titleMedium)
            upcoming.forEach { (label, whenAt) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        whenAt.format(UPCOMING_FORMAT),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    schedule: AutomationRunner.Schedule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(70.dp)) {
            Text(
                "%02d:%02d".format(schedule.hour, schedule.minute),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                daySummary(schedule.days),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                schedule.label.ifEmpty { actionName(schedule.action) },
                style = MaterialTheme.typography.titleSmall,
            )
            if (schedule.label.isNotEmpty()) {
                Text(
                    actionName(schedule.action),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = schedule.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                "Delete schedule",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun actionName(action: String): String = when {
    action == "on" -> "Turn on"
    action == "off" -> "Turn off"
    action.startsWith("bright:") -> "Dim to ${action.removePrefix("bright:")}%"
    action.startsWith("color:#") -> "Set ${action.removePrefix("color:")}"
    else -> action
}

private fun daySummary(days: Set<Int>): String {
    if (days.size == 7) return "Daily"
    if (days == setOf(1, 2, 3, 4, 5)) return "Weekdays"
    if (days == setOf(6, 7)) return "Weekends"
    return (1..7).joinToString(" ") { if (it in days) DAY_LETTERS[it - 1] else "·" }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleSheet(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Set<Int>, String) -> Unit,
) {
    val clock = rememberTimePickerState(initialHour = 22, initialMinute = 0, is24Hour = true)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf("") }
    val days = remember { mutableStateListOf<Int>().apply { addAll(1..7) } }
    var action by remember { mutableStateOf("off") }
    var bright by remember { mutableFloatStateOf(30f) }
    var colorHex by remember { mutableStateOf(hexOf(GLOW_PICKS[4].color)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("New schedule", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "%02d:%02d · %s".format(
                            clock.hour,
                            clock.minute,
                            if (days.size == 7) "every day" else daySummary(days.toSet()),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(clock)
            }

            Text("Repeat", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                (1..7).forEach { day ->
                    FilterChip(
                        selected = day in days,
                        onClick = { if (day in days) days.remove(day) else days.add(day) },
                        label = { Text(DAY_LETTERS[day - 1]) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickDays("Weekdays") { days.clear(); days.addAll(1..5) }
                QuickDays("Weekends") { days.clear(); days.addAll(6..7) }
                QuickDays("Every day") { days.clear(); days.addAll(1..7) }
            }

            Text("Action", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(
                    label = "On",
                    icon = Icons.Default.PowerSettingsNew,
                    selected = action == "on",
                    modifier = Modifier.weight(1f),
                    onClick = { action = "on" },
                )
                ActionTile(
                    label = "Off",
                    icon = Icons.Default.PowerOff,
                    selected = action == "off",
                    modifier = Modifier.weight(1f),
                    onClick = { action = "off" },
                )
                ActionTile(
                    label = "Dim",
                    icon = Icons.Default.Brightness6,
                    selected = action.startsWith("bright:"),
                    modifier = Modifier.weight(1f),
                    onClick = { action = "bright:${bright.toInt()}" },
                )
                ActionTile(
                    label = "Color",
                    icon = Icons.Default.Palette,
                    selected = action.startsWith("color:"),
                    modifier = Modifier.weight(1f),
                    onClick = { action = "color:$colorHex" },
                )
            }

            if (action.startsWith("bright:")) {
                Column {
                    Text(
                        "Brightness ${bright.toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(
                        value = bright,
                        onValueChange = {
                            bright = it
                            action = "bright:${it.toInt()}"
                        },
                        valueRange = 1f..100f,
                    )
                }
            }

            if (action.startsWith("color:")) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Scheduled color $colorHex",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GLOW_PICKS.forEach { pick ->
                            val hex = hexOf(pick.color)
                            ColorSwatch(
                                color = pick.color,
                                onClick = {
                                    colorHex = hex
                                    action = "color:$hex"
                                },
                                selected = colorHex == hex,
                                size = 40.dp,
                                contentDescription = pick.name,
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onSave(
                        label.trim(),
                        clock.hour,
                        clock.minute,
                        days.toSet(),
                        action,
                    )
                },
                enabled = days.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp),
            ) { Text("Save schedule", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun QuickDays(label: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .clip(shape)
            .background(container)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) content.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}
