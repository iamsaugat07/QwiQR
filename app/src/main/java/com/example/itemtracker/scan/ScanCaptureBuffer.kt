package com.example.itemtracker.scan

/**
 * Assembles individual key characters from a Bluetooth HID scanner into
 * complete scan payloads.
 *
 * A scan is committed only when the terminator (Enter/CR) arrives. HID
 * scanners type an entire code in a few tens of milliseconds, so if the gap
 * between two characters exceeds [interCharTimeoutMs] the partial buffer is
 * stale (an interrupted scan, or a human typing) and is discarded rather
 * than allowed to merge into the next scan.
 *
 * Pure logic, no Android dependencies: the clock is injected so the timeout
 * behaviour is unit-testable.
 */
class ScanCaptureBuffer(
    private val interCharTimeoutMs: Long = 150,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val buffer = StringBuilder()
    private var lastCharAtMs = 0L

    /** Feed one printable character from the key event stream. */
    fun onChar(c: Char) {
        val t = now()
        if (buffer.isNotEmpty() && t - lastCharAtMs > interCharTimeoutMs) {
            buffer.clear()
        }
        buffer.append(c)
        lastCharAtMs = t
    }

    /**
     * Terminator (Enter/CR) arrived. Returns the completed scan, or null if
     * the buffer is empty or stale. The buffer is always cleared, so the
     * next scan starts from a clean state either way.
     */
    fun onTerminator(): String? {
        val stale = buffer.isNotEmpty() && now() - lastCharAtMs > interCharTimeoutMs
        val result = if (buffer.isEmpty() || stale) null else buffer.toString()
        buffer.clear()
        return result
    }
}
