package com.shilapi.xcertplay.orchestration

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBluetoothAdapter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], manifest = Config.NONE, shadows = [HarmanBluetoothMediaHandoffTest.Adapter::class])
@LooperMode(LooperMode.Mode.PAUSED)
class HarmanBluetoothMediaHandoffTest {
    private val adapter get() = BluetoothAdapter.getDefaultAdapter()
    private val shadow get() = Shadow.extract<Adapter>(adapter)
    private val target get() = adapter.getRemoteDevice("02:00:00:00:00:01")

    @Test fun staleSessionCannotBindOrDisconnect() {
        val handoff = HarmanBluetoothMediaHandoff(RuntimeEnvironment.getApplication(), adapter, target, { false }, {})
        handoff.start()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(shadow.listener)
        handoff.close()
    }

    @Test fun closingBeforeAQueuedServiceCallbackStillReleasesItsProxy() {
        val handoff = HarmanBluetoothMediaHandoff(RuntimeEnvironment.getApplication(), adapter, target, { true }, {})
        handoff.start()
        shadowOf(Looper.getMainLooper()).idle()
        val service = Sink(target)
        shadow.listener!!.onServiceConnected(11, service)
        handoff.close()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, service.disconnections)
        assertTrue(shadow.released.contains(service))
    }

    @Test fun handoffOnlyTouchesTheSelectedPhoneAndRestoresItsMusicConnection() {
        val other = adapter.getRemoteDevice("02:00:00:00:00:02")
        val handoff = HarmanBluetoothMediaHandoff(RuntimeEnvironment.getApplication(), adapter, target, { true }, {})
        handoff.start()
        shadowOf(Looper.getMainLooper()).idle()
        val service = Sink(target, other)
        shadow.listener!!.onServiceConnected(11, service)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(target), service.disconnected)
        handoff.close()
        shadowOf(Looper.getMainLooper()).idle()
        // The other phone is still connected, so teardown must not replace it.
        assertTrue(service.connected.isEmpty())
        assertTrue(shadow.released.contains(service))
    }

    class Sink(private val target: BluetoothDevice, private val other: BluetoothDevice? = null) : BluetoothProfile {
        private var state = BluetoothProfile.STATE_CONNECTED
        val disconnected = mutableListOf<BluetoothDevice>()
        val connected = mutableListOf<BluetoothDevice>()
        val disconnections get() = disconnected.size
        override fun getConnectionState(device: BluetoothDevice) = if (device == target) state else BluetoothProfile.STATE_CONNECTED
        override fun getConnectedDevices() = listOfNotNull(target.takeIf { state == BluetoothProfile.STATE_CONNECTED }, other)
        override fun getDevicesMatchingConnectionStates(states: IntArray) = connectedDevices
        fun disconnect(device: BluetoothDevice): Boolean {
            disconnected += device
            if (device == target) state = BluetoothProfile.STATE_DISCONNECTED
            return true
        }
        fun connect(device: BluetoothDevice): Boolean { connected += device; return true }
    }

    @Implements(BluetoothAdapter::class)
    class Adapter : ShadowBluetoothAdapter() {
        var listener: BluetoothProfile.ServiceListener? = null
        val released = mutableListOf<BluetoothProfile>()
        @Implementation override fun getProfileProxy(context: Context, listener: BluetoothProfile.ServiceListener, profile: Int): Boolean {
            this.listener = listener
            assertEquals(11, profile)
            return true
        }
        @Implementation override fun closeProfileProxy(profile: Int, proxy: BluetoothProfile) { released += proxy }
    }
}
