package com.example.goveecontroller.ui

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** HSV state for the picker (hue 0..360, sat/value 0..1). */
data class Hsv(val h: Float, val s: Float, val v: Float) {
    fun toColor(): Color = Color.hsv(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))

    fun toRgb(): Triple<Int, Int, Int> {
        val c = toColor()
        return Triple(
            (c.red * 255).roundToInt(),
            (c.green * 255).roundToInt(),
            (c.blue * 255).roundToInt(),
        )
    }
}

fun Color.toHsv(): Hsv {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6)
        max == g -> 60f * (((b - r) / d) + 2)
        else -> 60f * (((r - g) / d) + 4)
    }
    val s = if (max == 0f) 0f else d / max
    return Hsv((h + 360f) % 360f, s, max)
}

fun Color.toRgbTriple(): Triple<Int, Int, Int> = Triple(
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
)

/**
 * Saturation/value field + hue ribbon + optional white-temperature slider.
 * Calls [onChange] live and [onDone] whenever the finger lifts.
 */
@Composable
fun HsvPicker(
    initial: Color,
    onChange: (Hsv) -> Unit,
    onDone: (Hsv) -> Unit,
    modifier: Modifier = Modifier,
    showTemperature: Boolean = true,
) {
    var hsv by remember { mutableStateOf(initial.toHsv()) }
    val currentOnChange by rememberUpdatedState(onChange)
    val currentOnDone by rememberUpdatedState(onDone)

    fun update(next: Hsv) {
        hsv = next
        currentOnChange(next)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val hueColor = Color.hsv(hsv.h, 1f, 1f)
        val fieldShape = RoundedCornerShape(24.dp)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(212.dp)
                .clip(fieldShape)
                .background(hueColor)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), fieldShape)
                .semantics { contentDescription = "Saturation and brightness" }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { currentOnDone(hsv) }) { change, _ ->
                        change.consume()
                        update(
                            hsv.copy(
                                s = (change.position.x / size.width).coerceIn(0f, 1f),
                                v = 1f - (change.position.y / size.height).coerceIn(0f, 1f),
                            ),
                        )
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { point ->
                        val next = hsv.copy(
                            s = (point.x / size.width).coerceIn(0f, 1f),
                            v = 1f - (point.y / size.height).coerceIn(0f, 1f),
                        )
                        update(next)
                        currentOnDone(next)
                    }
                },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val cx = hsv.s * size.width
            val cy = (1f - hsv.v) * size.height
            drawCircle(Color.Black.copy(alpha = 0.3f), 14.dp.toPx(), Offset(cx, cy), style = Stroke(6.dp.toPx()))
            drawCircle(Color.White, 12.dp.toPx(), Offset(cx, cy), style = Stroke(4.dp.toPx()))
        }

        val ribbonShape = RoundedCornerShape(20.dp)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(ribbonShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), ribbonShape)
                .semantics { contentDescription = "Hue" }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { currentOnDone(hsv) }) { change, _ ->
                        change.consume()
                        update(hsv.copy(h = ((change.position.x / size.width) * 360f).coerceIn(0f, 359.9f)))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { point ->
                        val next = hsv.copy(h = ((point.x / size.width) * 360f).coerceIn(0f, 359.9f))
                        update(next)
                        currentOnDone(next)
                    }
                },
        ) {
            val stops = (0..12).map { Color.hsv(it * 30f, 1f, 1f) }
            drawRect(Brush.horizontalGradient(stops))
            val thumbHalf = 6.dp.toPx()
            val cx = ((hsv.h / 360f) * size.width).coerceIn(thumbHalf, size.width - thumbHalf)
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.25f),
                topLeft = Offset(cx - thumbHalf - 1.dp.toPx(), 3.dp.toPx()),
                size = Size(thumbHalf * 2 + 2.dp.toPx(), size.height - 6.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - thumbHalf, 4.dp.toPx()),
                size = Size(thumbHalf * 2, size.height - 8.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
        }

        if (showTemperature) {
            var tempK by remember { mutableFloatStateOf(4000f) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "White",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(52.dp),
                )
                Slider(
                    value = tempK,
                    onValueChange = {
                        tempK = it
                        update(kelvinToHsv(it.toInt()))
                    },
                    onValueChangeFinished = { onDone(hsv) },
                    valueRange = 2700f..6500f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${tempK.toInt()}K",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val (r, g, b) = hsv.toRgb()
        val hex = "#%02X%02X%02X".format(r, g, b)
        val clipboard = LocalClipboard.current
        val clipboardScope = rememberCoroutineScope()
        val toastContext = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(hsv.toColor())
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(hex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap the field or drag to tune",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {
                clipboardScope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Hex color", hex)))
                }
                Toast.makeText(toastContext, "Copied $hex", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, "Copy hex")
            }
        }
    }
}

private val WARM_WHITE = kelvinToHsv(2700).toColor()
private val DAYLIGHT = kelvinToHsv(5600).toColor()

/**
 * A bottom sheet that wraps the picker. [onPreview] runs live so the strip can
 * follow the finger, while [onConfirm] fires on the explicit use button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    title: String,
    initial: Color,
    onPreview: (Color) -> Unit,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var color by remember { mutableStateOf(initial) }
    var pickerSeed by remember { mutableStateOf(initial) }
    var pickerKey by remember { mutableIntStateOf(0) }
    val picks = remember {
        listOf(
            GlowPick("Warm white", WARM_WHITE),
            GlowPick("Daylight", DAYLIGHT),
        ) + GLOW_PICKS
    }

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
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close")
                }
            }
            key(pickerKey) {
                HsvPicker(
                    initial = pickerSeed,
                    onChange = {
                        color = it.toColor()
                        onPreview(color)
                    },
                    onDone = {
                        color = it.toColor()
                        onPreview(color)
                    },                )
            }
            Text("Quick picks", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(picks, key = { it.name }) { pick ->
                    ColorSwatch(
                        color = pick.color,
                        onClick = {
                            pickerSeed = pick.color
                            pickerKey++
                            color = pick.color
                            onPreview(color)
                        },
                        selected = pick.color == color,
                        size = 46.dp,
                        contentDescription = pick.name,
                    )
                }
            }
            Button(
                onClick = {
                    onConfirm(color)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Use this color", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Approximate white-point for a color temperature (Tanner Helland). */
fun kelvinToHsv(kelvin: Int): Hsv {
    val t = (kelvin / 100.0).coerceIn(10.0, 150.0)
    val r = if (t <= 66) 255.0 else (329.698727446 * Math.pow(t - 60, -0.1332047592)).coerceIn(0.0, 255.0)
    val g = if (t <= 66) {
        (99.4708025861 * Math.log(t) - 161.1195681661).coerceIn(0.0, 255.0)
    } else {
        (288.1221695283 * Math.pow(t - 60, -0.0755148492)).coerceIn(0.0, 255.0)
    }
    val b = when {
        t >= 66 -> 255.0
        t <= 19 -> 0.0
        else -> (138.5177312231 * Math.log(t - 10) - 305.0447927307).coerceIn(0.0, 255.0)
    }
    return Color((r / 255).toFloat(), (g / 255).toFloat(), (b / 255).toFloat()).toHsv()
}

/** Gradient presets: 2–3 stops each, interpolated to 15 segments on apply. */
data class GradientPreset(val name: String, val stops: List<Color>)

val GRADIENT_PRESETS = listOf(
    GradientPreset("Sunset", listOf(Color(0xFFFF3D00), Color(0xFFFF9100), Color(0xFFFFD600))),
    GradientPreset("Ocean", listOf(Color(0xFF0000FF), Color(0xFF00E5FF), Color(0xFF00FFC8))),
    GradientPreset("Forest", listOf(Color(0xFF1B5E20), Color(0xFF66BB6A), Color(0xFFDCE775))),
    GradientPreset("Candy", listOf(Color(0xFFFF2E88), Color(0xFFB388FF), Color(0xFF40C4FF))),
    GradientPreset("Fire", listOf(Color(0xFFFF0000), Color(0xFFFF6D00), Color(0xFFFFEA00))),
    GradientPreset("Rainbow", listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta,
    )),
)

/** Interpolate stops across [segments] zones → RGB triples. */
fun interpolateGradient(stops: List<Color>, segments: Int = 15): List<Triple<Int, Int, Int>> {
    require(stops.size >= 2)
    val out = mutableListOf<Triple<Int, Int, Int>>()
    for (i in 0 until segments) {
        val t = i.toFloat() / (segments - 1)
        val scaled = t * (stops.size - 1)
        val a = scaled.toInt().coerceAtMost(stops.size - 2)
        val f = scaled - a
        val c0 = stops[a]
        val c1 = stops[a + 1]
        out.add(
            Triple(
                ((c0.red + (c1.red - c0.red) * f) * 255).roundToInt(),
                ((c0.green + (c1.green - c0.green) * f) * 255).roundToInt(),
                ((c0.blue + (c1.blue - c0.blue) * f) * 255).roundToInt(),
            ),
        )
    }
    return out
}

/** Little gradient preview bar. */
@Composable
fun GradientBar(
    stops: List<Color>,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Brush.horizontalGradient(stops))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), shape),
    )
}
