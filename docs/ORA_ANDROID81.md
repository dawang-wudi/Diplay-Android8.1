# Android 8.1 / x86 test build for ORA

This local fork targets the user's mainland-China 2024 ORA Good Cat head unit reported
as Android 8.1.0 on the Harman Intel platform. It is a compatibility test build.
The user confirmed that v8 works on this car, with media and navigation focus
enabled to give the requested separate volume controls. V9 adds selected upstream
music-metadata and main-screen display changes; its new behavior requires a car retest.

Upstream: https://github.com/shihabal3amri/DiPlay
Base commit: `81a0767ac6eaaeec6c934d3270822bd0874a7493` (0.2.10).

## Changes

- Minimum Android API is 27 in the mobile, common and shared modules.
- JNI libraries are compiled for Android 27 and both x86 and x86_64.
- The mobile APK filters out ARM libraries; this artifact is intended for Intel head units.
- Debug package is `com.shihab.diplay.ora81`, version `0.2.10-ora-android81-test10-wheel`, version code 39.
- V9.1 corrects the Chinese connection-setting description to “使用车载自带热点”.
- The launcher, home title, connection notification and iPhone receiver name use `Diplay`.
- The upstream BYD HUD diagnostic rejects pre-Android-9 devices before accessing
  Android-9-only package-signing APIs. It remains restricted to its original BYD firmware.
- The existing compatibility text mentions Android 8.1 as well as Android 9.
- Audio initialization no longer calls `AudioTrack.getAudioAttributes` below API 29.
  Older systems retain the attributes used to construct the track, including usage
  routing after a rejected legacy stream selection. Regression tests cover API 27 and 28.
- The launcher map service rejects all map messages on pre-Android-11 systems and
  guards cleanup callbacks. The unsupported service lifecycle is tested on API 27.
- Legacy hotspot BSSID parsing avoids Android-9-only `MacAddress`. It is tested on API 27.
- Wi-Fi Direct location diagnostics guard Android-9-only location APIs.
- Optional hidden SoftAP builder access uses reflection until the constructor became public.
- Compatibility help points to built-in car hotspot or USB; the removed LocalOnlyHotspot
  selection is no longer suggested.
- CarPlay driving-side behavior is retained: Settings → Display and performance →
  Right-hand drive. Reconnect after changing it. This requests right-side CarPlay controls;
  it does not flip rendered text or video pixels.

Android 8.1 cannot use the upstream Wi-Fi Direct mode (requires Android 10).
Wired USB and the built-in car-hotspot mode remain available, subject to the car's
USB, Bluetooth, Wi-Fi and firmware permissions. BYD-specific vehicle integrations
are outside this ORA compatibility work.

## Third build bug fixes

- Explicit Connect actions retry prerequisites after VPN consent is denied, and show
  the denial on the connection screen. Repeated lifecycle callbacks do not launch
  duplicate permission dialogs.
- Localized connection stages are displayed directly. Previously Chinese waiting
  and connection messages were fed into an English keyword matcher and incorrectly
  shown as "Preparing CarPlay" even after the controller reached USB discovery.
- Closing an auxiliary AirPlay connection leaves the active session and its UI intact.
  Media teardown and feedback are scoped to the owning session; closing an old session
  preserves a replacement session's audio and screen streams.
- Touch input keeps Android pointer IDs in stable HID slots when either finger lifts,
  a new finger joins, or a gesture is cancelled.
- Attaching a recreated UI replays the current active session as well as status, restoring
  session-dependent behavior such as day/night synchronization.
- Both settings screens offer only hotspot modes supported by the current Android
  version. API 27/28 cannot offer or persist Wi-Fi Direct; API 29 retains it.
- Full lint now passes for mobile, common and shared. Fixes include module permission
  declarations, permission-revocation handling, translations and format placeholders.
  Narrow documented suppressions cover intentional vendor/framework compatibility
  calls; no global lint baseline is used. Existing non-error warnings remain.

## Fourth build: IPv4 comparison

The user's October 3 report contains nine authenticated wireless attempts on Harman
Android 8.1. Every attempt advertised one IPv6 endpoint on `uap0`, despite the
interface also having usable IPv4. Discovery and accepted TCP connection counts
stayed at zero, including one wait exceeding five minutes. This identifies the
failure stage but does not establish that IPv6 is the only cause.

Manual car hotspots now select usable IPv4 before scoped link-local IPv6. The
existing controller passes this same selected address to the AirPlay listener,
the iAP2 endpoint sent to the phone, Bonjour publication/discovery and the connect
probe. There is no phone-side static-IP setting to change. IPv6 remains a fallback
when a hotspot has no usable IPv4. Wired USB and Wi-Fi Direct address selection
are unaffected.

Install v4 over v3, leave the phone's Wi-Fi IP configuration automatic, keep the
car hotspot and Bluetooth enabled, then reconnect in DiPlay. Exported reports
should show `addressPolicy=ipv4_preferred` and `wireless endpoint ... family=IPv4`.
A successful test must progress beyond zero discovery/TCP counts and establish
a session; merely changing the address family does not prove CarPlay works.

## Fifth build: navigation focus and route diagnostics

The user reports audible guidance that follows media volume, including after trying
other legacy stream numbers. No verified navigation stream ID for the 2024 ORA
Harman firmware has been found; a vendor-specific number is not assumed.

- Request transient navigation focus with navigation usage and speech content when
  audible navigation PCM arrives. Share one request across overlapping guidance
  tracks, release it after buffered speech and a short tail, and release on teardown.
  Persistent silent packets do not keep focus held between prompts.
- Add a navigation-focus switch, enabled by default in this compatibility build.
  It is independent of the existing media-focus switch and takes effect on reconnect.
  Turning it off restores the previous navigation-focus behavior.
- The navigation stream preview requests the same kind of transient focus.
- Export requested routes, legacy-track fallback, the actual AudioTrack stream,
  focus results, publicly exposed stream constants and readable stream volumes.
  These are diagnostics, not proof of a separate vehicle volume group.

Use navigation stream 0 (automatic) first, enable navigation audio focus and reconnect.
Check whether guidance follows the car's navigation volume and whether media recovers
after each prompt. This may change media ducking; it does not guarantee that the
Harman audio policy will expose navigation-volume control to third-party apps.
The wireless address-selection code and runtime/native libraries remain from v4.

## Sixth build: ORA return-to-car branding

CarPlay's return-to-car entry is now labelled "欧拉" and uses the square ORA mark
from the brand's official website. Saved upstream defaults ("BYD", "比亚迪",
"比亞迪") migrate when loaded; other custom names and custom images are retained.
The icon settings preview now shows the actual default car icon.
Reconnect CarPlay to send the new label and image to the iPhone.

This is a visual update to v5. It retains the v4 IPv4 behavior and the v5 audio
focus controls. It does not add ORA support to the upstream BYD-specific HUD,
cluster or vehicle-data interfaces. See `docs/ORA_BRANDING.md` for image provenance.

## Seventh build: media playback controls and focus ownership

The user installed v6 directly and reports that music pauses immediately after play,
including without navigation running. No v6 car log establishes the precise trigger.
Regression tests reproduce two defects: the media-key session requests focus even
with the focus switch off, and a repeated hardware PLAY is converted to a toggle.

- Keep the Android media session active for steering-wheel controls, but let the
  audio renderer alone request music focus according to the existing switch.
- A new iPhone play transition reuses that same request after permanent focus loss;
  transient guidance loss does not cause an immediate competing request.
- Preserve explicit PLAY and PAUSE commands; genuine toggle keys still toggle.
  Apply the same semantics to the optional local video player.
- Export media-key source/action and iPhone playback transitions alongside audio
  focus diagnostics. Old released media-session callbacks cannot control a new session.

Upgrade over v6. For the initial car test, turn off both "Audio focus" and
"Navigation audio focus", reconnect, and test music play/pause/resume before
enabling either switch separately. Existing settings are not silently reset.
IPv4 selection, ORA branding, native libraries and standalone runtime input remain
from v6. Passing regression tests does not establish that the car-specific issue
is fully resolved; export the new session log if music still pauses.

## Eighth build: ORA Bluetooth music handoff

The October 4 16:31 export from v7 contains granted media-focus requests followed
by iPhone playing=false within about one or two seconds. It contains no media-key
forwarding or focus-loss callback at those failures. Media decoding reports no
write errors. This does not identify who caused the phone to pause.

The supplied factory APK (package com.harman.connectivity.carplay.app, version 8.1.0,
SHA-256 2a433a8046a5ee1fcf82316086e4e71d91ebb3326138240a930dd29f01660fdf)
releases Bluetooth media profiles when taking over CarPlay. DiPlay v7 receives
the phone's disableBluetooth request but only closes its RFCOMM bootstrap after
the Wi-Fi iAP2 tunnel becomes ready. An independently connected A2DP receiver is
therefore a plausible remaining source of play/pause control; it is not proven
by this app-only log.

V8 adds a handoff for Android 8.1 hardware gwmv2_extend only, after both the phone's
request and tunnel readiness. It checks and requests disconnection of only the
selected phone's A2DP_SINK profile, confirms state over a bounded startup window,
and reports unavailable or denied platform calls without failing CarPlay.
It does not change pairing, Bluetooth power, HFP calls or profile priorities.
Teardown attempts to restore a music connection it released, provided no other
phone has taken that connection. Late callbacks cannot affect a newer session.
No OEM code, credentials, libraries or system-signing identity are bundled.

The APK also uses CarAudioManager and Harman source metadata: media deviceType=402
and alternate audio deviceType=404. These are vendor device types, not Android
legacy stream IDs. Native focus requests use privileged/system integration; the
APK alone does not establish that an ordinary installed app can use every API.
V8 keeps the working v7 audio route and focus implementation unchanged.

Keep both audio-focus switches enabled, as the user has confirmed that this gives
the desired separate media/navigation volume behavior. Test a full head-unit
restart without toggling them. Export the new log if playback still pauses;
Bluetooth media handoff records distinguish profile presence, disconnect acceptance,
observed state, and permission failures. Vehicle verification is still required.

## Ninth build: music metadata and display controls

Selected changes from upstream 0.2.11/0.2.12 are ported onto v8 rather than replacing
the ORA integration. The source baseline remains 0.2.10. Upstream PRs: #156, #161,
#162, #172, #178, #179, #181, the settings-menu portion of #191, #194, #211, #228,
and #230. Theme diagnostics are included as a dependency of day/night controls.

- Publish Android media metadata/artwork only when relevant values change; elapsed
  time and playback state keep updating. Preserve artists across partial updates,
  keep the prior art while the next transfer is pending, and clear confirmed failed art.
- Keep the v7 idempotent play/pause commands, controller-scoped media callbacks and
  renderer-owned focus. No media-key focus request is reintroduced.
- Add live main-screen brightness, contrast, saturation and warmth controls, temporary
  original-picture comparison and neutral reset. Neutral defaults preserve the picture.
- Add integer resolution percentages from 30 to 160, migrate legacy saved scales,
  and fall back to 100 when the negotiated canvas exceeds decoder capabilities.
  Resolution above 100 increases processing cost and is not the default.
- Add system/ambient/day/night appearance, configurable ambient threshold/delay,
  and system-mode fallback when a usable light sensor is unavailable.
- Expose independent status/navigation-bar switches, two/three/four-finger settings
  gestures, an in-session menu with cancel restoration, and compact/multi-window layouts.
- Preserve the IPv4 policy, audio renderer, navigation focus, Bluetooth media handoff,
  native libraries, runtime identity input and ORA return icon from v8. The artwork
  receiver and display scaling/layout helpers are the only related shared-path changes.
- Android 8.1 tests additionally cover picture controls, resolution dialogs,
  night-mode persistence/dialogs and independent bar settings.

Install v9 over v8 without uninstalling. Keep both audio-focus switches enabled and
retain the working output-channel selections. Retest cold-start music, a navigation
announcement during music, display changes/reconnection and normal music controls.
Sensor availability, split-screen support and the iPhone's night-mode behavior depend
on the actual head unit and phone; no emulator result establishes those car behaviors.

## Rebuild

Use JDK 25, the included Gradle 9.5 wrapper, SDK platform 37.0, build-tools 36.0.0
and NDK 28.2.13676358. Set `JAVA_HOME`, `ANDROID_HOME` / `ANDROID_SDK_ROOT`,
and `local.properties` for your local tools. Do not copy machine-specific paths.

The source archive excludes runtime identities and Android signing keys, following
the upstream build policy. For the same standalone runtime input, download
`DiPlay-0.2.10.apk` from the upstream v0.2.10 release:

https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.10

The expected APK SHA-256 is
`8c555ce179f30a30b659914f70355245a594f82957424bf44fc532fb1409441e`.

Prepare the selected public release's two assets in a directory outside this source tree:

```powershell
python scripts/prepare_ora81_runtime.py C:/downloads/DiPlay-0.2.10.apk C:/build-inputs/ora81-runtime
$env:DIPLAY_AUTH_ASSETS_DIR = 'C:/build-inputs/ora81-runtime'
.\gradlew.bat :mobile:lintDebug :common:lintDebug :shared:lintDebug :mobile:assembleStandaloneDebug :shared:testDebugUnitTest :common:testDebugUnitTest
```

Output: `mobile/build/outputs/apk/debug/mobile-debug.apk`.
Without the explicit asset input, `assembleDebug` produces a source-only APK which
cannot serve as a standalone CarPlay receiver. A locally rebuilt APK uses your own
debug signing certificate; updating this delivered test package requires the same
certificate. The signing keystore is deliberately excluded.

The APK retains the upstream experimental, extractable accessory identity and is
not Apple-certified. See the original README and third-party notices for provenance,
licenses and the limits of compatibility after iOS updates.

## Tenth build: controls before the first phone track change

The user reports that after a car reboot, steering-wheel track changes only work
after changing tracks once on the iPhone, whether or not music initially plays.
The code had no media session before the first media audio stream and confused
initial song-only metadata with an explicit paused status. These are reproducible
initialization gaps. On 2026-10-05 the owner confirmed the delivered v10 build
works on the ORA Good Cat Harman head unit, including steering-wheel track changes.

- Create and activate the Android media session when AirPlay connects. Publish
  playback actions and initial state before activation, without requesting focus
  or sending any automatic play, pause, or track-change command to the phone.
- Retain whether iAP2 actually supplied playback status. Until then, use the media
  stream state even if a title or position arrived first. An explicit phone pause
  still overrides an open stream. Status-only changes do not republish artwork.
- Forward media keys delivered to the foreground CarPlay window as well as keys
  delivered through MediaSession. Consume each down/up pair once, ignore repeats,
  and reject callbacks belonging to a previous controller.
- Log control registration and state transitions without logging every position
  update. The existing audio renderer remains the sole media-focus owner.

Regression coverage includes connection before audio/metadata, playing and paused
states, song-first updates, foreground and media-session keys, controller replacement,
duplicate connection notifications, release, and enabled/disabled audio focus.
Upgrade over v9.1 and leave both existing audio-focus switches enabled. After a car
reboot, connect without touching the phone's music controls and try next/previous
on the wheel, both with music playing and with music initially paused.

## Validation scope

### Eleventh build: allow the system hotspot to finish booting

Version `0.2.10-ora-android81-test11-hotspot` (code 40) addresses automatic
startup in a restored compact/PiP window racing the head unit's hotspot startup.
The launcher no longer blocks connection with an immediate hotspot-off dialog,
and the controller no longer rejects a temporarily disabled system AP.

The manual hotspot manager waits on its connection worker for up to 120 seconds,
checking every 500 ms. It waits for both an enabled AP (when the firmware exposes
that state) and a usable interface address. A retained address on a disabled AP
does not count as ready. Firmware that hides AP state keeps the existing interface
fallback. Live AP configuration is read after readiness, rather than cached during
early boot. An already ready hotspot connects without an added fixed delay.

The screen explains that connection will continue automatically. A timeout uses
the existing bounded-backoff reconnect path; closing the session interrupts the
wait. This does not enable, disable, or rewrite the system hotspot or change the
working audio-focus and routing behavior. Startup and readiness transitions are
logged without per-poll logging or hotspot passwords.

Regression coverage includes a 90-second delayed hotspot, delayed interface
address, stale address while AP is off, hidden state APIs, timeout, cancellation,
interrupts, compact automatic launch, manual connect and invalid saved settings.
On 2026-10-05 the owner confirmed the delivered v11 hotspot-startup fix passed
real-car validation on the ORA Good Cat Harman head unit. This is owner-reported
hardware validation, in addition to the automated and emulator checks below.

Gradle build, Android lint (no errors), and the shared/common unit tests pass.
An Android 8.1.0 x86_64 emulator verifies installation, startup, Simplified Chinese
settings, and persistence of the right-hand-drive switch across process restart.
See the installation note supplied with the APK for the final validation record.

The user's real-car reports confirm wireless operation, separate navigation/media
volume with both focus switches enabled, and successful v8 use after the handoff fix.
On 2026-10-05 the owner also confirmed the delivered display additions and v10
steering-wheel fix work in the car. This confirmation does not establish compatibility
with other vehicles or firmware, or a comprehensive test of every protocol feature.
The source archive includes the original GPL-3.0 license,
DiAuto AGPL-3.0 license and third-party notices.

### Twelfth build: Siri replies through the navigation output

Version `0.2.10-ora-android81-test12-siri` (code 41) adds a compatibility option
for head units without a usable assistant output. The owner originally reported
that Siri recognized speech but its replies were inaudible. On 2026-10-05, after
testing this build, the owner confirmed the Siri audio compatibility change works
on the ORA Good Cat Harman head unit. This is owner-reported vehicle validation.

The mobile ORA build enables "Siri follows navigation audio" by default, including
upgrades with no saved value. Users can turn it off to restore assistant routing.
The setting is under Audio routing and takes effect on reconnect. Other build
targets retain their previous default. Explicitly saved preferences take priority.

Only the playback classification of `speechrecognition` changes. Siri inherits
the selected navigation output and shares its temporary focus request, acquired
when audible PCM arrives and released after the buffered reply and a short tail.
Music keeps its existing media-focus owner. Navigation and Siri can overlap without
one closing the other's focus request. Microphone capture, stream identifiers,
telephone routing, existing audio settings and hotspot readiness are unchanged.
The saved compatibility setting and actual Siri playback mapping appear in the
exported diagnostics.

Keep both existing audio-focus switches enabled and reconnect after upgrading.
Verify a spoken Siri answer with music playing and stopped, return to music after
each reply, navigation-volume control, and the same behavior after a car reboot.
V12 is promoted to the latest regular release with the same APK, version code
and source tag as the initial test release. The confirmation covers this reported
Siri issue on the owner's vehicle; it does not establish a comprehensive test of
every Siri command, phone call, restart scenario or other firmware. The automated
and emulator records remain separate evidence. Existing v12 users need not reinstall.
