package com.shilapi.xcertplay.orchestration

import android.os.Looper
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.view.MotionEvent
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.media.CarPlayTouchMapper
import com.shilapi.xcertplay.network.CarPlayVpnService
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.net.Socket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Regressions for session isolation, UI handoff and stable touch slots on the Android 8.1 build. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class SessionIsolationRegressionTest {
    private val config = AirPlayConfig("Audit", "02:00:00:00:00:01", "02:00:00:00:00:01", "1", AirPlayDisplayConfig(800, 480))
    private val identity = AirPlayIdentity.generate()
    private val media = CarPlayMediaEngine(object : MediaSink {})
    private fun session(listener: AirPlaySessionListener = object : AirPlaySessionListener {}) =
        AirPlaySession(Socket(), config, identity, PairingStore(), null, listener, media)
    private fun controller(listener: AirPlaySessionListener) = CarPlayController(
        object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = false
        },
        CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL,
            identification = Iap2IdentificationConfig("Audit", "Audit", "Audit", "Audit", "1", "1", 0)),
        config, identity, PairingStore(), listener, media, {})

    @Test fun attachingNewUiRestoresAlreadyActiveSession() {
        val live = session()
        val owner = controller(object : AirPlaySessionListener {})
        try {
            owner.javaClass.getDeclaredField("activeSession").apply { isAccessible = true }.set(owner, live)
            var received: AirPlaySession? = null
            owner.attachUi(object : AirPlaySessionListener {
                override fun onSessionActive(session: AirPlaySession) { received = session }
            }, {})
            shadowOf(Looper.getMainLooper()).idle()
            assertSame("New UI must receive the live session needed for day/night synchronization", live, received)
        } finally { owner.close(); owner.awaitClosed(5000); live.close() }
    }

    @Test fun closingUnrelatedConnectionDoesNotNotifyUiThatLiveSessionEnded() {
        val live = session()
        val ended = AtomicInteger()
        val processed = CountDownLatch(1)
        val owner = controller(object : AirPlaySessionListener {
            override fun onSessionEnded(session: AirPlaySession) { ended.incrementAndGet() }
        })
        val serviceController = Robolectric.buildService(CarPlayVpnService::class.java).create()
        val service = serviceController.get()
        try {
            owner.javaClass.getDeclaredField("activeSession").apply { isAccessible = true }.set(owner, live)
            val bridge = owner.javaClass.getDeclaredField("sessionListener").apply { isAccessible = true }.get(owner) as AirPlaySessionListener
            assertEquals(CarPlayVpnService.AttachResult.Started, service.attachWireless(
                InetAddress.getByName("127.0.0.1"), config.copy(port = 0), identity, PairingStore(), null, object : AirPlaySessionListener by bridge {
                    override fun onSessionEnded(session: AirPlaySession) {
                        bridge.onSessionEnded(session)
                        processed.countDown()
                    }
                }, media))
            // Real loopback connection to the production listener; no authentication or media setup.
            Socket("127.0.0.1", service.boundPort()!!).close()
            assertTrue("Listener must process the disconnected peer", processed.await(3, TimeUnit.SECONDS))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("An unrelated TCP disconnect must not trigger live CarPlay reconnection", 0, ended.get())
        } finally { serviceController.destroy(); owner.close(); owner.awaitClosed(5000); live.close() }
    }

    @Test fun closingUnrelatedSessionPreservesLiveAudioMetadata() {
        val live = session()
        val probe = session()
        live.pairVerify.javaClass.getDeclaredField("sharedSecret").apply { isAccessible = true }.set(live.pairVerify, ByteArray(32) { 1 })
        try {
            assertNotNull(media.onAudio(live, 100, mapOf("audioType" to "media", "audioFormat" to 0x8000L, "streamConnectionID" to 42L)))
            val entries = media.javaClass.getDeclaredField("audioMeta").apply { isAccessible = true }.get(media) as Map<*, *>
            assertEquals(1, entries.size)
            media.onSessionClosed(probe)
            assertEquals("Closing a session with no audio must preserve the live session's timing metadata", 1, entries.size)
        } finally { live.close(); probe.close() }
    }

    @Test fun remainingFingerKeepsItsHidIdentityAfterFirstFingerLifts() {
        val mapper = CarPlayTouchMapper()
        fun report(ids: IntArray, action: Int): ByteArray {
            val props = ids.map { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
            val coords = ids.map { id -> MotionEvent.PointerCoords().apply { x = if (id == 7) 200f else 600f; y = 240f; pressure = 1f; size = 1f } }.toTypedArray()
            val event = MotionEvent.obtain(0, 1, action, ids.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
            return try { AirPlayHid.touchReport(mapper.contacts(event, 800, 480)) } finally { event.recycle() }
        }
        report(intArrayOf(7), MotionEvent.ACTION_DOWN)
        report(intArrayOf(7, 11), MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT))
        val lifted = report(intArrayOf(7, 11), MotionEvent.ACTION_POINTER_UP)
        val moved = report(intArrayOf(11), MotionEvent.ACTION_MOVE)
        assertEquals(0, lifted[1].toInt())
        assertEquals(1, lifted[7].toInt())
        assertEquals("Remaining finger must retain HID slot 1, rather than re-pressing slot 0", 1, moved[7].toInt())
        assertEquals(0, moved[1].toInt())
        val replaced = report(intArrayOf(11, 19), MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT))
        assertEquals(1, replaced[1].toInt())
        assertEquals(1, replaced[7].toInt())
        val cancelled = report(intArrayOf(11, 19), MotionEvent.ACTION_CANCEL)
        assertEquals(0, cancelled[1].toInt())
        assertEquals(0, cancelled[7].toInt())
        val next = report(intArrayOf(23), MotionEvent.ACTION_DOWN)
        assertEquals(1, next[1].toInt())
        assertEquals(0, next[7].toInt())
    }

    @Test fun activeSessionEndStillReachesUiExactlyOnce() {
        val live = session()
        var ended = 0
        val owner = controller(object : AirPlaySessionListener {
            override fun onSessionEnded(session: AirPlaySession) { ended++ }
        })
        try {
            owner.javaClass.getDeclaredField("activeSession").apply { isAccessible = true }.set(owner, live)
            val bridge = owner.javaClass.getDeclaredField("sessionListener").apply { isAccessible = true }.get(owner) as AirPlaySessionListener
            bridge.onSessionEnded(live)
            bridge.onSessionEnded(live)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, ended)
        } finally { owner.close(); owner.awaitClosed(5000); live.close() }
    }

    @Test fun replacedUiDoesNotReceiveQueuedSessionReplay() {
        val live = session()
        val owner = controller(object : AirPlaySessionListener {})
        var oldCalls = 0
        var newCalls = 0
        try {
            owner.javaClass.getDeclaredField("activeSession").apply { isAccessible = true }.set(owner, live)
            owner.attachUi(object : AirPlaySessionListener {
                override fun onSessionActive(session: AirPlaySession) { oldCalls++ }
            }, {})
            owner.attachUi(object : AirPlaySessionListener {
                override fun onSessionActive(session: AirPlaySession) { newCalls++ }
            }, {})
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, oldCalls)
            assertEquals(1, newCalls)
        } finally { owner.close(); owner.awaitClosed(5000); live.close() }
    }

    @Test fun endedSessionIsNotReplayedWhenUiAttachmentWasQueued() {
        val live = session()
        val owner = controller(object : AirPlaySessionListener {})
        var activeCalls = 0
        try {
            owner.javaClass.getDeclaredField("activeSession").apply { isAccessible = true }.set(owner, live)
            owner.attachUi(object : AirPlaySessionListener {
                override fun onSessionActive(session: AirPlaySession) { activeCalls++ }
            }, {})
            val bridge = owner.javaClass.getDeclaredField("sessionListener").apply { isAccessible = true }.get(owner) as AirPlaySessionListener
            bridge.onSessionEnded(live)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, activeCalls)
        } finally { owner.close(); owner.awaitClosed(5000); live.close() }
    }
}
