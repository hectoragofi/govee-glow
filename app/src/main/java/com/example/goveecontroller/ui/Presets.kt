package com.example.goveecontroller.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Your colors: custom swatches you save + automatic recents.
 * Backed by the shared "govee" prefs; version flow refreshes every row.
 */
object PresetStore {
    const val MAX_CUSTOM = 12
    const val MAX_RECENT = 8
    private const val KEY_CUSTOM = "custom_presets"
    private const val KEY_RECENT = "recent_colors"

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private fun prefs(c: Context) = c.getSharedPreferences("govee", Context.MODE_PRIVATE)

    private fun readList(c: Context, key: String): MutableList<String> {
        val out = mutableListOf<String>()
        try {
            val a = JSONArray(prefs(c).getString(key, "[]"))
            for (i in 0 until a.length()) {
                val s = a.optString(i, "")
                if (s.matches(Regex("#[0-9A-Fa-f]{6}"))) out.add(s.uppercase())
            }
        } catch (_: Exception) {}
        return out
    }

    private fun writeList(c: Context, key: String, list: List<String>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        prefs(c).edit().putString(key, a.toString()).apply()
        _version.value++
    }

    fun customs(c: Context): List<String> = readList(c, KEY_CUSTOM)

    /** False when full or already saved. */
    fun addCustom(c: Context, hex: String): Boolean {
        val h = hex.uppercase()
        val cur = readList(c, KEY_CUSTOM)
        if (h in cur || cur.size >= MAX_CUSTOM) return false
        writeList(c, KEY_CUSTOM, cur + h)
        return true
    }

    fun removeCustom(c: Context, hex: String) {
        writeList(c, KEY_CUSTOM, readList(c, KEY_CUSTOM) - hex.uppercase())
    }

    fun recents(c: Context): List<String> = readList(c, KEY_RECENT)

    fun record(c: Context, hex: String) {
        val h = hex.uppercase()
        val cur = (listOf(h) + readList(c, KEY_RECENT)).distinct().take(MAX_RECENT)
        writeList(c, KEY_RECENT, cur)
    }
}

fun hexOf(c: Color): String =
    "#%02X%02X%02X".format(
        (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(),
    )

fun colorOf(hex: String): Color {
    val v = hex.removePrefix("#").toLong(16)
    return Color(
        ((v shr 16) and 0xFF).toInt() / 255f,
        ((v shr 8) and 0xFF).toInt() / 255f,
        (v and 0xFF).toInt() / 255f,
    )
}

/** Your saved swatches + add button (long-press a swatch to delete). */
@Composable
fun CustomColorsRow(current: Color, onPick: (Color) -> Unit) {
    val ctx = LocalContext.current
    val v by PresetStore.version.collectAsState()
    @Suppress("UNUSED_EXPRESSION")
    v.let { }
    val customs = PresetStore.customs(ctx)
    SwatchRow(
        title = "My colors (${customs.size}/${PresetStore.MAX_CUSTOM})",
        colors = customs,
        onPick = onPick,
        onLongPressHint = "Long-press to delete",
        trailing = {
            if (customs.size < PresetStore.MAX_CUSTOM) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable {
                            PresetStore.addCustom(ctx, hexOf(current))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, "Save current color", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        onDelete = { PresetStore.removeCustom(ctx, it) },
    )
}

@Composable
fun RecentsRow(onPick: (Color) -> Unit) {
    val ctx = LocalContext.current
    val v by PresetStore.version.collectAsState()
    @Suppress("UNUSED_EXPRESSION")
    v.let { }
    val recents = PresetStore.recents(ctx)
    if (recents.isEmpty()) return
    SwatchRow(title = "Recent", colors = recents, onPick = onPick)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SwatchRow(
    title: String,
    colors: List<String>,
    onPick: (Color) -> Unit,
    onLongPressHint: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onDelete: ((String) -> Unit)? = null,
) {
    if (colors.isEmpty() && trailing == null) return
    Text(title, style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { hex ->
            val c = colorOf(hex)
            var mod = Modifier.size(44.dp).clip(CircleShape)
                .background(c)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            mod = if (onDelete != null) {
                mod.combinedClickable(
                    onClick = { onPick(c) },
                    onLongClick = { onDelete(hex) },
                )
            } else {
                mod.clickable { onPick(c) }
            }
            Box(mod)
        }
        trailing?.invoke()
    }
    if (onLongPressHint != null && colors.isNotEmpty()) {
        Text(
            onLongPressHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
