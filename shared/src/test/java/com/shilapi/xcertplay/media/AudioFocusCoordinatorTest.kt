package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.media.AudioFocusRequest as AndroidFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAudioManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 28], manifest = Config.NONE, shadows = [AudioFocusCoordinatorTest.Manager::class])
class AudioFocusCoordinatorTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = Shadow.extract<Manager>(context.getSystemService(AudioManager::class.java))

    @Test fun aNewPlayTransitionReusesTheSameFocusOwnerAfterPermanentLoss() = withMusic { focus ->
        val request = manager.requests.single()
        dispatchFocusChange(request, AudioManager.AUDIOFOCUS_LOSS)
        focus.regainMediaFocus()
        focus.regainMediaFocus()
        assertEquals(2, manager.requests.size)
        assertSame(request, manager.requests.last())
        assertTrue(manager.abandoned.isEmpty())
    }

    @Test fun transientGuidanceFocusIsNotStolenBackByPlaybackUpdates() = withMusic { focus ->
        val request = manager.requests.single()
        for (loss in listOf(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)) {
            dispatchFocusChange(request, loss)
            repeat(3) { focus.regainMediaFocus() }
        }
        assertEquals(1, manager.requests.size)
    }

    @Test fun closingMusicReleasesFocusAndLatePlaybackUpdatesCannotRequestItAgain() {
        lateinit var closed: AudioFocusCoordinator
        withMusic { closed = it }
        closed.regainMediaFocus()
        assertEquals(1, manager.requests.size)
        assertEquals(manager.requests, manager.abandoned)
    }

    private fun dispatchFocusChange(request: AndroidFocusRequest, change: Int) {
        // The Android framework exposes this getter internally, outside the public SDK stubs.
        ReflectionHelpers.callInstanceMethod<AudioManager.OnAudioFocusChangeListener>(
            request, "getOnAudioFocusChangeListener",
        ).onAudioFocusChange(change)
    }

    private fun withMusic(check: (AudioFocusCoordinator) -> Unit) {
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val track = AudioTrack.Builder().setAudioAttributes(attributes)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(4096).build()
        val focus = AudioFocusCoordinator(context, true)
        try {
            focus.acquire(track, AudioChannel.MEDIA, attributes)
            check(focus)
        } finally {
            focus.release(track)
            track.release()
        }
    }

    @Implements(AudioManager::class)
    class Manager : ShadowAudioManager() {
        val requests = mutableListOf<AndroidFocusRequest>()
        val abandoned = mutableListOf<AndroidFocusRequest>()
        @Implementation override fun requestAudioFocus(request: AndroidFocusRequest): Int {
            requests += request
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        @Implementation override fun abandonAudioFocusRequest(request: AndroidFocusRequest): Int {
            abandoned += request
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
}
