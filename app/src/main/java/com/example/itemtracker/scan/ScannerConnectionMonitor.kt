package com.example.itemtracker.scan

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScannerStatus { CONNECTED, RECONNECTING, DISCONNECTED }

private const val POLL_INTERVAL_MS = 1_000L

/**
 * Tracks the scanner's link state by listening to system ACL
 * connect/disconnect broadcasts, plus a periodic GATT poll for BLE devices
 * (see queryBleConnected). The broadcast alone is not enough: it only
 * reports transitions, and ACL broadcasts for BLE peripherals are not
 * consistently reliable across phones/Bluetooth stacks. Without polling, a
 * disconnect that doesn't produce a broadcast leaves the status frozen on
 * whatever it last was, e.g. stuck showing Connected after the device has
 * actually gone away. Polling re-asks the real state on a steady clock
 * instead of only reacting to events that may not always fire.
 *
 * The scanner is paired through the tablet's Bluetooth Settings, not
 * in-app: a Settings-owned bond means the OS reconnects a dropped HID
 * device by itself, and there is no public API for an app to force an HID
 * reconnection anyway. So this class only observes and reports.
 *
 * State derivation: RECONNECTING is a brief transitional state, shown the
 * instant an ACL disconnect broadcast fires (faster than the poll cycle),
 * on the assumption the OS is about to retry. The next poll tick, at most
 * POLL_INTERVAL_MS later, settles it for real: CONNECTED if the device came
 * back, DISCONNECTED if the GATT query confirms it is genuinely not
 * connected. So DISCONNECTED is the honest resting state once we know for
 * certain the link is down, not just reserved for Bluetooth being off.
 */
class ScannerConnectionMonitor(private val context: Context) {

    private val _status = MutableStateFlow(initialStatus())
    val status: StateFlow<ScannerStatus> = _status.asStateFlow()

    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            poll()
            pollHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (isBleDevice(aclDevice(intent))) _status.value = ScannerStatus.CONNECTED
                }

                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (isBleDevice(aclDevice(intent))) _status.value = ScannerStatus.RECONNECTING
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR
                    )
                    if (state != BluetoothAdapter.STATE_ON) {
                        _status.value = ScannerStatus.DISCONNECTED
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun aclDevice(intent: Intent): BluetoothDevice? =
        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    /**
     * Scopes ACL events to BLE/dual-mode devices, same as queryBleConnected.
     * Without this, any paired device's connect/disconnect (headphones, a
     * car kit) would move the status — those are almost always Classic
     * Bluetooth, not BLE, so this filters out the common false-positive
     * case cheaply. It does not distinguish between two different BLE
     * devices paired at once; that would need remembering the scanner's
     * specific address, out of scope for this single-scanner test setup.
     */
    private fun isBleDevice(device: BluetoothDevice?): Boolean {
        if (device == null) return false
        return try {
            device.type == BluetoothDevice.DEVICE_TYPE_LE ||
                device.type == BluetoothDevice.DEVICE_TYPE_DUAL
        } catch (e: SecurityException) {
            false
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        pollHandler.post(pollRunnable)
    }

    fun stop() {
        pollHandler.removeCallbacks(pollRunnable)
        context.unregisterReceiver(receiver)
    }

    /** Re-checks the real BLE connection state; a no-op for Classic HID. */
    private fun poll() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter?.isEnabled != true) {
            _status.value = ScannerStatus.DISCONNECTED
            return
        }
        queryBleConnected()?.let { connected ->
            _status.value = if (connected) ScannerStatus.CONNECTED else ScannerStatus.DISCONNECTED
        }
    }

    /**
     * Call when scanner input actually arrives. There's no public API to ask
     * "is this HID device connected right now" on demand, only the ACL
     * broadcast for future transitions, so a device already connected before
     * the app launched leaves us stuck on the pessimistic initial guess
     * forever. A character arriving through dispatchKeyEvent is undeniable
     * proof the link is live, so it corrects the guess immediately.
     */
    fun reportActivity() {
        _status.value = ScannerStatus.CONNECTED
    }

    /**
     * For BLE HID (HOGP) devices, unlike Classic HID, there is a public,
     * on-demand connection query: BluetoothManager.getConnectionState with
     * BluetoothProfile.GATT reflects the adapter's real current state, not
     * just future transitions. HOGP is built on GATT, so a bonded BLE
     * scanner's link shows up here directly, whether it connected before
     * this app started or afterward. Returns null when there's no bonded
     * BLE/dual device to check (Classic HID gun, or permission not granted
     * yet), meaning the caller should leave the status as-is rather than
     * treat "nothing to check" as "disconnected."
     */
    private fun queryBleConnected(): Boolean? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        if (!adapter.isEnabled) return null
        return try {
            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bleDevices = adapter.bondedDevices.orEmpty()
                .filter { it.type == BluetoothDevice.DEVICE_TYPE_LE || it.type == BluetoothDevice.DEVICE_TYPE_DUAL }
            if (bleDevices.isEmpty()) return null
            bleDevices.any {
                bluetoothManager.getConnectionState(it, BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED
            }
        } catch (e: SecurityException) {
            // BLUETOOTH_CONNECT permission not granted yet (its request is
            // still in flight on first launch).
            null
        }
    }

    private fun initialStatus(): ScannerStatus {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter?.isEnabled != true) return ScannerStatus.DISCONNECTED
        return when (queryBleConnected()) {
            true -> ScannerStatus.CONNECTED
            false -> ScannerStatus.DISCONNECTED
            null -> ScannerStatus.RECONNECTING // unknown yet; corrected by the first poll or scan
        }
    }
}
