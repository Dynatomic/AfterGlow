AFTERGLOW FOR ANDROID 1.0.4

Native Android controller; Android 8.0 (API 26) or newer with Bluetooth LE.
Package: com.dynatomic.afterglow
Version: 1.0.4 (version code 13), shown beside Afterglow in the app.
Delivery: artifacts\Afterglow-Android-v1.0.4.apk
This is a locally debug-signed test build, not a Play Store/production release.

INSTALL / USE
Transfer the APK to your phone and open it. Android may ask to allow installation
from that file/browser app. Install over the existing Afterglow app to retain
saved devices; this build uses the existing development signing certificate.
The earlier preview11/v1.0.3 APK artifacts remain unchanged.

Tap Device Option, allow Nearby devices, then Scan nearby or select a saved light
and Connect. Disconnect and Cancel connection remain inside Device Option.
The full-screen Saved/Nearby page retains its opaque layout and system safe areas.
Opening/canceling it preserves the current connection. Choosing the connected
light does not reconnect. There is no Advanced panel or profile-selection step.
Android 8-11 requires location permission (and sometimes system location services)
for BLE scanning; the app does not derive location. Native Android does not use
an in-browser Bluetooth chooser or require Chrome, a PC server or a vendor app.

Discovery lists nearby BLE identities without a service filter (up to 32 rows).
Names only provide hints: known models need verification; other names are labeled
Not yet supported, with explicit Connect available to check their GATT channel.
An unknown name can still match an exact supported QC triplet. Unsupported ELK-
BLED variants are refused without trying a guessed protocol or lighting command.

SUPPORTED SOFTWARE / EVIDENCE
DayBetter QC keeps the five exact service/write/notify triplets:
P001 FF10/FF12/FF11; P01B E01B/A01B/F01B; P026 E026/A026/F026;
P02C E02C/A02C/F02C; P031 E031/A031/F031. Notification subscription and CCHIP
authentication are required before QC lighting. Other names do not bypass this.

ELK-BLEDOB and ELK-BLEDOM have distinct catalog entries and independent encoders.
The raw device name is trimmed/case-normalized and must equal an exact model;
FFF0 service and FFF3 writer are also required. BLEDOB requires no-response writes;
BLEDOM accepts acknowledged or no-response writes according to channel properties.
Saved aliases and MAC prefixes never choose a driver. A saved driver/catalog
mismatch is refused. ELK becomes Ready without QC auth, subscription, clock/timer
sync or connection lighting writes. Local no-response write acceptance does not
prove hardware receipt. ELK microphone modes are disabled; connecting after QC
Music resets the UI selection to Solid without sending lighting.

Power, RGB, brightness, Scenes, Fade/Jump/Strobe and foreground Full color wheel
are implemented for both ELK models. Zero brightness sends Off; positive brightness
restores power without resetting the effect. QC Music uses the controller's mic,
never the phone microphone; leaving Music disables QC rhythm/mic as before.

The user accepted the prior preview11 native interface and verified connection
and control of their known QC light. Earlier P001 desktop evidence covered the
exact signature, authentication and one steady-blue result. Other QC signatures,
individual effects and this update's ELK behavior remain hardware-unverified.
This APK was not run on an emulator or physical phone during its local build.
JVM/build checks do not establish phone, controller or visual acceptance.

SAVED DEVICES / FOREGROUND POLICY
Save stores an unverified identity without connecting or sending commands. Ready
connections also save their validated descriptor. Up to 32 devices and 40-character
aliases are stored privately, excluded from backup. Rename is local. Forget of
the selected/pending/waiting device cancels and disconnects it; stale callbacks
cannot save it again. Forget of an inactive device preserves the active link.
Storage schema v2 migrates v1 identities/aliases. Lighting state is never saved.

Manual QC authentication recovery is bounded to two fresh links within 30 seconds.
After a Ready link is unexpectedly lost while visible, one native autoConnect
waiting link targets that exact address. Returning devices require fresh GATT
validation and driver setup (QC authenticates; ELK becomes Ready). Establishment
is bounded; failure stops automatic intent. No colors, settings, queued commands
or animations replay on connection/reconnection. Cancel, Disconnect, Forget,
another device selection or leaving the foreground cancels range waiting.

Full color wheel streams paced RGB only after explicit user selection/Resume.
Leaving the app pauses the stream and cancels queued lighting. Returning never
resumes it automatically. Native controller presets may continue independently.
The preview/marker show requested values, not measured controller state.
No background service, Internet/phone-mic permission, account, telemetry, remote
API, automatic updater or server is included. Android updates remain manual.

BUILD / TEST
Use JDK 17+, SDK platform 36, Build Tools 35.0.0, Gradle 8.13 (wrapper),
Android Gradle Plugin 8.13.2 and Kotlin 2.2.21. Set JAVA_HOME and ANDROID_HOME.
Set AFTERGLOW_DEBUG_KEYSTORE to the existing private debug keystore for updates;
never replace that key when building an APK for an existing installation.
Windows: gradlew.bat :app:testDebugUnitTest :app:assembleDebug
APK: app\build\outputs\apk\debug\app-debug.apk
JVM report: app\build\reports\tests\testDebugUnitTest\index.html
Tests cover QC vectors/authentication, exact catalog/name/property matching,
separate ELK vectors, no connect replay, paced writes/stale callbacks, capability
gates, cancellation, saved-device aliases/forgetting and foreground reconnection.
Toolchains, keys, local.properties, caches and generated files stay out of source.
See ../ARCHITECTURE.txt for the shared catalog/transport/driver/storage boundaries.

LICENSE / PROTOCOL REFERENCES
Afterglow: PolyForm Noncommercial 1.0.0, exact text in LICENSE.
Required Notice: Copyright 2026 Dynatomic
Third-party notices and Apache 2.0 text are included separately.
Protocol facts: github.com/DoudGeorges/daybetter-leds/PROTOCOL.md;
github.com/8none1/elk-bledob; github.com/smarthomesven/homey-duoco-strip.
No upstream executable protocol implementation is bundled.
