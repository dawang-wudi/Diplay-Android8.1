package com.shilapi.xcertplay.orchestration

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ORA's receiver must release the iPhone's separate A2DP music connection after Wi-Fi handoff.
 * This uses the platform's profile API, never the supplied OEM APK. RFCOMM, pairing, HFP and the
 * Bluetooth adapter remain under their existing owners. See docs/ORA_ANDROID81.md for evidence.
 */
internal class HarmanBluetoothMediaHandoff(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val device: BluetoothDevice,
    private val isCurrent: () -> Boolean,
    private val report: (String) -> Unit,
) : Closeable {
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private var started = false
    private var proxy: BluetoothProfile? = null
    private var lease: BluetoothMediaLease? = null
    private val checkProfile = Runnable {
        if (!closed.get() && isCurrent()) lease?.check()
    }

    private fun log(message: String) {
        runCatching { report("Bluetooth media handoff: $message") }
    }

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, service: BluetoothProfile) {
            main.post {
                if (profile != A2DP_SINK || closed.get() || !isCurrent()) {
                    runCatching { adapter.closeProfileProxy(profile, service) }
                    return@post
                }
                if (proxy === service) return@post
                proxy?.let { runCatching { adapter.closeProfileProxy(A2DP_SINK, it) } }
                proxy = service
                lease = lease ?: BluetoothMediaLease(object : BluetoothMediaLease.Profile {
                    override fun state() = requireNotNull(proxy).getConnectionState(device)
                    override fun disconnect() = invokeDeviceMethod(requireNotNull(proxy), "disconnect")
                    override fun anotherDeviceConnected() = requireNotNull(proxy).connectedDevices.any { it != device }
                    override fun connect() = invokeDeviceMethod(requireNotNull(proxy), "connect")
                }, ::log)
                // Observe confirmation and short OEM auto-reconnect races without polling forever.
                for (delay in longArrayOf(0, 1_000, 3_000, 7_000)) {
                    main.postDelayed(checkProfile, delay)
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == A2DP_SINK) main.post { log("profile service disconnected") }
        }
    }

    fun start() {
        main.post {
            if (started || closed.get() || !isCurrent()) return@post
            started = true
            log("start profile=A2DP_SINK trigger=phone_request_and_tunnel_ready")
            try {
                val accepted = adapter.getProfileProxy(context, listener, A2DP_SINK)
                log("profile bind accepted=$accepted")
                if (accepted) main.postDelayed({
                    if (!closed.get() && isCurrent() && proxy == null) log("profile unavailable timeout=true")
                }, 5_000)
            } catch (error: Exception) {
                log("profile unavailable error=${failureClass(error)}")
            }
        }
    }

    private fun invokeDeviceMethod(service: BluetoothProfile, name: String): Boolean =
        service.javaClass.getMethod(name, BluetoothDevice::class.java).invoke(service, device) == true

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        main.post {
            // Do not remove queued service callbacks: a late proxy still needs to be closed.
            main.removeCallbacks(checkProfile)
            lease?.close()
            lease = null
            proxy?.let { runCatching { adapter.closeProfileProxy(A2DP_SINK, it) } }
            proxy = null
        }
    }

    companion object {
        // BluetoothProfile.A2DP_SINK is hidden in the public SDK; verified in the supplied OEM APK.
        private const val A2DP_SINK = 11
        fun supported(sdk: Int = Build.VERSION.SDK_INT, hardware: String = Build.HARDWARE): Boolean =
            sdk == 27 && hardware.equals("gwmv2_extend", ignoreCase = true)

        internal fun failureClass(error: Exception): String =
            (if (error is java.lang.reflect.InvocationTargetException) error.cause ?: error else error).javaClass.simpleName
    }
}

/** One phone's reversible profile handoff. Android calls are abstracted to test teardown races. */
internal class BluetoothMediaLease(
    private val profile: Profile,
    private val report: (String) -> Unit,
) : Closeable {
    interface Profile {
        fun state(): Int
        fun disconnect(): Boolean
        fun anotherDeviceConnected(): Boolean
        fun connect(): Boolean
    }

    private var closed = false
    private var restoreConnection = false
    private var attempts = 0
    private var unavailable = false

    fun check() {
        if (closed || unavailable) return
        try {
            val state = profile.state()
            report("profile=A2DP_SINK targetState=$state attempts=$attempts")
            if (state != BluetoothProfile.STATE_CONNECTED || attempts >= 3) return
            attempts++
            val accepted = profile.disconnect()
            if (accepted) restoreConnection = true
            report("profile=A2DP_SINK disconnect requested=$accepted attempt=$attempts")
        } catch (error: Exception) {
            unavailable = true
            report("profile=A2DP_SINK unavailable error=${HarmanBluetoothMediaHandoff.failureClass(error)}")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (!restoreConnection) return
        try {
            val state = profile.state()
            if (state != BluetoothProfile.STATE_DISCONNECTED || profile.anotherDeviceConnected()) {
                report("profile=A2DP_SINK restore skipped state=$state")
                return
            }
            report("profile=A2DP_SINK restore requested=${profile.connect()}")
        } catch (error: Exception) {
            report("profile=A2DP_SINK restore unavailable error=${HarmanBluetoothMediaHandoff.failureClass(error)}")
        }
    }
}
