package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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
class Android81AudioTest {
    @Test fun navigationFocusStartsWithSpeechAndReleasesBetweenPromptsWithoutClosingStream() {
        val ready = CountDownLatch(1)
        val acquired = CountDownLatch(1)
        val played = CountDownLatch(1)
        val released = CountDownLatch(1)
        val diagnostics = CopyOnWriteArrayList<String>()
        val sink = AndroidMediaSink(
            context = RuntimeEnvironment.getApplication(),
            navigationAudioFocusEnabled = true,
            onAudioDiagnostic = { line ->
                diagnostics += line
                when {
                    line.startsWith("Audio: ready") -> ready.countDown()
                    line.startsWith("Audio: navigation focus requested") -> acquired.countDown()
                    line.startsWith("Audio: playback") -> played.countDown()
                    line == "Audio: navigation focus released" -> released.countDown()
                }
            },
        )
        val id = AudioStreamId(101, "default")
        val format = AudioFormat(AudioCodecKind.LPCM, 48000, 1, 101, "default")
        try {
            sink.onAudioStarted(id, format, 0)
            assertTrue("Navigation track must initialize: $diagnostics", ready.await(5, TimeUnit.SECONDS))
            assertFalse(diagnostics.any { it.startsWith("Audio: navigation focus requested") })
            // Ten milliseconds of big-endian PCM: too short to fill the prebuffer.
            val packet = ByteArray(12 + 960)
            for (index in 12 until packet.size step 2) {
                packet[index] = 3
                packet[index + 1] = 0xe8.toByte()
            }
            sink.onAudioRtp(id, format, packet, 0)
            assertTrue("Speech must request focus: $diagnostics", acquired.await(5, TimeUnit.SECONDS))
            assertTrue("Short guidance must play: $diagnostics", played.await(5, TimeUnit.SECONDS))
            assertTrue("Open idle streams must release focus: $diagnostics", released.await(5, TimeUnit.SECONDS))
            assertTrue(diagnostics.any { "mapped=NAVIGATION" in it && "requestedUsage=12" in it })
            assertFalse(diagnostics.any { "renderer failed" in it })
        } finally {
            sink.close()
        }
    }

    @Test fun enablingNavigationFocusDoesNotApplyItToMusic() {
        val played = CountDownLatch(1)
        val diagnostics = CopyOnWriteArrayList<String>()
        val sink = AndroidMediaSink(
            context = RuntimeEnvironment.getApplication(),
            navigationAudioFocusEnabled = true,
            onAudioDiagnostic = { line ->
                diagnostics += line
                if (line.startsWith("Audio: playback")) played.countDown()
            },
        )
        val id = AudioStreamId(100, "media")
        val format = AudioFormat(AudioCodecKind.LPCM, 48000, 1, 100, "media")
        try {
            sink.onAudioStarted(id, format, 0)
            sink.onAudioRtp(id, format, ByteArray(972) { 0x40 }, 0)
            assertTrue("Music must play: $diagnostics", played.await(5, TimeUnit.SECONDS))
            assertFalse(diagnostics.any { it.startsWith("Audio: navigation focus requested") })
            assertTrue(diagnostics.any { "mapped=MEDIA" in it && "requestedUsage=1" in it })
        } finally {
            sink.close()
        }
    }

    @Test fun pcmPlaybackInitializesOnPreAndroid10WithoutNewAudioTrackApis() {
        val ready = CountDownLatch(1)
        val diagnostics = CopyOnWriteArrayList<String>()
        val sink = AndroidMediaSink(
            context = RuntimeEnvironment.getApplication(),
            onAudioDiagnostic = { line ->
                diagnostics += line
                if (line.startsWith("Audio: ready")) ready.countDown()
            },
        )
        try {
            sink.onAudioStarted(AudioStreamId(100, "media"), AudioFormat(AudioCodecKind.LPCM, 44100, 2, 100), 0)
            assertTrue("PCM track must initialize on API 27/28: $diagnostics", ready.await(5, TimeUnit.SECONDS))
        } finally {
            sink.close()
        }
    }
}
