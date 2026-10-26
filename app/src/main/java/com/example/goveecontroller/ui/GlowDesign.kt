package com.example.goveecontroller.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.goveecontroller.ble.MusicProfile
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/** Shared UI state so tab switches and sheet opens never reset the controls. */
@Stable
class GlowUiState {
    var power by mutableStateOf(true)
    var brightness by mutableFloatStateOf(80f)
    var color by mutableStateOf(Color(0xFF2D9CFF))

    var zones by mutableStateOf((1..15).toSet())
    var zoneColor by mutableStateOf(Color(0xFFFF5A1F))

    var blendStart by mutableStateOf(Color(0xFFFF3D00))
    var blendEnd by mutableStateOf(Color(0xFF7B1FA2))
    var flipBlend by mutableStateOf(false)
    var hardwareGradient by mutableStateOf(false)

    var musicProfile by mutableStateOf(MusicProfile.RHYTHM)
    var sensitivity by mutableFloatStateOf(70f)
    var paletteA by mutableStateOf(Color(0xFFFF2E88))
    var paletteB by mutableStateOf(Color(0xFF00E5FF))
    var musicRunning by mutableStateOf(false)

    fun toggleZone(zone: Int) {
        zones = if (zone in zones) zones - zone else zones + zone
    }
}

/** Named swatches shared by the control and zone screens. */
data class GlowPick(val name: String, val color: Color)

val GLOW_PICKS = listOf(
    GlowPick("Ice", Color(0xFF8BE9FD)),
    GlowPick("Blue", Color(0xFF2D9CFF)),
    GlowPick("Violet", Color(0xFF8B5CF6)),
    GlowPick("Rose", Color(0xFFFF2E88)),
    GlowPick("Sunset", Color(0xFFFF6B35)),
    GlowPick("Amber", Color(0xFFFFB300)),
    GlowPick("Lime", Color(0xFFA3E635)),
    GlowPick("White", Color.White),
)

object GlowMotion {
    val expressive = spring<Float>(dampingRatio = 0.62f, stiffness = 420f)
    val soft = spring<Float>(dampingRatio = 0.82f, stiffness = 280f)
}

@Composable
fun GlowList(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.8.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    contentPadding: PaddingValues = PaddingValues(20.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(22.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    val accent = scheme.onPrimaryContainer.copy(alpha = 0.055f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primaryContainer,
                        scheme.secondaryContainer,
                        scheme.tertiaryContainer.copy(alpha = 0.85f),
                    ),
                ),
            )
            .drawBehind {
                drawCircle(accent, size.width * 0.46f, Offset(size.width * 0.96f, -size.height * 0.1f))
                drawCircle(accent, size.width * 0.34f, Offset(-size.width * 0.05f, size.height * 0.95f))
            }
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
fun LiveDot(
    color: Color = MaterialTheme.colorScheme.primary,
    pulsing: Boolean = true,
    size: Dp = 8.dp,
) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "liveDotAlpha",
    )
    Box(Modifier.size(size * 2f), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier
                    .size(size * 2f)
                    .alpha(pulse * 0.3f)
                    .background(color, CircleShape),
            )
        }
        Box(
            Modifier
                .size(size)
                .alpha(if (pulsing) pulse else 1f)
                .background(color, CircleShape),
        )
    }
}

@Composable
fun ColorSwatch(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    size: Dp = 44.dp,
    contentDescription: String? = null,
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = readableOn(color),
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

/**
 * The 15-zone strip. Pass [selected] and [onZoneClick] to make it a selector;
 * leave both null for a decorative gradient preview.
 */
@Composable
fun ZoneStrip(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    selected: Set<Int>? = null,
    height: Dp = 52.dp,
    gap: Dp = 4.dp,
    onZoneClick: ((Int) -> Unit)? = null,
) {
    val interactive = selected != null && onZoneClick != null
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (interactive) Modifier.selectableGroup() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        colors.forEachIndexed { index, color ->
            val zone = index + 1
            val isSelected = selected == null || zone in selected
            val shown by animateColorAsState(
                targetValue = if (isSelected) color else color.copy(alpha = 0.18f),
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 650f),
                label = "zone$zone",
            )
            var segment = Modifier
                .weight(1f)
                .height(height)
                .clip(shape)
                .background(shown)
            if (selected != null) {
                segment = segment.border(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = shape,
                )
            }
            if (interactive) {
                segment = segment.selectable(
                    selected = isSelected,
                    role = Role.Checkbox,
                    onClick = { onZoneClick!!(zone) },
                )
            }
            Box(segment.semantics { contentDescription = "Zone $zone" })
        }
    }
}

@Composable
fun AnimatedEqualizer(
    active: Boolean,
    modifier: Modifier = Modifier,
    bars: Int = 11,
    colors: List<Color> = listOf(Color(0xFF5BD6FF), Color(0xFFFF6AC1), Color(0xFFB6FF6A)),
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val heights = (0 until bars).map { index ->
        val value by transition.animateFloat(
            initialValue = 0.18f + (index % 3) * 0.07f,
            targetValue = 0.55f + ((index * 7) % 4) * 0.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 520 + (index % 5) * 110,
                    easing = FastOutSlowInEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar$index",
        )
        value
    }
    Canvas(modifier) {
        val gap = size.width * 0.012f
        val barWidth = (size.width - gap * (bars - 1)) / bars
        val amplitude = if (active) 1f else 0.62f
        heights.forEachIndexed { index, level ->
            val barHeight = size.height * (0.1f + level * 0.9f) * amplitude
            drawRoundRect(
                color = colors[index % colors.size].copy(alpha = if (active) 0.95f else 0.55f),
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** Today's sun path with a marker for the current time. */
@Composable
fun SunArc(
    rise: LocalTime?,
    set: LocalTime?,
    modifier: Modifier = Modifier,
) {
    val now = LocalTime.now()
    val isDay = rise != null && set != null && !now.isBefore(rise) && !now.isAfter(set)
    val track = MaterialTheme.colorScheme.primary
    val horizon = MaterialTheme.colorScheme.outlineVariant
    val sun = if (isDay) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(124.dp)) {
            val sidePad = 10.dp.toPx()
            val cx = size.width / 2f
            val baseline = size.height - 4.dp.toPx()
            val rx = (size.width - sidePad * 2) / 2f
            val ry = size.height * 0.66f
            drawArc(
                color = track.copy(alpha = 0.16f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - rx, baseline - ry),
                size = Size(rx * 2, ry * 2),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )
            drawLine(
                color = horizon.copy(alpha = 0.45f),
                start = Offset(cx - rx, baseline),
                end = Offset(cx + rx, baseline),
                strokeWidth = 1.dp.toPx(),
            )
            val progress = sunProgress(rise, set, now)
            val angle = Math.toRadians(180.0 + 180.0 * progress)
            val point = Offset(
                cx + (rx * cos(angle)).toFloat(),
                baseline + (ry * sin(angle)).toFloat(),
            )
            drawCircle(sun.copy(alpha = 0.12f), 18.dp.toPx(), point)
            drawCircle(sun.copy(alpha = 0.22f), 12.dp.toPx(), point)
            drawCircle(sun, 6.dp.toPx(), point)
            drawCircle(Color.White.copy(alpha = 0.9f), 2.dp.toPx(), point)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.Top) {
            SunTime("Sunrise", rise?.format(CLOCK) ?: "--:--")
            Spacer(Modifier.weight(1f))
            SunTime("Sunset", set?.format(CLOCK) ?: "--:--", endAligned = true)
        }
    }
}

@Composable
private fun SunTime(label: String, time: String, endAligned: Boolean = false) {
    Column(horizontalAlignment = if (endAligned) Alignment.End else Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            time,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun sunProgress(rise: LocalTime?, set: LocalTime?, now: LocalTime): Float {
    if (rise == null || set == null || !set.isAfter(rise)) return 0.5f
    val total = Duration.between(rise, set).toMinutes().toFloat()
    if (total <= 0f) return 0.5f
    return (Duration.between(rise, now).toMinutes().toFloat() / total).coerceIn(0f, 1f)
}
