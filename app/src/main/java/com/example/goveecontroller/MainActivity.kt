package com.example.goveecontroller

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.goveecontroller.ble.GoveeBleManager
import com.example.goveecontroller.ui.AutoScreen
import com.example.goveecontroller.ui.BlendScreen
import com.example.goveecontroller.ui.ControlScreen
import com.example.goveecontroller.ui.GlowUiState
import com.example.goveecontroller.ui.GoveeGlowTheme
import com.example.goveecontroller.ui.LabScreen
import com.example.goveecontroller.ui.LiveDot
import com.example.goveecontroller.ui.MusicScreen
import com.example.goveecontroller.ui.ZonesScreen
import kotlinx.coroutines.launch

private const val LAB_INDEX = 5

private data class GlowTab(val label: String, val icon: ImageVector)

private val DESTINATIONS = listOf(
    GlowTab("Control", Icons.Default.Tune),
    GlowTab("Blend", Icons.Default.Gradient),
    GlowTab("Zones", Icons.Default.Splitscreen),
    GlowTab("Music", Icons.Default.MusicNote),
    GlowTab("Auto", Icons.Default.Alarm),
)

class MainActivity : ComponentActivity() {

    private lateinit var ble: GoveeBleManager

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Permissions just granted (or already had them) — connect right away.
        ble.autoConnect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ble = GoveeBleManager(applicationContext)
        askBlePermissions()
        // Safety net: replant automation alarms on every cold start.
        com.example.goveecontroller.widget.AutomationRunner.scheduleAll(this)

        // Reconnect every time the app comes to the foreground.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ble.autoConnect()
            }
        }

        setContent {
            val themeMode by ble.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                GoveeBleManager.THEME_LIGHT -> false
                GoveeBleManager.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            GoveeGlowTheme(darkTheme = darkTheme) {
                val view = LocalView.current
                SideEffect {
                    (view.context as? Activity)?.window?.let { window ->
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = !darkTheme
                            isAppearanceLightNavigationBars = !darkTheme
                        }
                    }
                }

                val ui = remember { GlowUiState() }
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var labOpen by rememberSaveable { mutableStateOf(false) }

                BackHandler(enabled = labOpen) { labOpen = false }
                BackHandler(enabled = !labOpen && tab != 0) { tab = 0 }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        GlowTopBar(
                            ble = ble,
                            labOpen = labOpen,
                            onLab = { labOpen = true },
                            onBack = { labOpen = false },
                        )
                    },
                    bottomBar = {
                        if (!labOpen) {
                            GlowNavBar(selected = tab, onSelect = { tab = it })
                        }
                    },
                ) { inner ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(inner)
                            .background(
                                Brush.verticalGradient(
                                    0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                    520f to Color.Transparent,
                                ),
                            ),
                    ) {
                        AnimatedContent(
                            targetState = if (labOpen) LAB_INDEX else tab,
                            transitionSpec = {
                                val forward = targetState > initialState
                                (
                                    slideInHorizontally(
                                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                                    ) { width -> if (forward) width / 8 else -width / 8 } +
                                        fadeIn(tween(190))
                                    ) togetherWith (
                                    slideOutHorizontally(
                                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                                    ) { width -> if (forward) -width / 10 else width / 10 } +
                                        fadeOut(tween(140))
                                    )
                            },
                            label = "tab",
                        ) { index ->
                            when (index) {
                                0 -> ControlScreen(ble, ui, onOpenLab = { labOpen = true })
                                1 -> BlendScreen(ble, ui)
                                2 -> ZonesScreen(ble, ui)
                                3 -> MusicScreen(ble, ui)
                                4 -> AutoScreen()
                                else -> LabScreen(ble)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun askBlePermissions() {
        val want = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (want.isEmpty()) {
            ble.autoConnect()
        } else {
            permLauncher.launch(want.toTypedArray())
        }
    }

    override fun onDestroy() {
        ble.disconnect()
        super.onDestroy()
    }
}

@Composable
private fun GlowTopBar(
    ble: GoveeBleManager,
    labOpen: Boolean,
    onLab: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (labOpen) {
                FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lab", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Tools and diagnostics",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                GlowMark()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Govee Glow",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "H6125 · 15 zones",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ConnectionPill(ble)
            if (!labOpen) {
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = onLab, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.Science, "Lab")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
}

@Composable
private fun GlowMark() {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(primary, tertiary)))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.16f), size.width * 0.45f, androidx.compose.ui.geometry.Offset(size.width * 0.85f, 0f))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ConnectionPill(ble: GoveeBleManager) {
    val connected by ble.connected.collectAsState()
    val connecting by ble.connecting.collectAsState()
    val saved by ble.savedName.collectAsState()
    val label = when {
        connected -> "Live"
        connecting -> "Linking"
        saved != null -> "Auto"
        else -> "Off"
    }
    val container = when {
        connected -> MaterialTheme.colorScheme.primaryContainer
        connecting -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        connected -> MaterialTheme.colorScheme.onPrimaryContainer
        connecting -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = content,
                )
            } else {
                LiveDot(
                    color = if (connected) MaterialTheme.colorScheme.primary else content,
                    pulsing = connected,
                    size = 6.dp,
                )
            }
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlowNavBar(selected: Int, onSelect: (Int) -> Unit) {
    ShortNavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        arrangement = ShortNavigationBarArrangement.EqualWeight,
    ) {
        DESTINATIONS.forEachIndexed { index, item ->
            ShortNavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
            )
        }
    }
}
