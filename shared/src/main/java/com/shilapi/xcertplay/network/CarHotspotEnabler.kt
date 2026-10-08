package com.shilapi.xcertplay.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.provider.Settings
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Turns on the head unit's own Wi-Fi hotspot, for firmware that does not start it by itself.
 *
 * Android reserves this. Two separate facts decide what an ordinary installed app may do:
 *
 * 1. `WifiManager.setWifiApEnabled` is unusable on Android 8.0 and later. Its body only logs
 *    `attempted call to setWifiApEnabled` and returns false; it no longer reaches the service at
 *    all, and the service path it used to reach would start an AP without the DHCP server that
 *    Tethering owns. The supported entry point is tethering.
 * 2. `ConnectivityManager.startTethering` is `@SystemApi` and carries a `TETHER_PRIVILEGED`
 *    annotation, but its service method only calls `ConnectivityManager.enforceTetherChangePermission`.
 *    That enforces the signature permission `TETHER_PRIVILEGED` only when the firmware names a
 *    mobile-hotspot provisioning app; otherwise it enforces the `WRITE_SETTINGS` app-op, which an
 *    ordinary install can hold (Settings -> Special access -> Modify system settings, or
 *    `appops set <package> WRITE_SETTINGS allow`).
 *
 * So the hotspot is started through `IConnectivityManager.startTethering`, reached by reflection,
 * and the caller first checks [capability] to learn which gate this firmware will apply. The
 * stored AP configuration is never read or replaced: this app may not, and the SSID/passphrase
 * the user already saved is the one DiPlay connects to.
 *
 * The reflection targets Android 8.1, where hidden APIs are unrestricted. On Android 9 and later
 * the non-SDK interface blocklist can refuse it; that surfaces as [Outcome.Failed] and the caller
 * falls back to waiting, so a newer device loses the option rather than breaking.
 */
object CarHotspotEnabler {
    /** What this firmware is expected to require, read without touching the AP. */
    data class Capability(
        /** True when the framework names a mobile-hotspot provisioning app. */
        val provisioningAppConfigured: Boolean?,
        val writeSettingsGranted: Boolean,
        val writeSettingsPageAvailable: Boolean,
    ) {
        /**
         * False when the firmware's provisioning app forces the signature permission, which no
         * ordinary install can hold. An unreadable array stays unknown rather than refused, so
         * the attempt itself decides.
         */
        val reachable: Boolean get() = provisioningAppConfigured != true
    }

    sealed interface Outcome {
        /** Tethering reported success. */
        data object Enabled : Outcome

        /** The AP was already on, so nothing was requested. */
        data object AlreadyEnabled : Outcome

        /** The request was accepted but tethering never reported back inside the timeout. */
        data class Unconfirmed(val reason: String) : Outcome

        /** WRITE_SETTINGS is missing; the user has to grant it once. */
        data class PermissionRequired(val reason: String) : Outcome

        /** This firmware needs a system permission the app cannot hold, or lacks the API. */
        data class Unsupported(val reason: String) : Outcome

        /** Tethering reported an error, or the call failed. */
        data class Failed(val reason: String) : Outcome
    }

    fun capability(context: Context): Capability = Capability(
        provisioningAppConfigured = provisioningAppConfigured(),
        writeSettingsGranted = writeSettingsGranted(context),
        writeSettingsPageAvailable = writeSettingsIntent(context, context.packageName) != null,
    )

    /**
     * Starts the hotspot and waits up to [timeoutMillis] for tethering to report back.
     *
     * Blocks on the calling thread and must not run on the main thread. A refusal is returned
     * rather than thrown: the hotspot may already be usable, and the caller's normal hotspot wait
     * remains the authority on whether the session can start.
     */
    fun enable(
        context: Context,
        timeoutMillis: Long,
        isCancelled: () -> Boolean = { false },
        onDiagnostic: (String) -> Unit = {},
    ): Outcome {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "CarHotspotEnabler.enable must not run on the main thread"
        }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }

        val appContext = context.applicationContext
        val capability = capability(appContext)
        onDiagnostic(
            "Car hotspot capability: provisioningApp=" +
                "${capability.provisioningAppConfigured?.toString() ?: "unreadable"} " +
                "writeSettings=${capability.writeSettingsGranted} " +
                "writeSettingsPage=${capability.writeSettingsPageAvailable}",
        )
        if (!capability.reachable) {
            return Outcome.Unsupported(
                "this firmware names a mobile-hotspot provisioning app, so tethering changes " +
                    "need the system permission TETHER_PRIVILEGED",
            )
        }
        if (CarHotspotStatus.isEnabled(appContext) == true) return Outcome.AlreadyEnabled

        val tetherStart = AtomicReference(TetherStart.PENDING)
        val tetherCode = AtomicInteger(TETHER_ERROR_PENDING)
        val rejection = invokeStartTethering(appContext, tetherStart, tetherCode)
        if (rejection != null) {
            val outcome = classify(rejection)
            onDiagnostic("Car hotspot request rejected: $outcome")
            return outcome
        }

        return when (
            awaitTetherStart(
                timeoutMillis = timeoutMillis,
                isCancelled = isCancelled,
                tetherStart = { tetherStart.get() },
                onDiagnostic = onDiagnostic,
            )
        ) {
            TetherStart.STARTED -> {
                onDiagnostic(
                    "Car hotspot tethering started; AP state=" +
                        (CarHotspotStatus.isEnabled(appContext)?.toString() ?: "not exposed"),
                )
                Outcome.Enabled
            }
            TetherStart.FAILED -> Outcome.Failed(
                "tethering reported ${describeTetherError(tetherCode.get())}",
            )
            TetherStart.PENDING -> Outcome.Unconfirmed(
                "tethering did not report back within ${timeoutMillis}ms",
            )
        }
    }

    /**
     * The Settings page where the user grants WRITE_SETTINGS, or null when this firmware has no
     * such page. Some head units ship a trimmed Settings app.
     */
    fun writeSettingsIntent(context: Context, packageName: String): Intent? {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
        val resolvable = runCatching {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }.getOrDefault(false)
        return intent.takeIf { resolvable }
    }

    /**
     * Reads the framework's mobile-hotspot provisioning app list.
     *
     * A two-entry list makes `enforceTetherChangePermission` demand the signature permission
     * TETHER_PRIVILEGED and makes Tethering run carrier provisioning. Returns null when the
     * resource cannot be read, so an unknown stays unknown.
     */
    @Suppress("DEPRECATION")
    internal fun provisioningAppConfigured(
        resources: Resources = Resources.getSystem(),
    ): Boolean? = runCatching {
        val id = resources.getIdentifier(PROVISIONING_APP_ARRAY, "array", "android")
        if (id == 0) return@runCatching null
        resources.getStringArray(id).size == 2
    }.getOrNull()

    private fun writeSettingsGranted(context: Context): Boolean =
        runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    /**
     * Calls `IConnectivityManager.startTethering(TETHERING_WIFI, receiver, false, packageName)`.
     *
     * The package name must be this app's own: `enforceTetherChangePermission` passes it to
     * `Settings.checkAndNoteWriteSettingsOperation`, which rejects a name whose uid does not match
     * the caller. `showProvisioningUi` is false because a firmware with a provisioning app is
     * refused before this point.
     *
     * Returns the failure, or null when the service accepted the request.
     */
    @SuppressLint("PrivateApi")
    private fun invokeStartTethering(
        context: Context,
        tetherStart: AtomicReference<TetherStart>,
        tetherCode: AtomicInteger,
    ): Throwable? {
        return try {
            val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
            val serviceField = ConnectivityManager::class.java.getDeclaredField("mService")
            serviceField.isAccessible = true
            val service = connectivityManager?.let { serviceField.get(it) }
            if (service == null) {
                IllegalStateException("ConnectivityManager is unavailable")
            } else {
                val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        tetherCode.set(resultCode)
                        tetherStart.set(
                            if (resultCode == TETHER_ERROR_NO_ERROR) {
                                TetherStart.STARTED
                            } else {
                                TetherStart.FAILED
                            },
                        )
                    }
                }
                val method = Class.forName("android.net.IConnectivityManager").getMethod(
                    "startTethering",
                    Int::class.javaPrimitiveType,
                    ResultReceiver::class.java,
                    Boolean::class.javaPrimitiveType,
                    String::class.java,
                )
                method.invoke(service, TETHERING_WIFI, receiver, false, context.packageName)
                null
            }
        } catch (failure: Throwable) {
            if (failure is InvocationTargetException) failure.targetException ?: failure else failure
        }
    }

    private fun classify(failure: Throwable): Outcome = when (failure) {
        is SecurityException -> Outcome.PermissionRequired(
            failure.message
                ?: "grant Modify system settings; a firmware that requires TETHER_PRIVILEGED cannot",
        )
        is ReflectiveOperationException -> Outcome.Unsupported(
            "this firmware does not expose the tethering service (${failure.javaClass.simpleName})",
        )
        is UnsatisfiedLinkError, is NoClassDefFoundError -> Outcome.Unsupported(
            "the connectivity service is unavailable on this firmware",
        )
        else -> Outcome.Failed(
            "${failure.javaClass.simpleName}: ${failure.message ?: "no detail"}",
        )
    }

    private const val PROVISIONING_APP_ARRAY = "config_mobile_hotspot_provision_app"
}

internal enum class TetherStart { PENDING, STARTED, FAILED }

/**
 * Waits for tethering to report back.
 *
 * A [TetherStart.PENDING] return means the timeout expired, not that the request failed; the
 * caller reports that separately so a slow firmware is not described as a refusal.
 */
internal fun awaitTetherStart(
    timeoutMillis: Long,
    isCancelled: () -> Boolean,
    tetherStart: () -> TetherStart,
    onDiagnostic: (String) -> Unit,
    nanoTime: () -> Long = System::nanoTime,
    sleepNanos: (Long) -> Unit = TimeUnit.NANOSECONDS::sleep,
): TetherStart {
    require(timeoutMillis > 0) { "timeoutMillis must be positive" }
    val started = nanoTime()
    val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
    while (true) {
        if (isCancelled()) return TetherStart.PENDING
        when (val current = tetherStart()) {
            TetherStart.STARTED, TetherStart.FAILED -> {
                onDiagnostic(
                    "Car hotspot tethering reported $current after " +
                        "${(nanoTime() - started) / 1_000_000}ms",
                )
                return current
            }
            TetherStart.PENDING -> Unit
        }
        val remaining = timeoutNanos - (nanoTime() - started)
        if (remaining <= 0) return TetherStart.PENDING
        try {
            sleepNanos(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(POLL_MILLIS)))
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            return TetherStart.PENDING
        }
    }
}

/** `ConnectivityManager.TETHER_ERROR_*` are `@hide`, so the stable AOSP values are named here. */
private const val TETHER_ERROR_PENDING = -1
private const val TETHER_ERROR_NO_ERROR = 0
private const val TETHERING_WIFI = 0
private const val POLL_MILLIS = 500L

private fun describeTetherError(code: Int): String = when (code) {
    TETHER_ERROR_PENDING -> "no result"
    1 -> "unknown interface"
    2 -> "tethering service unavailable"
    3 -> "tethering is not supported on this firmware"
    4 -> "interface unavailable"
    5 -> "tethering master error"
    6 -> "could not tether the interface"
    7 -> "could not untether the interface"
    8 -> "could not enable NAT"
    9 -> "could not disable NAT"
    10 -> "interface configuration error"
    11 -> "carrier provisioning failed"
    else -> "error $code"
}
