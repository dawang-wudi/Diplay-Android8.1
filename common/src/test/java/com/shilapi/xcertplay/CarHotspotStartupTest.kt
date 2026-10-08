package com.shilapi.xcertplay

import android.net.wifi.WifiManager
import android.os.Looper
import com.shilapi.xcertplay.network.CarHotspotStatus
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 28], shadows = [CarHotspotStartupTest.BootRadio::class])
class CarHotspotStartupTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun savedWorkingConfiguration() {
        AirPlayPersistence.saveWirelessHotspotMode(context, WirelessHotspotMode.MANUAL)
        AirPlayPersistence.saveWirelessEnabled(context, true)
        AirPlayPersistence.saveManualHotspotSsid(context, "Car hotspot")
        AirPlayPersistence.saveManualHotspotPassphrase(context, "test-password")
        DiPlayPreferences.savePhone(context, "02:12:34:56:78:9A", "Test iPhone")
        DiPlayPreferences.saveAutoConnect(context, true)
        assertEquals(false, CarHotspotStatus.isEnabled(context))
    }

    @Test fun manualConnectDoesNotBlockOnHotspotOffDialog() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.javaClass.getDeclaredMethod("connect", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, true)
        assertEquals(CarPlayHostActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
    }

    @Test @Config(qualifiers = "zh-rCN-w400dp-h300dp")
    fun compactColdStartAutomaticallyOpensWaitingHostWithoutRequiringAnotherResume() {
        val lifecycle = Robolectric.buildActivity(DiPlayActivity::class.java).create().start()
        val activity = lifecycle.get()
        // This test exercises startup scheduling, with authentication already provisioned.
        activity.javaClass.getDeclaredField("setupError").apply { isAccessible = true }.set(activity, null)
        try {
            lifecycle.resume()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(CarPlayHostActivity::class.java.name, shadowOf(activity).nextStartedActivity.component!!.className)
            assertNull(ShadowAlertDialog.getLatestAlertDialog())
        } finally { lifecycle.pause().stop().destroy() }
    }

    @Test fun invalidSavedDetailsStillOpenSetupRatherThanStartingAConnection() {
        AirPlayPersistence.saveManualHotspotSsid(context, "")
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.javaClass.getDeclaredMethod("connect", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, true)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals("connection", activity.javaClass.getDeclaredField("page").apply { isAccessible = true }.get(activity))
    }

    @Implements(WifiManager::class)
    class BootRadio {
        @Implementation fun getWifiApState(): Int = 11 // disabled, before the car enables its hotspot
        @Implementation fun isWifiApEnabled(): Boolean = false
    }
}
