package com.example.goveecontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.goveecontroller.ble.GoveeBleManager
import com.example.goveecontroller.ui.AutoScreen
import com.example.goveecontroller.ui.ControlScreen
import com.example.goveecontroller.ui.EffectsScreen
import com.example.goveecontroller.ui.GoveeGlowTheme
import com.example.goveecontroller.ui.GradientScreen
import com.example.goveecontroller.ui.LabScreen
import com.example.goveecontroller.ui.SegmentsScreen
import kotlinx.coroutines.launch

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
            GoveeGlowTheme(
                darkTheme = when (themeMode) {
                    GoveeBleManager.THEME_LIGHT -> false
                    GoveeBleManager.THEME_DARK -> true
                    else -> isSystemInDarkTheme()
                },
            ) {
                var tab by remember { mutableIntStateOf(0) }
                val items = listOf(
                    "Control" to Icons.Default.Tune,
                    "Blend" to Icons.Default.Gradient,
                    "Zones" to Icons.Default.Splitscreen,
                    "Music" to Icons.Default.MusicNote,
                    "Auto" to Icons.Default.Alarm,
                    "Lab" to Icons.Default.Science,
                )
                Scaffold(
                    topBar = { GlowStatusBar(ble) },
                    bottomBar = {
                        NavigationBar {
                            items.forEachIndexed { i, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(icon, label) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    },
                ) { pad ->
                    Box(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> ControlScreen(ble)
                            1 -> GradientScreen(ble)
                            2 -> SegmentsScreen(ble)
                            3 -> EffectsScreen(ble)
                            4 -> AutoScreen()
                            else -> LabScreen(ble)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlowStatusBar(ble: GoveeBleManager) {
    val connected by ble.connected.collectAsState()
    val connecting by ble.connecting.collectAsState()
    val name by ble.connectedName.collectAsState()
    val saved by ble.savedName.collectAsState()
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Govee Glow", fontWeight = FontWeight.Bold)
            }
        },
        actions = {
            Row(
                Modifier.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (connecting) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Linking…", style = MaterialTheme.typography.labelMedium)
                } else {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape).background(
                            if (connected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            connected -> name ?: "Linked"
                            saved != null -> "Auto · $saved"
                            else -> "Offline"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
