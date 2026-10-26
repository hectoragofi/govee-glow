package com.example.goveecontroller.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goveecontroller.ble.GoveeBleManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZonesScreen(ble: GoveeBleManager, ui: GlowUiState) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var pickerOpen by remember { mutableStateOf(false) }
    var paintedLabel by remember { mutableStateOf<String?>(null) }

    fun paint(color: Color) {
        ui.zoneColor = color
        PresetStore.record(ctx, hexOf(color))
        paintZones(ble, ui.zones, color)
        paintedLabel = "Painted ${ui.zones.size} zones"
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(paintedLabel) {
        if (paintedLabel != null) {
            delay(2600)
            paintedLabel = null
        }
    }

    GlowList {
        item {
            ScreenHeader(
                eyebrow = "Zones",
                title = "Paint any part.",
                subtitle = "Fifteen addressable segments. Select the ones you want, then send a color.",
            )
        }
        item { StripCard(ui) }
        item {
            PaintCard(
                ui = ui,
                paintedLabel = paintedLabel,
                onOpenPicker = { pickerOpen = true },
                onPaint = { paint(it) },
            )
        }
    }

    if (pickerOpen) {
        ColorPickerSheet(
            title = "Zone color",
            initial = ui.zoneColor,
            onPreview = { ui.zoneColor = it },
            onConfirm = {
                ui.zoneColor = it
                paint(it)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun StripCard(ui: GlowUiState) {
    val haptic = LocalHapticFeedback.current
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Strip map", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${ui.zones.size} of 15 selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                hexOf(ui.zoneColor),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ZoneStrip(
            colors = List(15) { ui.zoneColor },
            selected = ui.zones,
            height = 60.dp,
            onZoneClick = {
                ui.toggleZone(it)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
        )
        Text(
            "Tap a segment to include or exclude it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectionButton("All") { ui.zones = (1..15).toSet() }
            SelectionButton("Left") { ui.zones = (1..8).toSet() }
            SelectionButton("Right") { ui.zones = (9..15).toSet() }
            SelectionButton("Invert") { ui.zones = (1..15).toSet() - ui.zones }
            SelectionButton("None") { ui.zones = emptySet() }
        }
    }
}

@Composable
private fun SelectionButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label) }
}

@Composable
private fun PaintCard(
    ui: GlowUiState,
    paintedLabel: String?,
    onOpenPicker: () -> Unit,
    onPaint: (Color) -> Unit,
) {
    val ink = readableOn(ui.zoneColor)
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Zone color", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Recorded to your recents on every paint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onOpenPicker,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tune")
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(ui.zoneColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                hexOf(ui.zoneColor),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                color = ink,
            )
        }

        Text("Quick colors", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(GLOW_PICKS, key = { it.name }) { pick ->
                ColorSwatch(
                    color = pick.color,
                    onClick = { onPaint(pick.color) },
                    selected = pick.color == ui.zoneColor,
                    size = 46.dp,
                    contentDescription = pick.name,
                )
            }
        }

        CustomColorsRow(ui.zoneColor) { onPaint(it) }
        RecentsRow { onPaint(it) }

        Button(
            onClick = { onPaint(ui.zoneColor) },
            enabled = ui.zones.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (ui.zones.isEmpty()) "Select at least one zone"
                else "Paint ${ui.zones.size} zones",
                fontWeight = FontWeight.Bold,
            )
        }

        if (paintedLabel != null) {
            Text(
                paintedLabel,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun paintZones(ble: GoveeBleManager, zones: Set<Int>, color: Color) {
    val (r, g, b) = color.toRgbTriple()
    var left = 0
    var right = 0
    zones.forEach { zone ->
        if (zone <= 8) left = left or (1 shl (zone - 1)) else right = right or (1 shl (zone - 9))
    }
    ble.segment(r, g, b, left, right)
}
