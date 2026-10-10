package com.example.goveecontroller.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** HSV state for the picker (hue 0..360, sat/value 0..1). */
data class Hsv(val h: Float, val s: Float, val v: Float) {
    fun toColor(): Color = Color.hsv(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    fun toRgb(): Triple<Int, Int, Int> {
        val c = toColor()
        return Triple((c.red * 255).roundToInt(), (c.green * 255).roundToInt(), (c.blue * 255).roundToInt())
    }
}

fun Color.toHsv(): Hsv {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
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

/**
 * Proper color picker: saturation/value square + hue ribbon + preview.
 * Calls onChange live during drag, onDone when the finger lifts.
 */
@Composable
fun HsvPicker(
    initial: Color,
    onChange: (Hsv) -> Unit,
    onDone: (Hsv) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hsv by remember(initial) { androidx.compose.runtime.mutableStateOf(initial.toHsv()) }

    fun update(n: Hsv) {
        hsv = n
        onChange(n)
    }

    Column(modifier) {
        // SV square
        val hueColor = Color.hsv(hsv.h, 1f, 1f)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.hsv(hsv.h, 1f, 1f))
                .pointerInput(hsv.h) {
                    detectDragGestures(
                        onDragEnd = { onDone(hsv) },
                    ) { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        val hgt = size.height.toFloat()
                        update(hsv.copy(
                            s = (change.position.x / w).coerceIn(0f, 1f),
                            v = 1f - (change.position.y / hgt).coerceIn(0f, 1f),
                        ))
                    }
                }
                .pointerInput(hsv.h) {
                    detectTapGestures(
                        onTap = {
                            val w = size.width.toFloat()
                            val hgt = size.height.toFloat()
                            update(hsv.copy(
                                s = (it.x / w).coerceIn(0f, 1f),
                                v = 1f - (it.y / hgt).coerceIn(0f, 1f),
                            ))
                            onDone(hsv.copy(
                                s = (it.x / w).coerceIn(0f, 1f),
                                v = 1f - (it.y / hgt).coerceIn(0f, 1f),
                            ))
                        },
                    )
                },
        ) {
            val w = size.width
            val hgt = size.height
            // white→hue horizontally, transparent→black vertically
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            // cursor
            val cx = hsv.s * w
            val cy = (1f - hsv.v) * hgt
            drawCircle(Color.White, radius = 22f, center = Offset(cx, cy), style = Stroke(width = 7f))
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 22f, center = Offset(cx, cy), style = Stroke(width = 2.5f))
        }
        Spacer(Modifier.height(12.dp))
        // Hue ribbon
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onDone(hsv) }) { change, _ ->
                        change.consume()
                        update(hsv.copy(h = (change.position.x / size.width) * 360f))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        val n = hsv.copy(h = (it.x / size.width) * 360f)
                        update(n)
                        onDone(n)
                    })
                },
        ) {
            val stops = (0..12).map { Color.hsv(it * 30f, 1f, 1f) }
            drawRect(Brush.horizontalGradient(stops))
            val cx = (hsv.h / 360f) * size.width
            drawCircle(Color.White, radius = 20f, center = Offset(cx, size.height / 2), style = Stroke(width = 7f))
        }
        Spacer(Modifier.height(12.dp))
        // Preview + hex
        val (r, g, b) = hsv.toRgb()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp)
                    .clip(CircleShape)
                    .background(hsv.toColor())
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "#%02X%02X%02X".format(r, g, b),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
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
        val c0 = stops[a]; val c1 = stops[a + 1]
        out.add(Triple(
            ((c0.red + (c1.red - c0.red) * f) * 255).roundToInt(),
            ((c0.green + (c1.green - c0.green) * f) * 255).roundToInt(),
            ((c0.blue + (c1.blue - c0.blue) * f) * 255).roundToInt(),
        ))
    }
    return out
}

/** Little gradient preview bar. */
@Composable
fun GradientBar(stops: List<Color>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(stops)),
    )
}
