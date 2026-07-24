package com.example.itemtracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.itemtracker.scan.ScanScreen
import com.example.itemtracker.scan.ScanViewModel
import com.example.itemtracker.scan.ScannerConnectionMonitor
import com.example.itemtracker.ui.theme.ItemTrackerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScanViewModel by viewModels()
    private lateinit var connectionMonitor: ScannerConnectionMonitor

    private val requestBluetoothPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        connectionMonitor = ScannerConnectionMonitor(applicationContext)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        setContent {
            ItemTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val scans by viewModel.scans.collectAsState()
                    val status by connectionMonitor.status.collectAsState()
                    ScanScreen(scans = scans, status = status, onClear = viewModel::clearHistory)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        connectionMonitor.start()
    }

    override fun onStop() {
        connectionMonitor.stop()
        super.onStop()
    }

    /**
     * Every key event in the Activity passes through here before any view or
     * focus handling, so scanner input is captured no matter what is on
     * screen or focused. This is the reason the app never needs a visible
     * (or hidden) focused text field to receive scans.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.onKeyEvent(event)) {
            connectionMonitor.reportActivity()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
