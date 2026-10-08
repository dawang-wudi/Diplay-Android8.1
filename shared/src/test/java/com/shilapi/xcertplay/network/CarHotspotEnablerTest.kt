package com.shilapi.xcertplay.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarHotspotEnablerTest {
    @Test fun treatsAProvisioningAppAsUnreachable() {
        assertFalse(CarHotspotEnabler.Capability(provisioningAppConfigured = true,
            writeSettingsGranted = false, writeSettingsPageAvailable = true).reachable)
    }

    @Test fun keepsAnUnknownProvisioningAppReachable() {
        // An unreadable framework array must not be reported as a refusal.
        assertTrue(CarHotspotEnabler.Capability(provisioningAppConfigured = null,
            writeSettingsGranted = false, writeSettingsPageAvailable = false).reachable)
        assertTrue(CarHotspotEnabler.Capability(provisioningAppConfigured = false,
            writeSettingsGranted = false, writeSettingsPageAvailable = false).reachable)
    }

    @Test fun reportsStartedWhenTetheringSucceeds() {
        val clock = FakeClock()
        val diagnostics = mutableListOf<String>()
        val result = awaitTetherStart(
            timeoutMillis = 1_000,
            isCancelled = { false },
            tetherStart = { TetherStart.STARTED },
            onDiagnostic = { diagnostics += it },
            nanoTime = clock::nanoTime,
            sleepNanos = clock::sleepNanos,
        )
        assertEquals(TetherStart.STARTED, result)
        assertTrue(diagnostics.single().contains("STARTED"))
    }

    @Test fun reportsFailureWhenTetheringIsRejected() {
        val clock = FakeClock()
        val result = awaitTetherStart(
            timeoutMillis = 1_000,
            isCancelled = { false },
            tetherStart = { TetherStart.FAILED },
            onDiagnostic = {},
            nanoTime = clock::nanoTime,
            sleepNanos = clock::sleepNanos,
        )
        assertEquals(TetherStart.FAILED, result)
        assertEquals(0L, clock.nanos)
    }

    @Test fun pollsUntilTetheringReportsStarted() {
        val clock = FakeClock()
        var polls = 0
        val result = awaitTetherStart(
            timeoutMillis = 5_000,
            isCancelled = { false },
            tetherStart = { if (++polls >= 3) TetherStart.STARTED else TetherStart.PENDING },
            onDiagnostic = {},
            nanoTime = clock::nanoTime,
            sleepNanos = clock::sleepNanos,
        )
        assertEquals(TetherStart.STARTED, result)
        assertEquals(3, polls)
    }

    @Test fun returnsPendingAfterTheTimeoutWithoutAReport() {
        val clock = FakeClock()
        val result = awaitTetherStart(
            timeoutMillis = 2_000,
            isCancelled = { false },
            tetherStart = { TetherStart.PENDING },
            onDiagnostic = {},
            nanoTime = clock::nanoTime,
            sleepNanos = clock::sleepNanos,
        )
        assertEquals(TetherStart.PENDING, result)
        // The wait is bounded: it stops at the deadline instead of polling forever.
        assertEquals(2_000L, clock.nanos / 1_000_000)
    }

    @Test fun stopsWaitingWhenCancelled() {
        val clock = FakeClock()
        var cancelled = false
        val result = awaitTetherStart(
            timeoutMillis = 60_000,
            isCancelled = { cancelled },
            tetherStart = { TetherStart.PENDING },
            onDiagnostic = {},
            nanoTime = clock::nanoTime,
            sleepNanos = { clock.sleepNanos(it).also { cancelled = true } },
        )
        assertEquals(TetherStart.PENDING, result)
        assertTrue(clock.nanos < 60_000L * 1_000_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsANonPositiveTimeout() {
        awaitTetherStart(
            timeoutMillis = 0,
            isCancelled = { false },
            tetherStart = { TetherStart.PENDING },
            onDiagnostic = {},
        )
    }

    private class FakeClock {
        var nanos = 0L

        fun nanoTime(): Long = nanos

        fun sleepNanos(nanos: Long) {
            this.nanos += nanos
        }
    }
}
