package com.example.goveecontroller.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goveecontroller.ble.GoveeBleManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlScreen(ble: GoveeBleManager, ui: GlowUiState, onOpenLab: () -> Unit) {
    val connected by ble.connected.collectAsState()
    val connecting by ble.connecting.collectAsState()
    val saved by ble.savedName.collectAsState()
    var pickerOpen by remember { mutableStateOf(false) }

    GlowList {
        item {
            ScreenHeader(
                eyebrow = "Control",
                title = "Your light, right now.",
                subtitle = "Power, brightness, and color. Changes land on the strip as you go.",
            )
        }
        if (!connected) {
            item {
                ConnectionBanner(
                    connecting = connecting,
                    saved = saved,
                    onOpenLab = onOpenLab,
                )
            }
        }
        item { PowerHero(ble, ui) }
        item {
            ColorStudio(ble, ui, onOpenPicker = { pickerOpen = true })
        }
    }

    if (pickerOpen) {
        val ctx = LocalContext.current
        ColorPickerSheet(
            title = "Color studio",
            initial = ui.color,
            onPreview = { ui.color = it },
            onConfirm = {
                ui.color = it
                sendSolid(ble, ctx, it)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

@Composable
private fun PowerHero(ble: GoveeBleManager, ui: GlowUiState) {
    val haptic = LocalHapticFeedback.current
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    val shownBrightness by animateIntAsState(
        targetValue = ui.brightness.toInt(),
        animationSpec = tween(220),
        label = "brightness",
    )
    HeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "POWER",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    color = ink.copy(alpha = 0.7f),
                )
                AnimatedContent(targetState = ui.power, label = "power") { on ->
                    Text(
                        if (on) "On" else "Off",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ink,
                    )
                }
            }
            Switch(
                checked = ui.power,
                onCheckedChange = {
                    ui.power = it
                    ble.power(it)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                thumbContent = {
                    Icon(
                        Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ink,
                    checkedTrackColor = ink.copy(alpha = 0.32f),
                    checkedBorderColor = ink.copy(alpha = 0.25f),
                    uncheckedThumbColor = ink.copy(alpha = 0.8f),
                    uncheckedTrackColor = ink.copy(alpha = 0.12f),
                    uncheckedBorderColor = ink.copy(alpha = 0.35f),
                ),
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "$shownBrightness",
                style = MaterialTheme.typography.displayLarge,
                color = ink,
            )
            Text(
                "%",
                style = MaterialTheme.typography.headlineSmall,
                color = ink.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = 10.dp, start = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Brightness",
                style = MaterialTheme.typography.labelLarge,
                color = ink.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Slider(
            value = ui.brightness,
            onValueChange = { ui.brightness = it },
            onValueChangeFinished = {
                ble.brightness(ui.brightness.toInt())
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            },
            valueRange = 1f..100f,
            colors = SliderDefaults.colors(
                thumbColor = ink,
                activeTrackColor = ink,
                inactiveTrackColor = ink.copy(alpha = 0.22f),
            ),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 50, 75, 100).forEach { percent ->
                PercentPill(
                    text = "$percent%",
                    selected = shownBrightness == percent,
                    onClick = {
                        ui.brightness = percent.toFloat()
                        ble.brightness(percent)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )
            }
        }

        Text(
            "Your strip",
            style = MaterialTheme.typography.labelMedium,
            color = ink.copy(alpha = 0.7f),
        )
        ZoneStrip(
            colors = List(15) { index ->
                ui.color.copy(alpha = 0.35f + (0.65f * index / 14f))
            },
            height = 44.dp,
        )
    }
}

@Composable
private fun PercentPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        shape = CircleShape,
        color = if (selected) ink.copy(alpha = 0.18f) else ink.copy(alpha = 0.07f),
        contentColor = ink,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorStudio(ble: GoveeBleManager, ui: GlowUiState, onOpenPicker: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val textOnColor = readableOn(ui.color)

    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Color studio", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Solid color · ${hexOf(ui.color)}",
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
                .height(112.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            ui.color,
                            ui.color.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ),
                )
                .clickable(onClick = onOpenPicker),
        ) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    hexOf(ui.color),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    color = textOnColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Tap to fine-tune",
                    style = MaterialTheme.typography.labelSmall,
                    color = textOnColor.copy(alpha = 0.75f),
                )
            }
        }

        Text("Quick colors", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(GLOW_PICKS, key = { it.name }) { pick ->
                ColorSwatch(
                    color = pick.color,
                    onClick = {
                        ui.color = pick.color
                        sendSolid(ble, ctx, pick.color)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    selected = pick.color == ui.color,
                    size = 46.dp,
                    contentDescription = pick.name,
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WhiteButton("Warm white") {
                val color = kelvinToHsv(2700).toColor()
                ui.color = color
                sendSolid(ble, ctx, color)
            }
            WhiteButton("Daylight") {
                val color = kelvinToHsv(5600).toColor()
                ui.color = color
                sendSolid(ble, ctx, color)
            }
        }

        CustomColorsRow(ui.color) { color ->
            ui.color = color
            sendSolid(ble, ctx, color)
        }
        RecentsRow { color ->
            ui.color = color
            sendSolid(ble, ctx, color)
        }
    }
}

@Composable
private fun WhiteButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Default.Thermostat, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun ConnectionBanner(connecting: Boolean, saved: String?, onOpenLab: () -> Unit) {
    GlowCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (connecting) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(
                        if (saved != null) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        connecting -> "Linking to your strip"
                        saved != null -> "Auto-link ready"
                        else -> "Strip not connected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    when {
                        connecting -> "Handshake in progress."
                        saved != null -> "Govee Glow will reconnect on open."
                        else -> "Open Lab and scan for Govee_H6125."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onOpenLab, shape = CircleShape) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Lab")
            }
        }
    }
}

fun sendSolid(ble: GoveeBleManager, ctx: android.content.Context, color: Color) {
    ble.color(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
    )
    PresetStore.record(ctx, hexOf(color))
}
