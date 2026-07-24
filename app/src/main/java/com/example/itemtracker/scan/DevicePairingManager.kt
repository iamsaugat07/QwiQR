package com.example.itemtracker.scan

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BondState { NONE, BONDING, BONDED }

// Keeps both the bonded and discovered lists scoped to our scanner instead
// of showing every nearby/paired Bluetooth device (headphones, other
// phones, etc). Confirmed via adb dumpsys that the scanner's live name is
// "NIZI_1806..." both while advertising and once bonded.
private const val NAME_FILTER = "nizi"

data class DiscoveredDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val bondState: BondState,
)

/**
 * Drives in-app discovery and pairing, replacing the OS Settings flow used
 * in the original test task. BluetoothAdapter.startDiscovery() only performs
 * classic BR/EDR inquiry, which does not reliably find LE-only peripherals
 * like this scanner, so a BluetoothLeScanner scan runs alongside it to catch
 * BLE devices that haven't been bonded yet. BluetoothDevice.createBond()
 * initiates pairing without leaving the app. Bonded devices are shown
 * immediately; either scan adds unpaired ones as they're found.
 */
class DevicePairingManager(private val context: Context) {

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val seen = linkedMapOf<String, DiscoveredDevice>()

    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            upsert(result.device)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = aclDevice(intent) ?: return
                    upsert(device)
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = aclDevice(intent) ?: return
                    upsert(device)
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _scanning.value = false
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun aclDevice(intent: Intent): BluetoothDevice? =
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    private fun upsert(device: BluetoothDevice) {
        try {
            if (device.name?.contains(NAME_FILTER, ignoreCase = true) != true) return
            val bond = when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> BondState.BONDED
                BluetoothDevice.BOND_BONDING -> BondState.BONDING
                else -> BondState.NONE
            }
            seen[device.address] = DiscoveredDevice(
                device = device,
                name = device.name ?: device.address,
                address = device.address,
                bondState = bond,
            )
            _devices.value = seen.values.sortedByDescending { it.bondState == BondState.BONDED }
        } catch (e: SecurityException) {
            // BLUETOOTH_CONNECT not granted yet; skip this update.
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        loadBondedDevices()
    }

    fun stop() {
        stopScan()
        context.unregisterReceiver(receiver)
    }

    private fun loadBondedDevices() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        try {
            adapter.bondedDevices.orEmpty().forEach { upsert(it) }
        } catch (e: SecurityException) {
            // BLUETOOTH_CONNECT not granted yet.
        }
    }

    fun startScan() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        if (!adapter.isEnabled) return
        try {
            loadBondedDevices()
            adapter.cancelDiscovery()
            _scanning.value = adapter.startDiscovery()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            adapter.bluetoothLeScanner?.startScan(null, settings, leScanCallback)
        } catch (e: SecurityException) {
            // BLUETOOTH_SCAN not granted; the refresh simply does nothing.
        }
    }

    fun stopScan() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.cancelDiscovery()
            adapter?.bluetoothLeScanner?.stopScan(leScanCallback)
        } catch (e: SecurityException) {
            // Nothing further to do.
        }
        _scanning.value = false
    }

    /**
     * Initiates pairing without leaving the app. createBond() is
     * asynchronous: the result arrives via ACTION_BOND_STATE_CHANGED above,
     * which updates the device's row once bonding completes (the system
     * still shows its own pairing confirmation dialog/PIN prompt, that part
     * can't be skipped, but the device list and trigger stay in-app).
     */
    fun pair(device: DiscoveredDevice) {
        try {
            device.device.createBond()
        } catch (e: SecurityException) {
            // BLUETOOTH_CONNECT not granted; pairing simply doesn't start.
        }
    }
}
