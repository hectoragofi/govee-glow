package com.example.goveecontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.goveecontroller.ble.GoveeBleManager
import com.example.goveecontroller.ble.PacketLog
import com.example.goveecontroller.ble.ScannedLight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LabScreen(ble: GoveeBleManager) {
    val scanning by ble.scanning.collectAsState()
    val found by ble.found.collectAsState()
    val log by ble.log.collectAsState()
    var raw by remember { mutableStateOf("3301010000000000000000000000000000000033") }
    var rawFeedback by remember { mutableStateOf<String?>(null) }

    GlowList {
        item {
            ScreenHeader(
                eyebrow = "Lab",
                title = "Under the hood.",
                subtitle = "Pairing, appearance, scanner, raw frames, and every write the app sends.",
            )
        }
        item { AutoLinkCard(ble) }
        item { ThemeCard(ble) }
        item { ShortcutsCard() }
        item { ScannerCard(ble, scanning, found) }
        item {
            RawPacketCard(
                raw = raw,
                onRawChange = {
                    raw = it
                    rawFeedback = null
                },
                feedback = rawFeedback,
                onSend = {
                    val ok = ble.sendRawHex(raw)
                    rawFeedback = when {
                        !ok -> "Rejected. A packet needs 20 bytes, and the last byte must XOR the first 19."
                        !ble.connected.value -> "Frame is valid, but the strip is offline. See the write log."
                        else -> "Frame queued for the strip."
                    }
                },
            )
        }
        item { PacketLogCard(log) }
    }
}

@Composable
private fun AutoLinkCard(ble: GoveeBleManager) {
    val auto by ble.autoEnabled.collectAsState()
    val saved by ble.savedName.collectAsState()
    val connected by ble.connected.collectAsState()
    val connecting by ble.connecting.collectAsState()

    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        connected -> Icons.Default.BluetoothConnected
                        saved != null -> Icons.Default.Bluetooth
                        else -> Icons.Default.BluetoothDisabled
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Auto-link", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        connected -> "Connected now"
                        connecting -> "Connecting…"
                        saved != null -> saved!!
                        else -> "No strip remembered yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (saved != null && !connected) FontFamily.Monospace
                    else FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = auto, onCheckedChange = { ble.setAutoEnabled(it) })
        }
        if (saved != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!connected) {
                    FilledTonalButton(onClick = { ble.connectSaved() }, shape = CircleShape) {
                        Text("Connect now")
                    }
                } else {
                    FilledTonalButton(onClick = { ble.disconnect() }, shape = CircleShape) {
                        Text("Disconnect")
                    }
                }
                FilledTonalButton(onClick = { ble.forgetDevice() }, shape = CircleShape) {
                    Text("Forget")
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(ble: GoveeBleManager) {
    val mode by ble.themeMode.collectAsState()
    GlowCard {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Text(
            "Android 12+ takes its palette from your wallpaper. Override it here.",
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

@Composable
private fun ShortcutsCard() {
    GlowCard {
        Text("Outside the app", style = MaterialTheme.typography.titleMedium)
        ShortcutRow(
            icon = Icons.Default.Widgets,
            title = "Home screen widgets",
            subtitle = "Power, brightness, and color without opening the app.",
        )
        ShortcutRow(
            icon = Icons.Default.GridView,
            title = "Quick Settings tile",
            subtitle = "Edit your tiles from the shade and add “Glow Strip”.",
        )
        ShortcutRow(
            icon = Icons.Default.Devices,
            title = "Device controls",
            subtitle = "The same place Google Home lives, under the power menu.",
        )
    }
}

@Composable
private fun ShortcutRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScannerCard(ble: GoveeBleManager, scanning: Boolean, found: List<ScannedLight>) {
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scanner", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (scanning) "Listening for BLE advertisements…" else "${found.size} devices found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (scanning) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
            }
        }
        Button(
            onClick = { if (scanning) ble.stopScan() else ble.startScan() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(
                if (scanning) Icons.Default.BluetoothDisabled else Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (scanning) "Stop scan" else "Scan for 15 seconds", fontWeight = FontWeight.Bold)
        }
        if (found.isEmpty()) {
            Text(
                if (scanning) "Keeping the strip powered and nearby speeds this up."
                else "Nothing listed yet. Start a scan with the strip powered on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        found.forEach { device ->
            DeviceRow(device) { ble.connect(device) }
        }
    }
}

@Composable
private fun DeviceRow(device: ScannedLight, onConnect: () -> Unit) {
    val govee = device.name.contains("Govee", ignoreCase = true)
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (govee) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(onClick = onConnect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleSmall)
            Text(
                device.address,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SignalBars(device.rssi)
        Spacer(Modifier.width(8.dp))
        Text(
            "${device.rssi}",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignalBars(rssi: Int) {
    val level = when {
        rssi >= -55 -> 4
        rssi >= -67 -> 3
        rssi >= -78 -> 2
        else -> 1
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(4) { index ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((5 + index * 4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (index < level) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

@Composable
private fun RawPacketCard(
    raw: String,
    onRawChange: (String) -> Unit,
    feedback: String?,
    onSend: () -> Unit,
) {
    val isRejection = feedback?.startsWith("Rejected") == true
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Raw packet", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Straight to the write characteristic, no validation beyond the checksum.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        OutlinedTextField(
            value = raw,
            onValueChange = onRawChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Hex bytes") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSend,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Send")
            }
            FilledTonalButton(
                onClick = { onRawChange("3301010000000000000000000000000000000033") },
                shape = RoundedCornerShape(16.dp),
            ) { Text("ON frame") }
            FilledTonalButton(
                onClick = { onRawChange("3301000000000000000000000000000000000032") },
                shape = RoundedCornerShape(16.dp),
            ) { Text("OFF frame") }
        }
        Text(
            feedback ?: "20 bytes required. A 19-byte frame gets its XOR checksum appended.",
            style = MaterialTheme.typography.bodySmall,
            color = when {
                feedback == null -> MaterialTheme.colorScheme.onSurfaceVariant
                isRejection -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            },
        )
    }
}

private val LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
private fun PacketLogCard(log: List<PacketLog>) {
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Write log", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    "${log.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        if (log.isEmpty()) {
            Text(
                "Nothing sent yet. Every command the app writes lands here, newest first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        log.take(30).forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Instant.ofEpochMilli(entry.timeMs)
                            .atZone(ZoneId.systemDefault())
                            .toLocalTime()
                            .format(LOG_TIME),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    entry.hex,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
