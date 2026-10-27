package com.example.goveecontroller.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScannedLight(
    val name: String,
    val address: String,
    val rssi: Int,
    val device: BluetoothDevice,
)

data class PacketLog(val hex: String, val label: String, val timeMs: Long = System.currentTimeMillis())

class GoveeBleManager(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val btManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter get() = btManager.adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var keepAliveJob: Job? = null

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _found = MutableStateFlow<List<ScannedLight>>(emptyList())
    val found: StateFlow<List<ScannedLight>> = _found.asStateFlow()

    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedName: StateFlow<String?> = _connectedName.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    // Remembered strip for auto-connect.
    private val prefs = appContext.getSharedPreferences("govee", Context.MODE_PRIVATE)
    private val _autoEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO, true))
    val autoEnabled: StateFlow<Boolean> = _autoEnabled.asStateFlow()
    private val _savedName = MutableStateFlow(prefs.getString(KEY_NAME, null))
    val savedName: StateFlow<String?> = _savedName.asStateFlow()
    private var userDropped = false
    private var reconnectJob: Job? = null

    // Widget + theme state (persisted).
    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME, THEME_SYSTEM))
    val themeMode: StateFlow<String?> = _themeMode.asStateFlow()
    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
        _themeMode.value = mode
    }
    fun savePower(on: Boolean) = prefs.edit().putBoolean(KEY_POWER, on).apply()
    fun saveBrightness(p: Int) = prefs.edit().putInt(KEY_BRIGHT, p.coerceIn(0, 100)).apply()

    private val _log = MutableStateFlow<List<PacketLog>>(emptyList())
    val log: StateFlow<List<PacketLog>> = _log.asStateFlow()

    fun log(label: String, bytes: ByteArray) {
        _log.update { (listOf(PacketLog(GoveeProtocol.hex(bytes), label)) + it).take(120) }
    }

    fun logText(label: String, text: String) {
        _log.update { (listOf(PacketLog(text, label)) + it).take(120) }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val name = try { dev.name } catch (_: SecurityException) { null } ?: "Unknown"
            // Surface Govee devices first, but keep everything visible for debugging.
            _found.update { cur ->
                val entry = ScannedLight(name, dev.address, result.rssi, dev)
                (listOf(entry) + cur.filter { it.address != dev.address })
                    .sortedWith(compareBy({ !it.name.contains("Govee", true) }, { -it.rssi }))
                    .take(30)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_scanning.value) return
        _found.value = emptyList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner?.startScan(null, settings, scanCallback)
        _scanning.value = true
        scope.launch {
            delay(15_000)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        try { scanner?.stopScan(scanCallback) } catch (_: Exception) {}
        _scanning.value = false
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logText("GATT", "connected, discovering services…")
                // Small delay: some Govee chips return empty services if queried instantly.
                scope.launch {
                    delay(150)
                    try { g.discoverServices() } catch (_: SecurityException) {}
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connected.value = false
                _connectedName.value = null
                _connecting.value = false
                writeChar = null
                keepAliveJob?.cancel()
                logText("GATT", "disconnected (status=$status)")
                Log.d(TAG, "disconnected status=$status userDropped=$userDropped")
                scheduleReconnect()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(GoveeProtocol.SERVICE_UUID)
            val ch = svc?.getCharacteristic(GoveeProtocol.WRITE_UUID)
            if (ch == null) {
                logText("GATT", "⚠ Govee service/char NOT found — wrong model or needs pairing. Services: " +
                        g.services.joinToString { it.uuid.toString().take(8) })
                return
            }
            writeChar = ch
            _connected.value = true
            _connecting.value = false
            _connectedName.value = try { g.device.name } catch (_: SecurityException) { "Govee" }
            rememberDevice(g.device.address, _connectedName.value)
            logText("GATT", "ready ✓ ${g.device.address}")
            Log.d(TAG, "gatt ready ${g.device.address}")
            startKeepAlive()
            com.example.goveecontroller.widget.WidgetState.clearError(appContext)
            com.example.goveecontroller.widget.WidgetUpdater.refreshAll(appContext)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(light: ScannedLight) {
        stopScan()
        disconnectInternal()
        userDropped = false
        reconnectJob?.cancel()
        _connecting.value = true
        logText("GATT", "connecting to ${light.name} ${light.address}…")
        Log.d(TAG, "connect ${light.address}")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            light.device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            light.device.connectGatt(appContext, false, gattCallback)
        }
    }

    /**
     * Auto-connect to the remembered strip by MAC — no scan needed.
     * [force] is for explicit widget/system actions, which must link even
     * when background auto-connect is disabled in the app.
     */
    @SuppressLint("MissingPermission")
    fun autoConnect(force: Boolean = false) {
        if (!force && !_autoEnabled.value) return
        val mac = prefs.getString(KEY_MAC, null) ?: return
        if (_connected.value || _connecting.value) return
        val dev = try {
            adapter?.getRemoteDevice(mac)
        } catch (_: IllegalArgumentException) {
            return
        } ?: return
        userDropped = false
        _connecting.value = true
        logText("GATT", "auto-connecting to $mac…")
        Log.d(TAG, "auto-connect $mac")
        try {
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dev.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                dev.connectGatt(appContext, false, gattCallback)
            }
        } catch (_: SecurityException) {
            _connecting.value = false
            logText("ERR", "missing BLUETOOTH_CONNECT permission")
        }
    }

    fun setAutoEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO, on).apply()
        _autoEnabled.value = on
        if (on) autoConnect() else reconnectJob?.cancel()
    }

    fun connectSaved() = autoConnect()

    fun forgetDevice() {
        prefs.edit().remove(KEY_MAC).remove(KEY_NAME).apply()
        _savedName.value = null
        disconnect()
        logText("GATT", "forgotten — pick your strip in the scanner")
        com.example.goveecontroller.widget.WidgetState.finish(appContext)
        com.example.goveecontroller.widget.WidgetUpdater.refreshAll(appContext)
    }

    private fun rememberDevice(mac: String, name: String?) {
        prefs.edit().putString(KEY_MAC, mac).putString(KEY_NAME, name).apply()
        _savedName.value = name
    }

    private fun scheduleReconnect() {
        if (!_autoEnabled.value || userDropped) return
        if (prefs.getString(KEY_MAC, null) == null) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(4_000)
            if (!_connected.value && !userDropped) autoConnect()
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        userDropped = true
        reconnectJob?.cancel()
        _connecting.value = false
        disconnectInternal()
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal() {
        keepAliveJob?.cancel()
        try { gatt?.disconnect() } catch (_: Exception) {}
        try { gatt?.close() } catch (_: Exception) {}
        gatt = null
        writeChar = null
        _connected.value = false
        _connectedName.value = null
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (true) {
                writeRaw(GoveeProtocol.KEEP_ALIVE, "keep-alive")
                delay(2_000)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeRaw(packet: ByteArray, label: String) {
        val ch = writeChar
        val g = gatt
        if (ch == null || g == null) {
            logText("SKIP", "$label — not connected")
            return
        }
        log(label, packet)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(ch, packet, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                @Suppress("DEPRECATION")
                ch.value = packet
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
        } catch (_: SecurityException) {
            logText("ERR", "missing BLUETOOTH_CONNECT permission")
        }
    }

    // ---- Public commands (all fire-and-forget, 0x52 no-response) ----

    fun power(on: Boolean) {
        savePower(on)
        writeRaw(GoveeProtocol.power(on), if (on) "power ON" else "power OFF")
        com.example.goveecontroller.widget.WidgetState.clearError(appContext)
        com.example.goveecontroller.widget.WidgetUpdater.refreshAll(appContext)
    }
    fun brightness(p: Int) {
        saveBrightness(p)
        writeRaw(GoveeProtocol.brightness(p), "brightness $p%")
        com.example.goveecontroller.widget.WidgetState.clearError(appContext)
        com.example.goveecontroller.widget.WidgetUpdater.refreshAll(appContext)
    }
    fun color(r: Int, g: Int, b: Int) = writeRaw(GoveeProtocol.color(r, g, b), "color #${
        "%02X%02X%02X".format(r, g, b)
    }")
    fun segment(r: Int, g: Int, b: Int, left: Int, right: Int) =
        writeRaw(GoveeProtocol.segmentColor(r, g, b, left, right), "segment L=${left.toString(16)} R=${right.toString(16)}")
    fun colorLegacy(r: Int, g: Int, b: Int) =
        writeRaw(GoveeProtocol.colorLegacy(r, g, b), "color-legacy")
    fun gradient(on: Boolean) = writeRaw(GoveeProtocol.gradient(on), "gradient ${if (on) "ON" else "OFF"}")
    fun scene(id: Int, label: String) = writeRaw(GoveeProtocol.scene(id), "scene $label")

    /**
     * Music start: brightness → palette frags twice (UDP-style Rule 5, BLE
     * is lossy too) → activate. Mic is on the strip — room must be loud.
     */
    fun startMusic(profile: Int, colors: List<Triple<Int, Int, Int>>, sensitivity: Int) {
        scope.launch {
            writeRaw(GoveeProtocol.brightness(80), "music brightness 80%")
            delay(150)
            val frags = GoveeProtocol.musicPalette(profile, colors)
            repeat(2) {
                frags.forEach { writeRaw(it, "music palette") ; delay(120) }
            }
            writeRaw(
                GoveeProtocol.musicActivate(profile, sensitivity),
                "music ON profile=${"%02X".format(profile)} sens=$sensitivity",
            )
        }
    }

    /** Solid color exits music mode (camera-verified). */
    fun stopMusic(r: Int, g: Int, b: Int) =
        writeRaw(GoveeProtocol.musicStop(r, g, b), "music OFF → solid")

    /**
     * Smooth gradient: one verified mask@12 write per segment, ~120ms apart.
     * Pass exactly 15 RGB triples in app order; flip=true reverses physical direction.
     */
    fun applyGradient(segments: List<Triple<Int, Int, Int>>, flip: Boolean = false) {
        require(segments.size == 15) { "need 15 segment colors" }
        scope.launch {
            val ordered = if (flip) segments.reversed() else segments
            ordered.forEachIndexed { i, (r, g, b) ->
                val seg = i + 1
                val left = if (seg <= 8) (1 shl (seg - 1)) else 0
                val right = if (seg > 8) (1 shl (seg - 9)) else 0
                writeRaw(
                    GoveeProtocol.segmentColor(r, g, b, left, right),
                    "gradient seg $seg",
                )
                delay(120)
            }
        }
    }

    /** Raw hex sender for the Lab tab — validates XOR before sending. */
    fun sendRawHex(hex: String): Boolean {
        return try {
            var bytes = GoveeProtocol.hex(hex)
            if (bytes.size == 19) {
                // Auto-append checksum if user typed 19 bytes.
                bytes = bytes + GoveeProtocol.checksum19(bytes)
            }
            if (bytes.size != 20 || !GoveeProtocol.validate(bytes)) {
                logText("ERR", "bad packet (need 20 bytes, last byte = XOR of first 19)")
                return false
            }
            writeRaw(bytes, "raw")
            true
        } catch (e: Exception) {
            logText("ERR", "bad hex: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "GoveeGlow"
        private const val KEY_MAC = "last_mac"
        private const val KEY_NAME = "last_name"
        private const val KEY_AUTO = "auto_enabled"
        const val KEY_POWER = "last_power"
        const val KEY_BRIGHT = "last_brightness"
        private const val KEY_THEME = "theme_mode"
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
