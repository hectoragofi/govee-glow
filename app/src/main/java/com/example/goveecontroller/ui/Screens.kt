package com.example.goveecontroller.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.goveecontroller.ble.GoveeBleManager
import com.example.goveecontroller.ble.GoveeScene
import com.example.goveecontroller.ble.MusicProfile

// ---------------------------------------------------------------------------
// CONTROL — power, brightness, color picker
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlScreen(ble: GoveeBleManager) {
    val ctx = LocalContext.current
    val connected by ble.connected.collectAsState()
    val connecting by ble.connecting.collectAsState()
    val name by ble.connectedName.collectAsState()
    val saved by ble.savedName.collectAsState()
    var power by remember { mutableStateOf(true) }
    var brightness by remember { mutableFloatStateOf(80f) }
    var current by remember { mutableStateOf(Color(0xFF008CFF)) }
    val presets = listOf(
        "Sunset" to Color(0xFFFF6B35), "Rose" to Color(0xFFFF2E88),
        "Violet" to Color(0xFF8B5CF6), "Aqua" to Color(0xFF00E5CC),
        "Lime" to Color(0xFFA3E635), "Amber" to Color(0xFFFFB300),
        "White" to Color.White,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ConnectionCard(ble, connected, connecting, name, saved) }
        item {
            ElevatedCard(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Power", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = power,
                            onCheckedChange = { power = it; ble.power(it) },
                            thumbContent = { Icon(Icons.Default.PowerSettingsNew, null) },
                        )
                    }
                    Text("Brightness ${(brightness.toInt())}%", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = brightness,
                        onValueChange = { brightness = it },
                        onValueChangeFinished = { ble.brightness(brightness.toInt()) },
                        valueRange = 1f..100f,
                    )
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Color", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    HsvPicker(
                        initial = current,
                        onChange = { current = it.toColor() },
                        onDone = {
                            current = it.toColor()
                            sendSolid(ble, ctx, current)
                        },
                    )
                    CustomColorsRow(current) { c ->
                        current = c
                        sendSolid(ble, ctx, c)
                    }
                    RecentsRow { c ->
                        current = c
                        sendSolid(ble, ctx, c)
                    }
                    // Animated glow strip — mirrors your pick with M3 motion.
                    val glow by animateColorAsState(current, label = "glow")
                    Box(
                        Modifier.fillMaxWidth().height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(glow)
                            .clickable {
                                ble.color(
                                    (current.red * 255).toInt(),
                                    (current.green * 255).toInt(),
                                    (current.blue * 255).toInt(),
                                )
                            },
                    )
                    Text("Presets", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        presets.forEach { (label, c) ->
                            Row(
                                Modifier.clip(CircleShape).clickable {
                                    current = c
                                    sendSolid(ble, ctx, c)
                                }.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(18.dp).clip(CircleShape).background(c))
                                Spacer(Modifier.size(8.dp))
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionCard(
    ble: GoveeBleManager,
    connected: Boolean,
    connecting: Boolean,
    name: String?,
    saved: String?,
) {
    ElevatedCard(shape = RoundedCornerShape(28.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                connecting -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                else -> Icon(
                    if (connected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                    null,
                    tint = if (connected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        connected -> "Connected — $name"
                        connecting -> "Linking your strip…"
                        saved != null -> "Auto-link · $saved"
                        else -> "Not connected"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        connected -> "Keep-alive running every 2s ✓"
                        connecting -> "Handshake in progress"
                        saved != null -> "Will connect on open — no Lab needed"
                        else -> "Go to Lab tab → Scan → tap your Govee_H6125"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (connected) FilledTonalButton(onClick = { ble.disconnect() }) { Text("Drop") }
        }
    }
}

@Composable
fun AutoLinkCard(ble: GoveeBleManager) {
    val auto by ble.autoEnabled.collectAsState()
    val saved by ble.savedName.collectAsState()
    val connected by ble.connected.collectAsState()
    ElevatedCard(shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Auto-link strip", fontWeight = FontWeight.SemiBold)
                    Text(
                        saved ?: "No strip remembered yet",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = auto, onCheckedChange = { ble.setAutoEnabled(it) })
            }
            if (saved != null && !connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { ble.connectSaved() }) { Text("Connect now") }
                    FilledTonalButton(onClick = { ble.forgetDevice() }) { Text("Forget") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// GRADIENT — smooth multi-segment blends
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GradientScreen(ble: GoveeBleManager) {
    var flip by remember { mutableStateOf(false) }
    var hwGradient by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(Color(0xFFFF3D00)) }
    var end by remember { mutableStateOf(Color(0xFF7B1FA2)) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Presets", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Painted as 15 per-segment blends, verified on your strip.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GRADIENT_PRESETS.forEach { preset ->
                        Column(
                            Modifier.clip(RoundedCornerShape(22.dp))
                                .clickable {
                                    ble.applyGradient(interpolateGradient(preset.stops), flip)
                                },
                        ) {
                            GradientBar(preset.stops)
                            Text(
                                preset.name,
                                Modifier.padding(top = 4.dp, bottom = 10.dp),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Flip direction", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Segment 1 is at the far end of some installs",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = flip, onCheckedChange = { flip = it })
                    }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Custom blend", style = MaterialTheme.typography.titleMedium)
                    Text("Start", style = MaterialTheme.typography.labelLarge)
                    HsvPicker(initial = start, onChange = { start = it.toColor() }, onDone = {})
                    Text("End", style = MaterialTheme.typography.labelLarge)
                    HsvPicker(initial = end, onChange = { end = it.toColor() }, onDone = {})
                    GradientBar(listOf(start, end))
                    Button(
                        onClick = { ble.applyGradient(interpolateGradient(listOf(start, end)), flip) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    ) { Text("Apply blend") }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hardware gradient", fontWeight = FontWeight.SemiBold)
                        Text("33 A3 toggle — strip-side effect", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = hwGradient, onCheckedChange = {
                        hwGradient = it; ble.gradient(it)
                    })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SEGMENTS — RGBIC 15 zones with picker
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SegmentsScreen(ble: GoveeBleManager) {
    val ctx = LocalContext.current
    val selected = remember { mutableStateListOf<Int>().apply { addAll(1..15) } }
    var current by remember { mutableStateOf(Color(0xFFFF5000)) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Zones (15-zone RGBIC)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..15).forEach { seg ->
                            FilterChip(
                                selected = seg in selected,
                                onClick = {
                                    if (seg in selected) selected.remove(seg) else selected.add(seg)
                                },
                                label = { Text("$seg") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { selected.clear(); selected.addAll(1..15) }) { Text("All") }
                        FilledTonalButton(onClick = { selected.clear(); selected.addAll(1..8) }) { Text("Left") }
                        FilledTonalButton(onClick = { selected.clear(); selected.addAll(9..15) }) { Text("Right") }
                    }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HsvPicker(
                        initial = current,
                        onChange = { current = it.toColor() },
                        onDone = {
                            current = it.toColor()
                            PresetStore.record(ctx, hexOf(current))
                            paintSelected(ble, selected, current)
                        },
                    )
                    CustomColorsRow(current) { c ->
                        current = c
                        PresetStore.record(ctx, hexOf(c))
                        paintSelected(ble, selected, c)
                    }
                    Button(
                        onClick = {
                            PresetStore.record(ctx, hexOf(current))
                            paintSelected(ble, selected, current)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    ) { Text("Paint ${selected.size} zones") }
                }
            }
        }
    }
}

private fun sendSolid(ble: GoveeBleManager, ctx: android.content.Context, color: Color) {
    ble.color(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
    )
    PresetStore.record(ctx, hexOf(color))
}

private fun paintSelected(ble: GoveeBleManager, selected: List<Int>, color: Color) {
    val r = (color.red * 255).toInt()
    val g = (color.green * 255).toInt()
    val b = (color.blue * 255).toInt()
    var left = 0; var right = 0
    selected.forEach { s ->
        if (s <= 8) left = left or (1 shl (s - 1)) else right = right or (1 shl (s - 9))
    }
    ble.segment(r, g, b, left, right)
}

// ---------------------------------------------------------------------------
// EFFECTS — scenes + fixed music sync
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectsScreen(ble: GoveeBleManager) {
    var profile by remember { mutableStateOf(MusicProfile.RHYTHM) }
    var sensitivity by remember { mutableFloatStateOf(70f) }
    var palA by remember { mutableStateOf(Color(0xFFFF0080)) }
    var palB by remember { mutableStateOf(Color(0xFF00FFFF)) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Music sync — fixed ✓", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Uses the strip's mic. Profiles measured live on H6125_5242. " +
                            "Palette uploads first, then 33 05 13 activates. Room needs to be loud!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MusicProfile.entries.forEach { p ->
                            FilterChip(
                                selected = profile == p,
                                onClick = { profile = p },
                                label = { Text("${p.label} · ${"%02X".format(p.id)}") },
                            )
                        }
                    }
                    Text(profile.note, style = MaterialTheme.typography.labelMedium)
                    Text("Sensitivity ${sensitivity.toInt()}", style = MaterialTheme.typography.labelLarge)
                    Slider(value = sensitivity, onValueChange = { sensitivity = it }, valueRange = 0f..99f)
                    Text("Palette A", style = MaterialTheme.typography.labelLarge)
                    HsvPicker(initial = palA, onChange = { palA = it.toColor() }, onDone = {})
                    Text("Palette B", style = MaterialTheme.typography.labelLarge)
                    HsvPicker(initial = palB, onChange = { palB = it.toColor() }, onDone = {})
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                ble.startMusic(
                                    profile.id,
                                    listOf(palA.toTriple(), palB.toTriple()),
                                    sensitivity.toInt(),
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                        ) { Text("Start dancing") }
                        FilledTonalButton(
                            onClick = { ble.stopMusic(255, 224, 178) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Stop") }
                    }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Scenes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoveeScene.entries.forEach { s ->
                            FilledTonalButton(onClick = { ble.scene(s.id, s.label) }) {
                                Text("${s.label} · ${"%02X".format(s.id)}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Color.toTriple(): Triple<Int, Int, Int> =
    Triple((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

// ---------------------------------------------------------------------------
// LAB — scanner, raw packets, sniffer log
// ---------------------------------------------------------------------------

@Composable
fun ThemeCard(ble: GoveeBleManager) {
    val mode by ble.themeMode.collectAsState()
    ElevatedCard(shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Look", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Wallpaper-matched dynamic color, Material 3 Expressive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    GoveeBleManager.THEME_SYSTEM to "Auto",
                    GoveeBleManager.THEME_LIGHT to "Light",
                    GoveeBleManager.THEME_DARK to "Dark",
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = (mode ?: GoveeBleManager.THEME_SYSTEM) == id,
                        onClick = { ble.setThemeMode(id) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@Composable
fun SystemCard() {
    ElevatedCard(shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("System", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Home-screen widgets ✓ · swipe-down Quick Tile: edit your tiles and add “Glow Strip” · " +
                    "Devices panel: same place Google Home lives, under Device controls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabScreen(ble: GoveeBleManager) {
    val scanning by ble.scanning.collectAsState()
    val found by ble.found.collectAsState()
    val log by ble.log.collectAsState()
    var raw by remember { mutableStateOf("3301010000000000000000000000000000000033") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { AutoLinkCard(ble) }
        item { ThemeCard(ble) }
        item { SystemCard() }
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Scanner", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { if (scanning) ble.stopScan() else ble.startScan() }) {
                            Icon(Icons.Default.Refresh, null)
                        }
                    }
                    Button(
                        onClick = { if (scanning) ble.stopScan() else ble.startScan() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(if (scanning) Icons.Default.BluetoothDisabled else Icons.Default.Bluetooth, null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (scanning) "Scanning… tap to stop" else "Scan 15s")
                    }
                    if (found.isEmpty()) {
                        Text("No devices yet — keep the strip powered and nearby.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    found.forEach { dev ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .clickable { ble.connect(dev) }
                                .background(
                                    if (dev.name.contains("Govee", true)) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(dev.name, fontWeight = FontWeight.SemiBold)
                                Text(dev.address, fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${dev.rssi} dBm", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.size(6.dp))
                    }
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Raw packet sender", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = raw,
                        onValueChange = { raw = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Hex, 20 bytes (19 + auto-XOR ok)") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { ble.sendRawHex(raw) }) {
                            Icon(Icons.Default.Send, null); Spacer(Modifier.size(4.dp)); Text("Send")
                        }
                        FilledTonalButton(onClick = { raw = "3301010000000000000000000000000000000033" }) { Text("ON") }
                        FilledTonalButton(onClick = { raw = "3301000000000000000000000000000000000032" }) { Text("OFF") }
                    }
                    Text(
                        "Verified H6125 frames: color 33 05 15 01 RRGGBB + 5×00 + mask · music 33 05 13 profile sens",
                        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            ElevatedCard(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Packet log (${log.size})", style = MaterialTheme.typography.titleMedium)
                    log.take(40).forEach { entry ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(entry.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(entry.hex, fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
