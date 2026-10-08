package com.shilapi.xcertplay

import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Looper
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayMediaPublicationTest {
    @Test fun repeatedPositionUpdatesPublishPlaybackButDoNotRepublishSongMetadata() {
        val controller = mock(CarPlayController::class.java)
        val session = mock(MediaSession::class.java)
        val context = RuntimeEnvironment.getApplication()
        val onUpdate = CarPlayMediaKeys::class.java.getDeclaredMethod(
            "onNowPlayingChanged", CarPlayController::class.java, CarPlayNowPlaying::class.java,
        ).apply { isAccessible = true }
        CarPlayMediaKeys.attach(context, controller)
        CarPlayMediaKeys::class.java.getDeclaredField("session").apply { isAccessible = true }
            .set(CarPlayMediaKeys, session)
        try {
            val first = CarPlayNowPlaying(title = "First song", artist = "Artist", playing = true)
            onUpdate.invoke(CarPlayMediaKeys, controller, first)
            repeat(1000) { index ->
                onUpdate.invoke(CarPlayMediaKeys, controller, first.copy(
                    elapsedMillis = (index + 1) * 500L, playing = index < 999,
                ))
            }
            onUpdate.invoke(CarPlayMediaKeys, controller, first.copy(title = "Second song", elapsedMillis = 0))
            shadowOf(Looper.getMainLooper()).idle()

            val metadata = ArgumentCaptor.forClass(MediaMetadata::class.java)
            verify(session, times(2)).setMetadata(metadata.capture())
            assertEquals(listOf("First song", "Second song"), metadata.allValues.map {
                it.getString(MediaMetadata.METADATA_KEY_TITLE)
            })
            val playback = ArgumentCaptor.forClass(PlaybackState::class.java)
            verify(session, times(1002)).setPlaybackState(playback.capture())
            assertEquals(500_000L, playback.allValues[1000].position)
            assertEquals(PlaybackState.STATE_PAUSED, playback.allValues[1000].state)
        } finally {
            CarPlayMediaKeys.detach(controller)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
