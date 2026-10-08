package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioFocusRequest as AndroidFocusRequest
import android.media.AudioManager
import android.os.Looper
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAudioManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 28], shadows = [CarPlayMediaFocusRegressionTest.Manager::class])
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayMediaFocusRegressionTest {
    @Test fun disabledMediaFocusStaysDisabledWhenMusicAndMediaSessionStart() = checkFocus(false, 0)
    @Test fun enabledMediaFocusHasOnlyOneOwnerAcrossPlaybackUpdates() = checkFocus(true, 1)

    private fun checkFocus(enabled: Boolean, expectedRequests: Int) {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int) = false
        }
        AirPlayPersistence.saveAudioFocusEnabled(context, enabled)
        val manager = Shadow.extract<Manager>(context.getSystemService(AudioManager::class.java))
        val ready = CountDownLatch(1)
        val ended = CountDownLatch(1)
        val sink = AndroidMediaSink(context = context, audioFocusEnabled = enabled,
            onMediaAudioChanged = CarPlayMediaKeys::onMediaAudioChanged,
            onAudioDiagnostic = {
                if (it.startsWith("Audio: ready")) ready.countDown()
                if ("ended=true" in it) ended.countDown()
            })
        val controller = CarPlayController(context,
            CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL,
                identification = Iap2IdentificationConfig("Audit", "Audit", "Audit", "Audit", "1", "1", 0)),
            AirPlayConfig("Audit", "02:00:00:00:00:01", "02:00:00:00:00:01", "1", AirPlayDisplayConfig(800, 480)),
            AirPlayIdentity.generate(), PairingStore(), object : AirPlaySessionListener {},
            CarPlayMediaEngine(sink), {})
        try {
            CarPlayMediaKeys.attach(context, controller, onPlaybackStarted = sink::onMediaPlaybackStarted)
            CarPlayMediaKeys.onSessionActive(controller)
            sink.onAudioStarted(AudioStreamId(100, "media"), AudioFormat(AudioCodecKind.LPCM, 48000, 2, 100, "media"), 0)
            assertTrue("Music must initialize", ready.await(5, TimeUnit.SECONDS))
            // The renderer requests focus just after its ready diagnostic; wait for the first PCM.
            sink.onAudioRtp(AudioStreamId(100, "media"), AudioFormat(AudioCodecKind.LPCM, 48000, 2, 100, "media"),
                ByteArray(972) { 0x40 }, 0)
            shadowOf(Looper.getMainLooper()).idle()
            controller.playbackListener?.invoke(true)
            shadowOf(Looper.getMainLooper()).idle()
            sink.close()
            assertTrue(ended.await(5, TimeUnit.SECONDS))
            assertEquals("Media-session key handling must not independently claim audio focus",
                expectedRequests, manager.requests.size)
        } finally {
            sink.close()
            CarPlayMediaKeys.detach(controller)
            controller.close()
            controller.awaitClosed(5000)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Implements(AudioManager::class)
    class Manager : ShadowAudioManager() {
        val requests = CopyOnWriteArrayList<AndroidFocusRequest>()
        @Implementation override fun requestAudioFocus(request: AndroidFocusRequest): Int {
            requests += request
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
}
