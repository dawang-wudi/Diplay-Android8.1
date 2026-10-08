package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 28], manifest = Config.NONE)
class SiriAudioRoutingTest {
    private val siriId = AudioStreamId(100, "speechrecognition")
    private val siriFormat = AudioFormat(AudioCodecKind.LPCM, 48000, 1, 100, "speechrecognition")

    @Test fun siriRepliesUseNavigationFocusEvenWhileAMusicStreamIsOpen() {
        val lines = CopyOnWriteArrayList<String>()
        val musicFocus = CountDownLatch(1)
        val siriReady = CountDownLatch(1)
        val siriFocus = CountDownLatch(1)
        val siriPlayed = CountDownLatch(1)
        val siriReleased = CountDownLatch(1)
        val musicPlayed = CountDownLatch(1)
        val sink = AndroidMediaSink(
            context = RuntimeEnvironment.getApplication(),
            audioFocusEnabled = true,
            navigationAudioFocusEnabled = true,
            siriUsesNavigation = true,
            onAudioDiagnostic = { line ->
                lines += line
                when {
                    line.startsWith("Audio: focus requested channel=MEDIA") -> musicFocus.countDown()
                    line.startsWith("Audio: ready audioType=speechrecognition") -> siriReady.countDown()
                    line.startsWith("Audio: navigation focus requested") -> siriFocus.countDown()
                    line.startsWith("Audio: playback audioType=speechrecognition") -> siriPlayed.countDown()
                    line == "Audio: navigation focus released" -> siriReleased.countDown()
                    line.startsWith("Audio: playback audioType=media") -> musicPlayed.countDown()
                }
            },
        )
        val musicId = AudioStreamId(100, "media")
        val musicFormat = siriFormat.copy(audioType = "media")
        try {
            sink.onAudioStarted(musicId, musicFormat, 0)
            assertTrue("Music must own focus first: $lines", musicFocus.await(5, TimeUnit.SECONDS))
            sink.onAudioStarted(siriId, siriFormat, 0)
            assertTrue("Siri must initialize: $lines", siriReady.await(5, TimeUnit.SECONDS))
            assertFalse(lines.any { it.startsWith("Audio: navigation focus requested") })
            sink.onAudioRtp(siriId, siriFormat, pcmPacket(), 0)
            assertTrue("Siri must request temporary navigation focus: $lines", siriFocus.await(5, TimeUnit.SECONDS))
            assertTrue("Short Siri reply must play: $lines", siriPlayed.await(5, TimeUnit.SECONDS))
            assertTrue("Idle Siri must release focus without closing its stream: $lines", siriReleased.await(5, TimeUnit.SECONDS))
            sink.onAudioRtp(musicId, musicFormat, pcmPacket(), 0)
            assertTrue("Music must still play after Siri: $lines", musicPlayed.await(5, TimeUnit.SECONDS))
            assertTrue(lines.any { "audioType=speechrecognition" in it && "mapped=NAVIGATION" in it && "requestedUsage=12" in it })
            assertTrue(lines.any { "audioType=media" in it && "mapped=MEDIA" in it && "requestedUsage=1" in it })
            assertEquals(1, lines.count { it.startsWith("Audio: focus requested channel=MEDIA") })
            assertFalse(lines.any { "channel=ASSISTANT" in it || "renderer failed" in it })
        } finally { sink.close() }
    }

    @Test fun siriInheritsTheSelectedNavigationStreamInsteadOfTheMediaOverride() {
        checkReady(enabled = true, navigationChannel = 3, mediaChannel = 4) { line ->
            // Robolectric leaves getStreamType() at -1; validate the requested legacy
            // route and its attributes, not the head unit's eventual volume-group choice.
            assertTrue(line, "mapped=NAVIGATION" in line && "route=streamType=3" in line && "requestedUsage=1" in line)
        }
    }

    @Test fun disablingCompatibilityRestoresTheAssistantOutput() {
        checkReady(enabled = false, navigationChannel = 3, mediaChannel = 4) { line ->
            assertTrue(line, "mapped=ASSISTANT" in line && "route=usage" in line && "requestedUsage=16" in line)
        }
    }

    private fun checkReady(enabled: Boolean, navigationChannel: Int, mediaChannel: Int, check: (String) -> Unit) {
        val ready = CountDownLatch(1)
        val lines = CopyOnWriteArrayList<String>()
        val sink = AndroidMediaSink(
            context = RuntimeEnvironment.getApplication(),
            siriUsesNavigation = enabled,
            navigationChannel = navigationChannel,
            mediaChannel = mediaChannel,
            onAudioDiagnostic = { line ->
                lines += line
                if (line.startsWith("Audio: ready")) ready.countDown()
            },
        )
        try {
            sink.onAudioStarted(siriId, siriFormat, 0)
            assertTrue("Siri track must initialize: $lines", ready.await(5, TimeUnit.SECONDS))
            check(lines.single { it.startsWith("Audio: ready") })
        } finally { sink.close() }
    }

    private fun pcmPacket() = ByteArray(12 + 960).also { packet ->
        // Ten milliseconds, signed 16-bit big-endian PCM, below the prebuffer threshold.
        for (index in 12 until packet.size step 2) {
            packet[index] = 3
            packet[index + 1] = 0xe8.toByte()
        }
    }
}
