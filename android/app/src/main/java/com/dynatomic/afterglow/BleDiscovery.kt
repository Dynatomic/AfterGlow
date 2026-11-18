// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.os.Handler

/** Discovery reports identities only. It cannot select a driver, authenticate, or send commands. */
interface DeviceDiscovery {
    val transport: TransportType
    val active: Boolean
    fun start(found: (LightDevice) -> Unit, failure: (String) -> Unit): Boolean
    fun stop()
}

@SuppressLint("MissingPermission")
class AndroidBleDiscovery(private val adapter: BluetoothAdapter?, private val ui: Handler) : DeviceDiscovery {
    override val transport = TransportType.BLE
    private var generation = 0L
    private var scanner: BluetoothLeScanner? = null
    private var callback: ScanCallback? = null
    override val active get() = callback != null
    override fun start(found: (LightDevice) -> Unit, failure: (String) -> Unit): Boolean {
        stop()
        val token = generation
        val next = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) { ui.post {
                if (token != generation || callback !== this) return@post
                try {
                    val device = result.device
                    found(LightDevice(device.address, device.name?.takeIf { it.isNotBlank() } ?: "Unnamed Bluetooth device"))
                } catch (_: SecurityException) { failure("Bluetooth permission was removed. Allow Nearby devices and retry.") }
            } }
            override fun onScanFailed(code: Int) { ui.post {
                if (token == generation && callback === this) failure("Bluetooth scan failed. Try Scan nearby again.")
            } }
        }
        try {
            scanner = adapter?.bluetoothLeScanner
            if (scanner == null) { failure("Bluetooth scanning is unavailable."); return false }
            callback = next
            scanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), next)
            return true
        } catch (_: SecurityException) {
            stop(); failure("Bluetooth permission was removed. Allow Nearby devices and retry.")
        } catch (_: Exception) { stop(); failure("Could not start Bluetooth scanning. Try again.") }
        return false
    }
    override fun stop() {
        generation++
        val old = callback
        callback = null
        if (old != null) try { scanner?.stopScan(old) } catch (_: Exception) {}
        scanner = null
    }
}
