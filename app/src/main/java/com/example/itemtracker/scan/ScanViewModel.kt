package com.example.itemtracker.scan

import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One committed scan with the time it arrived. */
data class Scan(val code: String, val atMs: Long)

class ScanViewModel : ViewModel() {

    private val capture = ScanCaptureBuffer()

    // Committed scans go through an unbounded channel drained by a single
    // consumer coroutine: each scan is fully processed before the next one
    // starts, so two rapid consecutive scans can never interleave or drop.
    private val committed = Channel<Scan>(Channel.UNLIMITED)

    private val _scans = MutableStateFlow<List<Scan>>(emptyList())
    /** Newest first. */
    val scans: StateFlow<List<Scan>> = _scans.asStateFlow()

    init {
        viewModelScope.launch {
            for (scan in committed) {
                _scans.update { listOf(scan) + it }
            }
        }
    }

    /**
     * Fed every key event from Activity.dispatchKeyEvent, before any view or
     * focus handling. This is what makes capture focus-independent: it works
     * with a dialog open, with nothing focused, or with the screen mid
     * navigation. Returns true when the event was consumed as scanner input.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            // Swallow the matching ACTION_UP for keys we handle on the way
            // down, let everything else (volume, back, etc.) pass through.
            return event.action == KeyEvent.ACTION_UP && isHandledKey(event)
        }
        if (isTerminator(event.keyCode)) {
            capture.onTerminator()?.let {
                committed.trySend(Scan(it, System.currentTimeMillis()))
            }
            return true
        }
        val c = event.unicodeChar
        if (c != 0 && !Character.isISOControl(c)) {
            capture.onChar(c.toChar())
            return true
        }
        return false
    }

    fun clearHistory() {
        _scans.value = emptyList()
    }

    private fun isTerminator(keyCode: Int) =
        keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun isHandledKey(event: KeyEvent) =
        isTerminator(event.keyCode) ||
            (event.unicodeChar != 0 && !Character.isISOControl(event.unicodeChar))
}
