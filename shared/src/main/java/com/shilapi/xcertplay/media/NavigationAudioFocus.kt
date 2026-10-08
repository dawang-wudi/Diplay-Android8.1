package com.shilapi.xcertplay.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** A separate transient request lets car audio policy recognize guidance while media stays alive. */
class NavigationAudioFocus(
    context: Context?,
    private val enabled: Boolean,
    private val report: (String) -> Unit = {},
) {
    private val manager = context?.getSystemService(AudioManager::class.java)
    private val owners = mutableSetOf<Any>()
    private var request: AudioFocusRequest? = null
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        reportSafely("Audio: navigation focus change=$change")
    }

    @Synchronized
    fun acquire(owner: Any) {
        if (!enabled || manager == null || !owners.add(owner) || owners.size > 1) return
        val next = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
            .build()
        request = next
        try {
            val result = manager.requestAudioFocus(next)
            reportSafely("Audio: navigation focus requested usage=12 gain=3 result=$result")
        } catch (error: RuntimeException) {
            reportSafely("Audio: navigation focus unavailable error=${error.javaClass.simpleName}")
        }
    }

    @Synchronized
    fun release(owner: Any) {
        if (!owners.remove(owner) || owners.isNotEmpty()) return
        val previous = request
        request = null
        if (previous != null) {
            try { manager?.abandonAudioFocusRequest(previous) }
            catch (error: RuntimeException) {
                reportSafely("Audio: navigation focus release failed error=${error.javaClass.simpleName}")
            }
            reportSafely("Audio: navigation focus released")
        }
    }

    private fun reportSafely(line: String) { runCatching { report(line) } }
}

/** Keep focus through queued speech, but release it between prompts even if the stream stays open. */
internal class NavigationFocusWindow {
    private var releaseAfterNs = 0L

    fun onPcm(data: ByteArray, offset: Int, length: Int, nowNs: Long,
              queuedBytes: Long, bytesPerSecond: Int, startDelayNs: Long = 0): Boolean {
        // Test signed PCM samples, not packet arrival: persistent silent packets must not hold focus.
        val end = offset + length
        var audible = false
        var index = offset
        while (index + 1 < end) {
            val sample = ((data[index].toInt() and 0xff) or (data[index + 1].toInt() shl 8)).toShort().toInt()
            if (sample > 16 || sample < -16) { audible = true; break }
            index += 2
        }
        if (audible && bytesPerSecond > 0) {
            val bufferedNs = queuedBytes.coerceAtLeast(0) * 1_000_000_000L / bytesPerSecond
            releaseAfterNs = maxOf(releaseAfterNs, nowNs + startDelayNs + bufferedNs + 350_000_000L)
        }
        return audible
    }

    fun shouldRelease(nowNs: Long): Boolean = releaseAfterNs != 0L && nowNs >= releaseAfterNs
    fun released() { releaseAfterNs = 0L }
}
