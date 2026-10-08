package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class NavigationFocusWindowTest {
    private val speech = byteArrayOf(0, 64, 0, -64)

    @Test fun silenceAndPacketHeadersOutsidePcmDoNotClaimNavigationFocus() {
        val window = NavigationFocusWindow()
        val packet = speech + ByteArray(32)
        assertFalse(window.onPcm(packet, speech.size, 32, 1, 0, 96000))
        assertFalse(window.shouldRelease(Long.MAX_VALUE))
    }

    @Test fun bufferedSpeechKeepsFocusUntilItsPlayoutAndTailFinish() {
        val window = NavigationFocusWindow()
        assertTrue(window.onPcm(speech, 0, speech.size, 1_000_000_000, 96000, 96000))
        assertFalse(window.shouldRelease(2_349_999_999))
        assertTrue(window.shouldRelease(2_350_000_000))
        window.released()
        assertFalse(window.shouldRelease(Long.MAX_VALUE))
    }

    @Test fun silentPacketsDoNotKeepDuckingMusicBetweenPrompts() {
        val window = NavigationFocusWindow()
        window.onPcm(speech, 0, speech.size, 1_000_000_000, 960, 96000)
        assertFalse(window.onPcm(ByteArray(4096), 0, 4096, 1_300_000_000, 96000, 96000))
        assertTrue(window.shouldRelease(1_360_000_000))
        assertTrue(window.onPcm(speech, 0, speech.size, 2_000_000_000, 960, 96000))
        assertFalse(window.shouldRelease(2_100_000_000))
    }

    @Test fun shortPromptDoesNotLoseFocusWhileWaitingForPlaybackStart() {
        val window = NavigationFocusWindow()
        window.onPcm(speech, 0, speech.size, 1_000_000_000, 960, 96000, 500_000_000)
        assertFalse(window.shouldRelease(1_600_000_000))
        assertTrue(window.shouldRelease(1_860_000_000))
    }
}
