AFTERGLOW FOR ANDROID — DEVELOPMENT PREVIEW

Native Android app, separate from the desktop/browser controller.
Android 8.0 (API 26) or newer; Bluetooth Low Energy hardware required.
Package: com.dynatomic.afterglow
Version: 1.0.0-android-preview.11 (version code 11)

INSTALL AND TRY
The public v1.0.3 release asset is Afterglow-Android-v1.0.3.apk. Its internal
Android version remains the development preview11 listed above.
Transfer the provided debug APK to your Android phone and open it. Android may
ask you to permit installation from that particular file/browser app. This is a
locally debug-signed development build, not a Play Store or production release.
No vendor app, Chrome, PC server, account or Internet connection is required.
Install preview.11 over the existing app without uninstalling it. It uses the same
development signing key. The original preview did not store a light identity;
shortcuts saved by preview.2 or later persist across in-place updates.

Tap Device Option, scan nearby or select a saved light, then tap Connect. Allow
Nearby devices when requested. The main connection panel is read-only and shows
a device name only while the connection is authenticated. All connection actions
are inside Device Option, including Disconnect and pending-connection Cancel.
On Android 8–11 the OS requires location permission for BLE scanning; system
location services may also need to be enabled. The app does not derive location.
Names alone do not establish compatibility. A supported QC channel is checked
and authenticated before lighting commands are allowed. Connecting does not
change the light, and disconnected edits are never replayed on connection.

Device Option beside the connection status opens an opaque full-screen Saved/
Nearby devices page with a Back toolbar. System status/navigation bars stay
visible; safe-area padding/clipping protects the page. Opening it
does not scan or connect. Tap Scan nearby deliberately, then use separate Connect
or Save actions. Save stores an unverified identity locally without changing the
current connection or sending commands. Successful authentication also saves the
device and records successful authentication privately; this does not certify every effect.

Saved entries have local names. Their menu offers Rename (1-40 characters) and
Forget. Rename changes only the local name, not the Bluetooth hardware. Forget
the selected active/pending device also disconnects it and cancels reconnection;
forgetting another saved device leaves the selected link alone.
The list holds up to 32 devices, deduplicated by exact address. Later connections
preserve your chosen names. There is no startup scan/automatic connection or
lighting replay. Choosing an already connected device does not reconnect it.

Close, Back or dismissing the chooser stops discovery only and preserves the
current light/settings. Selecting a different device explicitly switches links.
Failed selections never erase saved entries or mark a device authenticated.
Address, advertised name, local name and previous-authentication status are kept
only in app-private phone storage and excluded from backup/device transfer.
The previous single-light record migrates once; forgetting it will not cause it
to reappear from the old settings. Corrupt/unsupported saved data is rejected.

One explicit Connect attempt permits at most two GATT connections and 30 seconds
total. Only an authentication/notification timeout can trigger the second link,
using the same selected light without rescanning. Services, notifications and
CCHIP authentication are established afresh. No lighting is changed by recovery.
Cancel, Disconnect, another Connect attempt or leaving the foreground cancels it.
There is no startup/background retry. Malformed/rejected authentication stops
this manual authentication recovery. Exhausted timeout
errors show the phase, write/response progress and notification count, without
device addresses or challenge data. This is bounded recovery, not a claim that
the intermittent device/Android cause has been established.

FOREGROUND RANGE RECONNECTION
After a selected light authenticates, unexpected link loss while this app is
visible can open one native Android autoConnect waiting link to that exact light.
An after-authentication transport/write failure can enter the same waiting state;
the failed command and its queued remainder are discarded, never replayed.
No periodic scan, saved-device guessing, worker, Internet or background service
is used. Device Option offers Cancel connection while waiting for range.

When Android reports that the device has returned, fresh services, notifications
and CCHIP authentication are required. Establishment is bounded to 30 seconds,
with one automatic authentication attempt; a failure stops automatic intent.
Success never resumes colors, effects or the app-driven wheel automatically.
Use the light controls deliberately afterward. Actual Android/device range
reconnection reliability remains unverified until phone testing.

Disconnect, Forget the selected device, choosing a new device or closing the app
cancels the waiting intent. Leaving the foreground cancels waiting/automatic
establishment and closes an autoConnect-created link so it cannot reconnect in
the background. Returning to the app never reconnects an already-disconnected
light by itself. An existing authenticated direct link can re-arm future loss
handling when the app becomes visible again.

Color, brightness, eight Scenes, Fade/Jump/Strobe presets and four controller-mic
Music modes are available. Music uses the light controller's microphone, not the
phone microphone. Leaving Music explicitly disables rhythm and controller mic
before the destination. Brightness preserves the selected mode. Turning on at
zero brightness restores a positive requested brightness.

Full color wheel streams paced RGB commands only after explicit selection while
authenticated. An unconnected sample stays a sample after connection; use Resume
to request hardware animation. Leaving the app pauses its wheel scheduler and
cancels remaining lighting work. It never silently resumes on returning. No
background service is used. Controller-native presets/music may continue after
disconnect while browser/app-driven RGB stops at the last submitted color.

The monitor glow and wheel marker are illustrative requested values, not measured
device state. The app contains no Advanced panel, custom packet editing, automatic
updates, Internet permission, microphone permission, telemetry or remote API.

COMPATIBILITY / VERIFICATION
P001 FF10 / FF12 / FF11 was previously verified with CCHIP authentication and a
steady-blue test using a separate desktop hardware client and user observation.
P01B/P026/P02C/P031 signatures are included but remain hardware-unverified.
The initial Android preview connected and worked in a user phone test. This
preview.3 added bounded authentication recovery after intermittent phone timeouts.
Preview.10 groups the current connection status and Disconnect/Cancel action in
a distinct panel at the top of Device Option. It retains preview.9, which moved
every connection action into Device Option, made the main device
panel read-only, disconnected on Forget of the selected device, and added the
foreground-only exact-device range policy described above. The opaque page and
safe-area layout are retained. These changes and reconnection reliability need
phone acceptance. This does not
establish every effect or device as verified.
JVM tests do not establish hardware success.

ARCHITECTURE
See ../ARCHITECTURE.txt for the shared catalog and boundary contract.
Preview.11 separates the exact-channel DeviceCatalog, LightDriver/DaybetterQcDriver,
AndroidBleDiscovery, BluetoothGattTransport/GattSessionLink, LightSession and
SavedDevices/SavedDeviceCodec storage boundaries. MainActivity's view hierarchy is
unchanged. Only Bluetooth LE is registered; Wi-Fi has no implementation.
Native storage schema v2 reads and migrates v1 while preserving saved identities,
local aliases, authentication flags and the last authenticated pointer. Old records
retain an unknown model. A descriptor is assigned only after an exact supported
channel match and successful authentication. Unsupported descriptor metadata becomes
unknown while its saved identity is preserved. No lighting state is persisted.

BUILD / TEST
Use a compatible JDK (17+), Android SDK platform 36 and Build Tools 35.0.0.
Set JAVA_HOME and ANDROID_HOME for your shell, or use a local.properties sdk.dir.
The Gradle 8.13 wrapper downloads checksum-verified Gradle; plugin dependencies
come from Google/Maven Central. Android Gradle Plugin 8.13.2, Kotlin 2.2.21.

Windows: gradlew.bat :app:testDebugUnitTest :app:assembleDebug
APK: app\build\outputs\apk\debug\app-debug.apk
Tests: app\build\reports\tests\testDebugUnitTest\index.html

The 139-case JVM suite covers packet vectors/CRC, strict authentication,
failures/timeouts/stale callbacks, serialization/pacing, coalescing/cancellation,
mode/music transitions, brightness and no connect/disconnected replay. It also
covers bounded manual recovery, foreground exact-device reconnection, discovery,
saved lists/forgetting, exact catalog matching, capability/evidence boundaries,
driver dispatch and backward-compatible descriptor storage migration.

Toolchains, caches, generated build files and debug keys must stay out of source
publication. The normal debug key is outside this source folder. Optionally set
AFTERGLOW_DEBUG_KEYSTORE to your existing private Android debug keystore path.
Keep the signing key if you need future APKs to update the same installation.
A production release needs a separately managed release key; none is created here.

LIVE ACCEPTANCE
The user confirmed this refactored preview11 connected and controlled the known
light. This does not verify every effect, firmware, catalog profile or range-
reconnection scenario. The v1.0.3 release retains the exact tested APK bytes.
Android updates remain manual; the app has no Internet/update-check feature.

LICENSE
Afterglow source: PolyForm Noncommercial 1.0.0, exact text in LICENSE.
Required Notice: Copyright 2026 Dynatomic
Commercial use outside those permissions needs separate approval from Dynatomic.
Third-party runtime/wrapper notices and Apache 2.0 text are included separately.
Protocol facts reference: github.com/DoudGeorges/daybetter-leds/PROTOCOL.md.
No upstream executable protocol implementation is bundled.
