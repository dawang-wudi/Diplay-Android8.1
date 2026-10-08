package com.shilapi.xcertplay

import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Looper
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedConstruction
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 28])
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayMediaStartupTest {
    private lateinit var sessions: MockedConstruction<MediaSession>
    private lateinit var controller: CarPlayController

    @Before fun setUp() {
        sessions = mockConstruction(MediaSession::class.java)
        controller = mock(CarPlayController::class.java)
        CarPlayMediaKeys.attach(RuntimeEnvironment.getApplication(), controller)
    }

    @After fun tearDown() {
        CarPlayMediaKeys.detach(controller)
        shadowOf(Looper.getMainLooper()).idle()
        sessions.close()
    }

    @Test fun connectedWithoutMusicRegistersControlsBeforeTheFirstPhoneTrackChange() {
        assertTrue(sessions.constructed().isEmpty())
        CarPlayMediaKeys.onSessionActive(controller)
        assertEquals(1, sessions.constructed().size)
        val session = sessions.constructed().single()
        val invocations = mockingDetails(session).invocations.toList()
        assertTrue(invocations.indexOfFirst { it.method.name == "setPlaybackState" } <
            invocations.indexOfFirst { it.method.name == "setActive" })
        verify(session).isActive = true
        assertEquals(PlaybackState.STATE_PAUSED, state().state)
        assertTrue(state().actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L)
        assertTrue(state().actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L)
        callback().onSkipToNext()
        verify(controller).sendMediaButton(CarPlayMediaButton.NEXT)
        verify(controller, never()).sendMediaButton(CarPlayMediaButton.PLAY)
        verify(controller, never()).sendMediaButton(CarPlayMediaButton.PLAY_PAUSE)
    }

    @Test fun songAndPositionBeforePhoneStatusDoNotMarkAnOpenMusicStreamPaused() {
        CarPlayMediaKeys.onSessionActive(controller)
        CarPlayMediaKeys.onMediaAudioChanged(true)
        shadowOf(Looper.getMainLooper()).idle()
        update(CarPlayNowPlaying(title = "Already playing", elapsedMillis = 1000))
        assertEquals(PlaybackState.STATE_PLAYING, state().state)
        assertEquals(1000L, state().position)
        callback().onSkipToPrevious()
        verify(controller).sendMediaButton(CarPlayMediaButton.PREVIOUS)
    }

    @Test fun explicitPauseStillWinsWhileTheMusicStreamRemainsOpen() {
        CarPlayMediaKeys.onSessionActive(controller)
        CarPlayMediaKeys.onMediaAudioChanged(true)
        shadowOf(Looper.getMainLooper()).idle()
        update(CarPlayNowPlaying(title = "Paused", playbackStatusKnown = true))
        assertEquals(PlaybackState.STATE_PAUSED, state().state)
        callback().onSkipToNext()
        verify(controller).sendMediaButton(CarPlayMediaButton.NEXT)
    }

    @Test fun phonePlaybackStatusWorksWithoutATitleOrAudioStartCallback() {
        CarPlayMediaKeys.onSessionActive(controller)
        playbackListener(controller).invoke(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(PlaybackState.STATE_PLAYING, state().state)
        playbackListener(controller).invoke(false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(PlaybackState.STATE_PAUSED, state().state)
    }

    @Test fun foregroundWheelPressWorksBeforeAnySongAndIsNotRepeatedOnReleaseOrHold() {
        assertFalse(CarPlayMediaKeys.onKeyEvent(controller, key(KeyEvent.KEYCODE_MEDIA_NEXT)))
        CarPlayMediaKeys.onSessionActive(controller)
        assertTrue(CarPlayMediaKeys.onKeyEvent(controller, key(KeyEvent.KEYCODE_MEDIA_NEXT)))
        assertTrue(CarPlayMediaKeys.onKeyEvent(controller, key(KeyEvent.KEYCODE_MEDIA_NEXT, repeat = 1)))
        assertTrue(CarPlayMediaKeys.onKeyEvent(controller, key(KeyEvent.KEYCODE_MEDIA_NEXT, action = KeyEvent.ACTION_UP)))
        assertFalse(CarPlayMediaKeys.onKeyEvent(controller, key(KeyEvent.KEYCODE_VOLUME_UP)))
        verify(controller, times(1)).sendMediaButton(CarPlayMediaButton.NEXT)
    }

    @Test fun duplicateConnectionAndAudioStartKeepOneMediaSession() {
        CarPlayMediaKeys.onSessionActive(controller)
        CarPlayMediaKeys.onSessionActive(controller)
        CarPlayMediaKeys.onMediaAudioChanged(true)
        shadowOf(Looper.getMainLooper()).idle()
        CarPlayMediaKeys.onMediaAudioChanged(false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, sessions.constructed().size)
        verify(sessions.constructed().single(), times(1)).isActive = true
        assertEquals(PlaybackState.STATE_PAUSED, state().state)
    }

    @Test fun hostWindowConsumesTheWheelPressInsteadOfRedispatchingItToAndroid() {
        val activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        CarPlayHostActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }
            .set(activity, controller)
        CarPlayMediaKeys.onSessionActive(controller)
        assertTrue(activity.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT)))
        assertTrue(activity.dispatchKeyEvent(key(KeyEvent.KEYCODE_MEDIA_NEXT, action = KeyEvent.ACTION_UP)))
        verify(controller, times(1)).sendMediaButton(CarPlayMediaButton.NEXT)
    }

    @Test fun oldCallbacksCannotPublishOrSendKeysIntoAReconnectedSession() {
        CarPlayMediaKeys.onSessionActive(controller)
        val oldController = controller
        val oldCallback = callback()
        val oldUpdate = updateListener(oldController)
        val oldPlayback = playbackListener(oldController)
        val oldSession = sessions.constructed().single()
        controller = mock(CarPlayController::class.java)
        CarPlayMediaKeys.attach(RuntimeEnvironment.getApplication(), controller)
        CarPlayMediaKeys.onSessionActive(controller)
        verify(oldSession).release()
        oldUpdate(CarPlayNowPlaying(title = "Stale", playing = true))
        oldPlayback(true)
        oldCallback.onSkipToNext()
        CarPlayMediaKeys.onSessionActive(oldController)
        assertFalse(CarPlayMediaKeys.onKeyEvent(oldController, key(KeyEvent.KEYCODE_MEDIA_NEXT)))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, sessions.constructed().size)
        assertEquals(PlaybackState.STATE_PAUSED, state().state)
        verify(oldController, never()).sendMediaButton(anyInt())
        verify(controller, never()).sendMediaButton(anyInt())
    }

    @Test fun detachReleasesSessionAndStopsWindowKeys() {
        CarPlayMediaKeys.onSessionActive(controller)
        val session = sessions.constructed().single()
        CarPlayMediaKeys.detach(controller)
        verify(session).isActive = false
        verify(session).release()
        assertFalse(CarPlayMediaKeys.onKeyEvent(controller, key(KeyEvent.KEYCODE_MEDIA_NEXT)))
    }

    private fun state(): PlaybackState = mockingDetails(sessions.constructed().last()).invocations
        .last { it.method.name == "setPlaybackState" }.getArgument(0)

    private fun callback(): MediaSession.Callback = mockingDetails(sessions.constructed().last()).invocations
        .last { it.method.name == "setCallback" }.getArgument(0)

    private fun updateListener(owner: CarPlayController): (CarPlayNowPlaying) -> Unit =
        mockingDetails(owner).invocations.last { it.method.name == "setNowPlayingListener" }.getArgument(0)

    private fun playbackListener(owner: CarPlayController): (Boolean) -> Unit =
        mockingDetails(owner).invocations.last { it.method.name == "setPlaybackListener" }.getArgument(0)

    private fun update(value: CarPlayNowPlaying) {
        updateListener(controller)(value)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun key(code: Int, action: Int = KeyEvent.ACTION_DOWN, repeat: Int = 0) =
        KeyEvent(0, 0, action, code, repeat)
}
