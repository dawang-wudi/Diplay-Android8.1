package com.shilapi.xcertplay

import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], qualifiers = "zh-rCN")
class ConnectionStageLocalizationTest {
    private val activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
    private val stage = TextView(activity).also {
        CarPlayHostActivity::class.java.getDeclaredField("stageStatusView")
            .apply { isAccessible = true }.set(activity, it)
    }

    @Suppress("UNCHECKED_CAST")
    private val report = CarPlayHostActivity::class.java
        .getDeclaredMethod("createStatusReporter", Int::class.javaPrimitiveType)
        .apply { isAccessible = true }.invoke(activity, 0) as (CarPlayStatus) -> Unit

    @Test fun chineseConnectionStagesAreNotReplacedWithPreparing() {
        report(CarPlayStatus.WaitingForIphone)
        assertEquals(activity.getString(R.string.waiting_for_iphone_over_usb), stage.text.toString())
        report(CarPlayStatus.ConnectingBluetooth)
        assertEquals(activity.getString(R.string.connecting_bluetooth), stage.text.toString())
    }

    @Test fun permissionFailureStillShowsLocalizedRecoveryGuidance() {
        report(CarPlayStatus.Failed("Allow Nearby devices permission to select your iPhone"))
        assertEquals(activity.getString(R.string.allow_nearby_devices_for_diplay_in_the_head_unit_s_app_per), stage.text.toString())
    }

    @Test fun vpnDenialMessageRemainsVisible() {
        val message = activity.getString(R.string.vpn_consent_was_denied)
        CarPlayHostActivity::class.java.getDeclaredMethod("setConnectionStage", String::class.java)
            .apply { isAccessible = true }.invoke(activity, message)
        assertEquals(message, stage.text.toString())
    }

    @Test fun manualHotspotStartupShowsAutomaticWaitingInChinese() {
        CarPlayHostActivity::class.java.getDeclaredField("wirelessHotspotMode")
            .apply { isAccessible = true }.set(activity, com.shilapi.xcertplay.orchestration.WirelessHotspotMode.MANUAL)
        report(CarPlayStatus.StartingHotspot)
        assertEquals(activity.getString(R.string.waiting_for_car_hotspot), stage.text.toString())
    }

    @Test fun slowHotspotTimeoutExplainsAutomaticRetryWithoutAnOffDialog() {
        report(CarPlayStatus.Failed("Could not establish MANUAL hotspot: Timed out after 120000ms waiting for the manual hotspot: system AP not enabled yet"))
        assertEquals(activity.getString(R.string.car_hotspot_wait_timeout), stage.text.toString())
    }
}
