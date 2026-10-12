package com.shilapi.xcertplay.network

import java.io.IOException

object WirelessStartupPolicy {
    const val HOTSPOT_READY_MILLIS = 15_000L

    /**
     * How long to wait for the car's own access point once the hotspot is being started or is
     * already on. Kept separate from [HOTSPOT_READY_MILLIS] so a slow firmware's access point is
     * not cut off, while a hotspot request that is never going to be answered still fails quickly.
     */
    const val MANUAL_HOTSPOT_WAIT_MILLIS = 60_000L
    const val INTERFACE_POLL_MILLIS = 250L
    const val STABLE_SAMPLES = 3
    const val FIRST_TCP_MILLIS = 30_000L
    const val MAX_STARTUP_RETRIES = 5
    const val STABLE_SESSION_MILLIS = 60_000L
}

enum class WirelessStartupFailure {
    HOTSPOT_NOT_READY, FIRST_TCP_TIMEOUT, HOTSPOT_CONFIGURATION,

    /** The head unit's Android Bluetooth accepts connections that reach no phone. */
    BLUETOOTH_UNUSABLE;

    /** False when no further attempt can change what went wrong. */
    val retryable: Boolean get() = this != HOTSPOT_CONFIGURATION && this != BLUETOOTH_UNUSABLE
}

class WirelessStartupException(val reason: WirelessStartupFailure, message: String) : IOException(message)
