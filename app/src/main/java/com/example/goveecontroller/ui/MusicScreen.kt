package com.example.goveecontroller.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goveecontroller.ble.GoveeBleManager
import com.example.goveecontroller.ble.GoveeScene
import com.example.goveecontroller.ble.MusicProfile
import kotlinx.coroutines.delay

private enum class PaletteStop { A, B }

private data class PaletteDuo(val name: String, val a: Color, val b: Color)

private val PALETTE_DUOS = listOf(
    PaletteDuo("Neon", Color(0xFFFF2E88), Color(0xFF00E5FF)),
    PaletteDuo("Ember", Color(0xFFFF3D00), Color(0xFFFFD600)),
    PaletteDuo("Ocean", Color(0xFF0026FF), Color(0xFF00FFC8)),
    PaletteDuo("Bloom", Color(0xFF8B5CF6), Color(0xFFFF8AD8)),
)

@Composable
fun MusicScreen(ble: GoveeBleManager, ui: GlowUiState) {
    val haptic = LocalHapticFeedback.current
    var pickerTarget by remember { mutableStateOf<PaletteStop?>(null) }
    var appliedScene by remember { mutableStateOf<String?>(null) }

    fun start() {
        ble.startMusic(
            ui.musicProfile.id,
            listOf(ui.paletteA.toRgbTriple(), ui.paletteB.toRgbTriple()),
            ui.sensitivity.toInt(),
        )
        ui.musicRunning = true
        appliedScene = null
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun stop() {
        ble.stopMusic(255, 224, 178)
        ui.musicRunning = false
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(appliedScene) {
        if (appliedScene != null) {
            delay(2600)
            appliedScene = null
        }
    }

    GlowList {
        item {
            ScreenHeader(
                eyebrow = "Music",
                title = "Let the room drive.",
                subtitle = "The strip's own mic hears the beat. No phone audio capture, no permissions.",
            )
        }
        item { MusicHero(ui, onStart = ::start, onStop = ::stop) }
        item { ProfileCard(ui) }
        item { SensitivityCard(ui) }
        item {
            PaletteCard(
                ui = ui,
                onPickA = { pickerTarget = PaletteStop.A },
                onPickB = { pickerTarget = PaletteStop.B },
            )
        }
        item { ScenesCard(ble, ui, appliedScene) { appliedScene = it } }
    }

    when (pickerTarget) {
        PaletteStop.A -> ColorPickerSheet(
            title = "Palette A",
            initial = ui.paletteA,
            onPreview = { ui.paletteA = it },
            onConfirm = { ui.paletteA = it },
            onDismiss = { pickerTarget = null },
        )
        PaletteStop.B -> ColorPickerSheet(
            title = "Palette B",
            initial = ui.paletteB,
            onPreview = { ui.paletteB = it },
            onConfirm = { ui.paletteB = it },
            onDismiss = { pickerTarget = null },
        )
        null -> {}
    }
}

@Composable
private fun MusicHero(ui: GlowUiState, onStart: () -> Unit, onStop: () -> Unit) {
    val night = Color(0xFF0A0D12)
    val nightLift = Color(0xFF17202C)
    val neon = Color(0xFF5BD6FF)
    val accent = Color(0xFFB6FF6A)
    val shape = MaterialTheme.shapes.extraLarge

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(nightLift, night)))
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(color = if (ui.musicRunning) accent else neon, pulsing = ui.musicRunning)
            Spacer(Modifier.width(8.dp))
            Text(
                "MIC SYNC",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.8.sp,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.weight(1f),
            )
            Text(
                "0x%02X".format(ui.musicProfile.id),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = neon,
            )
        }

        AnimatedEqualizer(
            active = ui.musicRunning,
            modifier = Modifier.fillMaxWidth().height(104.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (ui.musicRunning) "Listening to the room" else "Ready when you are",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                "${ui.musicProfile.label} · sensitivity ${ui.sensitivity.toInt()} · palette uploaded on start",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onStart,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = night),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (ui.musicRunning) "Restart" else "Start sync", fontWeight = FontWeight.Bold)
            }
            FilledTonalButton(
                onClick = onStop,
                enabled = ui.musicRunning,
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White,
                    disabledContainerColor = Color.White.copy(alpha = 0.05f),
                    disabledContentColor = Color.White.copy(alpha = 0.35f),
                ),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop")
            }
        }

        Text(
            "The mic sits on the strip. Turn the volume up in the room, not on your phone.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun ProfileCard(ui: GlowUiState) {
    GlowCard {
        Text("Profile", style = MaterialTheme.typography.titleLarge)
        Text(
            "How hard the strip reacts. All three profiles were measured on the H6125.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MusicProfile.entries.forEach { profile ->
                ProfileTile(
                    profile = profile,
                    selected = ui.musicProfile == profile,
                    modifier = Modifier.weight(1f),
                    onClick = { ui.musicProfile = profile },
                )
            }
        }
    }
}

@Composable
private fun ProfileTile(
    profile: MusicProfile,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface
    val icon: ImageVector = when (profile) {
        MusicProfile.RHYTHM -> Icons.Default.GraphicEq
        MusicProfile.PULSE -> Icons.Default.Bolt
        MusicProfile.FLOW -> Icons.Default.Waves
    }
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
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(profile.label, style = MaterialTheme.typography.titleSmall, color = content)
        Text(
            profile.note.substringBefore(" ·"),
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun SensitivityCard(ui: GlowUiState) {
    var slider by remember { mutableFloatStateOf(ui.sensitivity) }
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sensitivity", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Higher means quieter sounds move the strip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${slider.toInt()}",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = slider,
            onValueChange = {
                slider = it
                ui.sensitivity = it
            },
            valueRange = 0f..99f,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25 to "Calm", 60 to "Balanced", 90 to "Reactive").forEach { (value, label) ->
                FilledTonalButton(
                    onClick = {
                        slider = value.toFloat()
                        ui.sensitivity = value.toFloat()
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun PaletteCard(ui: GlowUiState, onPickA: () -> Unit, onPickB: () -> Unit) {
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Palette", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                "2 stops",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PaletteStopCard(
                label = "A",
                color = ui.paletteA,
                modifier = Modifier.weight(1f),
                onClick = onPickA,
            )
            PaletteStopCard(
                label = "B",
                color = ui.paletteB,
                modifier = Modifier.weight(1f),
                onClick = onPickB,
            )
        }
        GradientBar(listOf(ui.paletteA, ui.paletteB), height = 36.dp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PALETTE_DUOS.forEach { duo ->
                DuoChip(
                    duo = duo,
                    selected = ui.paletteA == duo.a && ui.paletteB == duo.b,
                    onClick = {
                        ui.paletteA = duo.a
                        ui.paletteB = duo.b
                    },
                )
            }
        }
    }
}

@Composable
private fun PaletteStopCard(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    val ink = readableOn(color)
    Box(
        modifier
            .height(88.dp)
            .clip(shape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), shape)
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(
                "STOP $label",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
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
private fun DuoChip(duo: PaletteDuo, selected: Boolean, onClick: () -> Unit) {
    val shape = CircleShape
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(duo.a))
        Box(Modifier.size(20.dp).clip(CircleShape).background(duo.b))
        Text(duo.name, style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenesCard(
    ble: GoveeBleManager,
    ui: GlowUiState,
    appliedScene: String?,
    onApplied: (String) -> Unit,
) {
    GlowCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Scenes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                "${GoveeScene.entries.size} presets",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Store-side animations. Picking one leaves music mode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GoveeScene.entries.forEach { scene ->
                FilledTonalButton(
                    onClick = {
                        ble.scene(scene.id, scene.label)
                        ui.musicRunning = false
                        onApplied(scene.label)
                    },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(scene.label) }
            }
        }
        if (appliedScene != null) {
            Text(
                "Sent $appliedScene",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
