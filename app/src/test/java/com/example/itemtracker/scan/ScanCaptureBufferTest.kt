package com.example.itemtracker.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanCaptureBufferTest {

    /** Fake clock the test advances manually. */
    private var nowMs = 0L
    private val buffer = ScanCaptureBuffer(interCharTimeoutMs = 150, now = { nowMs })

    private fun type(code: String, gapMs: Long = 10) {
        code.forEach { c ->
            nowMs += gapMs
            buffer.onChar(c)
        }
    }

    @Test
    fun `commits a complete scan on terminator`() {
        type("ITEM-0042")
        assertEquals("ITEM-0042", buffer.onTerminator())
    }

    @Test
    fun `two rapid consecutive scans stay separate`() {
        type("PERSON-007")
        assertEquals("PERSON-007", buffer.onTerminator())
        // Second scan begins 50ms later, well inside human "rapid" range
        nowMs += 50
        type("ITEM-0042")
        assertEquals("ITEM-0042", buffer.onTerminator())
    }

    @Test
    fun `interrupted scan does not merge into the next one`() {
        // Scanner sends half a code, then the trigger is released
        type("ITEM-00")
        // 2 seconds later a fresh scan arrives
        nowMs += 2000
        type("PERSON-007")
        assertEquals("PERSON-007", buffer.onTerminator())
    }

    @Test
    fun `stale buffer followed by bare terminator commits nothing`() {
        type("ITEM-00")
        nowMs += 2000
        assertNull(buffer.onTerminator())
    }

    @Test
    fun `bare terminator with empty buffer commits nothing`() {
        assertNull(buffer.onTerminator())
    }

    @Test
    fun `buffer is clean after each commit`() {
        type("AAA")
        buffer.onTerminator()
        assertNull(buffer.onTerminator())
    }
}
