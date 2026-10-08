package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.media.AudioFocusRequest as AndroidFocusRequest
import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAudioManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 28], manifest = Config.NONE, shadows = [NavigationAudioFocusTest.Manager::class])
class NavigationAudioFocusTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val manager get() = Shadow.extract<Manager>(context.getSystemService(AudioManager::class.java))

    @Test fun concurrentGuidanceTracksShareOneTransientRequestAndReleaseTheLastOwner() {
        val focus = NavigationAudioFocus(context, true)
        val first = Any(); val second = Any()
        focus.acquire(first); focus.acquire(first); focus.acquire(second)
        assertEquals(1, manager.requests.size)
        val request = manager.requests.single()
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, request.focusGain)
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, request.audioAttributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, request.audioAttributes.contentType)
        focus.release(first)
        assertTrue(manager.abandoned.isEmpty())
        focus.release(second); focus.release(second)
        assertEquals(listOf(request), manager.abandoned)
        focus.acquire(first)
        assertEquals(2, manager.requests.size)
        focus.release(first)
    }

    @Test fun switchOffLeavesTheExistingFocusBehaviorIntact() {
        val focus = NavigationAudioFocus(context, false)
        val owner = Any()
        focus.acquire(owner); focus.release(owner)
        assertTrue(manager.requests.isEmpty())
        assertTrue(manager.abandoned.isEmpty())
    }

    @Test fun rejectedFocusDoesNotCrashPlaybackOrRetryOnEveryAudioPacket() {
        manager.reject = true
        val lines = mutableListOf<String>()
        val focus = NavigationAudioFocus(context, true, lines::add)
        val owner = Any()
        repeat(5) { focus.acquire(owner) }
        assertEquals(1, manager.requests.size)
        assertTrue(lines.any { "result=0" in it })
        focus.release(owner)
    }

    @Implements(AudioManager::class)
    class Manager : ShadowAudioManager() {
        val requests = mutableListOf<AndroidFocusRequest>()
        val abandoned = mutableListOf<AndroidFocusRequest>()
        var reject = false
        @Implementation override fun requestAudioFocus(request: AndroidFocusRequest): Int {
            requests += request
            return if (reject) AudioManager.AUDIOFOCUS_REQUEST_FAILED else AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        @Implementation override fun abandonAudioFocusRequest(request: AndroidFocusRequest): Int {
            abandoned += request
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }
}
