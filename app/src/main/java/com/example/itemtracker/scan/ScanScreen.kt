package com.example.itemtracker.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Millisecond precision so two rapid consecutive scans are visibly distinct
// entries in the demo.
private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

/**
 * Deliberately contains no focusable text input. Scans arrive through
 * Activity.dispatchKeyEvent regardless of what this screen shows.
 */
@Composable
fun ScanScreen(scans: List<Scan>, status: ScannerStatus, onClear: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Scan Capture", style = MaterialTheme.typography.headlineMedium)
            StatusChip(status)
        }

        LastScanPanel(scans.firstOrNull())

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History (${scans.size})", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClear, enabled = scans.isNotEmpty()) {
                Text("Clear")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(scans) { scan ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    // Code and timestamp stack vertically, sharing full row
                    // width, so long scanned values (URLs, long IDs) wrap
                    // normally instead of squeezing the timestamp into a
                    // sliver that wraps one character per line.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            scan.code,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            timeFormat.format(Date(scan.atMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ScannerStatus) {
    val (label, color) = when (status) {
        ScannerStatus.CONNECTED -> "Connected" to Color(0xFF2E7D32)
        ScannerStatus.RECONNECTING -> "Reconnecting" to Color(0xFFF9A825)
        ScannerStatus.DISCONNECTED -> "Disconnected" to Color(0xFFC62828)
    }
    Surface(shape = MaterialTheme.shapes.large, color = color.copy(alpha = 0.15f)) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun LastScanPanel(last: Scan?) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Last scan", style = MaterialTheme.typography.labelMedium)
            Text(
                last?.code ?: "Waiting for scan…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
