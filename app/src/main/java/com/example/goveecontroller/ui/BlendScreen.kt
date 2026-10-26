package com.example.goveecontroller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.goveecontroller.ble.GoveeBleManager
import kotlinx.coroutines.delay

private enum class BlendStop { Start, End }

@Composable
fun BlendScreen(ble: GoveeBleManager, ui: GlowUiState) {
    var pickerTarget by remember { mutableStateOf<BlendStop?>(null) }
    var applied by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    fun applyBlend(name: String, stops: List<Color>) {
        ble.applyGradient(interpolateGradient(stops), ui.flipBlend)
        applied = name
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(applied) {
        if (applied != null) {
            delay(3200)
            applied = null
        }
    }

    GlowList {
        item {
            ScreenHeader(
                eyebrow = "Blend",
                title = "Fifteen zones, one flow.",
                subtitle = "Pick a preset wash or build a fade between two colors.",
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GRADIENT_PRESETS.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { preset ->
                            GradientTile(
                                preset = preset,
                                modifier = Modifier.weight(1f),
                                onApply = { applyBlend(preset.name, preset.stops) },
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        applied?.let { name ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Sent $name to the strip",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        item { FlipCard(ui) }
        item { CustomBlendCard(ui, onPickStart = { pickerTarget = BlendStop.Start }, onPickEnd = { pickerTarget = BlendStop.End }, onApply = { applyBlend("Custom blend", listOf(ui.blendStart, ui.blendEnd)) }) }
        item { HardwareGradientCard(ble, ui) }
    }

    when (pickerTarget) {
        BlendStop.Start -> ColorPickerSheet(
            title = "Blend start",
            initial = ui.blendStart,
            onPreview = { ui.blendStart = it },
            onConfirm = { ui.blendStart = it },
            onDismiss = { pickerTarget = null },
        )
        BlendStop.End -> ColorPickerSheet(
            title = "Blend end",
            initial = ui.blendEnd,
            onPreview = { ui.blendEnd = it },
            onConfirm = { ui.blendEnd = it },
            onDismiss = { pickerTarget = null },
        )
        null -> {}
    }
}

@Composable
private fun GradientTile(
    preset: GradientPreset,
    modifier: Modifier = Modifier,
    onApply: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val ink = readableOn(preset.stops[preset.stops.size / 2])
    val badgeInk = readableOn(preset.stops.last())
    Box(
        modifier
            .height(112.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(preset.stops))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), shape)
            .clickable(onClick = onApply),
    ) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(badgeInk.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = badgeInk,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                "${preset.stops.size} stops · 15 zones",
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun FlipCard(ui: GlowUiState) {
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Flip direction", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Segment 1 sits at the far end on some installs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = ui.flipBlend, onCheckedChange = { ui.flipBlend = it })
        }
    }
}

@Composable
private fun CustomBlendCard(
    ui: GlowUiState,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onApply: () -> Unit,
) {
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Custom blend",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = {
                    val start = ui.blendStart
                    ui.blendStart = ui.blendEnd
                    ui.blendEnd = start
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Default.SwapHoriz, "Swap colors")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BlendStopCard(
                label = "Start",
                color = ui.blendStart,
                modifier = Modifier.weight(1f),
                onClick = onPickStart,
            )
            BlendStopCard(
                label = "End",
                color = ui.blendEnd,
                modifier = Modifier.weight(1f),
                onClick = onPickEnd,
            )
        }
        GradientBar(listOf(ui.blendStart, ui.blendEnd), height = 56.dp)
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(Icons.Default.Gradient, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Apply blend", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BlendStopCard(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val ink = readableOn(color)
    Box(
        modifier
            .height(104.dp)
            .clip(shape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier.align(Alignment.BottomStart).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.65f),
            )
            Text(
                hexOf(color),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = ink,
            )
        }
    }
}

@Composable
private fun HardwareGradientCard(ble: GoveeBleManager, ui: GlowUiState) {
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hardware gradient", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Strip-side fade · 33 A3. Leaves per-zone painting behind.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = ui.hardwareGradient,
                onCheckedChange = {
                    ui.hardwareGradient = it
                    ble.gradient(it)
                },
            )
        }
    }
}
