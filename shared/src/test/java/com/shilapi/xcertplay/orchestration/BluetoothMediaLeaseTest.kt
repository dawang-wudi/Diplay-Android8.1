package com.shilapi.xcertplay.orchestration

import android.bluetooth.BluetoothProfile
import org.junit.Assert.*
import org.junit.Test

class BluetoothMediaLeaseTest {
    private class Profile(var current: Int = BluetoothProfile.STATE_CONNECTED) : BluetoothMediaLease.Profile {
        var disconnects = 0
        var connects = 0
        var otherDevice = false
        var reject = false
        var error: Exception? = null
        override fun state(): Int { error?.let { throw it }; return current }
        override fun disconnect(): Boolean {
            disconnects++
            if (!reject) current = BluetoothProfile.STATE_DISCONNECTED
            return !reject
        }
        override fun anotherDeviceConnected() = otherDevice
        override fun connect(): Boolean { connects++; return true }
    }

    @Test fun connectedMusicIsReleasedOnceAndRestoredOnTeardown() {
        val profile = Profile()
        val lease = BluetoothMediaLease(profile) {}
        repeat(4) { lease.check() }
        assertEquals(1, profile.disconnects)
        lease.close(); lease.close(); lease.check()
        assertEquals(1, profile.connects)
        assertEquals(1, profile.disconnects)
    }

    @Test fun disconnectedOrConnectingProfilesAreNotClaimed() {
        for (state in listOf(BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTING)) {
            val profile = Profile(state)
            val lease = BluetoothMediaLease(profile) {}
            lease.check(); lease.close()
            assertEquals(0, profile.disconnects)
            assertEquals(0, profile.connects)
        }
    }

    @Test fun rejectedDisconnectIsNotRestoredAndRetriesAreBounded() {
        val profile = Profile().apply { reject = true }
        val lease = BluetoothMediaLease(profile) {}
        repeat(20) { lease.check() }
        lease.close()
        assertEquals(3, profile.disconnects)
        assertEquals(0, profile.connects)
    }

    @Test fun restoringMusicDoesNotReplaceAnotherConnectedPhone() {
        val profile = Profile()
        val lease = BluetoothMediaLease(profile) {}
        lease.check()
        profile.otherDevice = true
        lease.close()
        assertEquals(0, profile.connects)
    }

    @Test fun permissionFailureIsReportedWithoutARepeatedRequestLoop() {
        val profile = Profile().apply { error = SecurityException() }
        val lines = mutableListOf<String>()
        val lease = BluetoothMediaLease(profile, lines::add)
        repeat(4) { lease.check() }
        lease.close()
        assertEquals(1, lines.size)
        assertTrue(lines.single().contains("SecurityException"))
        assertEquals(0, profile.disconnects)
        assertEquals(0, profile.connects)
    }

    @Test fun lateChecksAfterCloseCannotDisconnectThePhone() {
        val profile = Profile()
        val lease = BluetoothMediaLease(profile) {}
        lease.close()
        lease.check()
        assertEquals(0, profile.disconnects)
    }

    @Test fun oemAutoReconnectIsReleasedButNeverRetriedForever() {
        val profile = Profile()
        val lease = BluetoothMediaLease(profile) {}
        repeat(5) { profile.current = BluetoothProfile.STATE_CONNECTED; lease.check() }
        assertEquals(3, profile.disconnects)
        lease.close()
        assertEquals(0, profile.connects)
    }

    @Test fun oemHandoffIsLimitedToTheVerifiedPlatform() {
        assertTrue(HarmanBluetoothMediaHandoff.supported(27, "gwmv2_extend"))
        assertFalse(HarmanBluetoothMediaHandoff.supported(28, "gwmv2_extend"))
        assertFalse(HarmanBluetoothMediaHandoff.supported(27, "generic_x86_64"))
    }
}
