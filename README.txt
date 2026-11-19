AFTERGLOW — Bluetooth RGB light controller

Source and manual releases: https://github.com/Dynatomic/AfterGlow

ANDROID DEVELOPMENT PREVIEW
Release v1.0.4 includes Afterglow-Android-v1.0.4.apk, a native Android
8.0+ (API 26+) development build. Download the APK from the GitHub release page,
transfer/open it on the phone and permit installation from that file source if
Android asks. Install over an existing Afterglow preview without uninstalling
to retain local saved devices; this provided APK uses the same debug signer.
Other developers' independently signed builds cannot update that installation.

Download the current APK directly:
https://github.com/Dynatomic/AfterGlow/releases/download/v1.0.4/Afterglow-Android-v1.0.4.apk
Release notes and checksums: https://github.com/Dynatomic/AfterGlow/releases/latest

The APK is version 1.0.4 (code 13), debug-signed rather than
production-signed. Android has no Internet permission or update checker: updates
are manual APK downloads/installation. No vendor app, PC server or background
Bluetooth service is required. See android/README.txt for controls and build
instructions. The native source and Gradle wrapper are under android/.

ACCEPTANCE AND LIMITS
The user confirmed both refactored Windows and Android versions connected and
controlled the known light. This is overall acceptance of that device/session,
not certification of every effect, profile, firmware variant or range-recovery
condition. P001 reference evidence is described separately in ARCHITECTURE.txt;
the other four catalog signatures remain documented, not hardware verified.
That acceptance applies to the earlier preview11 build. Version 1.0.4 adds
separate native ELK-BLEDOB/BLEDOM drivers, automatic validated-channel matching
and compact version text while preserving the native interface. It passed local
JVM/build/signature checks; this new APK and ELK controls still need phone testing.
The earlier preview11/v1.0.3 release remains available unchanged.

BROWSER APP
The self-contained dist/index.html is the HTTPS hosting source. There is currently
no active hosted site or GitHub Pages deployment. GitHub provides source code
and manual releases, rather than a running controller. The root index.html is a separate local fallback
with the same light controls and a local-server Quit action.

Use a desktop Chrome setup with Web Bluetooth support. Browser and operating
system support varies. Light control talks directly to the selected device. No vendor app, account,
cloud control service, telemetry, computer microphone or system-audio capture is
used. The optional manual update check contacts GitHub for public release metadata. Device/profile settings stay in this browser's local storage.

VERSION AND MANUAL UPDATES
Copies 1.0.2 and earlier use the previous repository address. Download the current release
manually once from the AfterGlow release page; this copy uses the new address.
This copy is version 1.0.4. Check for updates in the top bar makes one anonymous
request to the public GitHub latest-release API only when clicked. Results and
the manual release link appear in Advanced setup, which opens for the check. There is no
startup check, authorization token, automatic download, executable update or
installation. It handles no public release, rate limits and network/timeouts.
The repository must be public and have a published stable release for this check
to find one. A regular Git tag alone is not a GitHub release.

If a release is available, Open release page lets you review it and manually
download the source ZIP. Extract it to a chosen folder. For the local fallback,
stop the old app before starting the extracted copy. Keep the old copy until you
have confirmed the replacement works. Browser grants/settings are origin-bound.

LICENSE AND COMMERCIAL PERMISSION
Source is available under the exact PolyForm Noncommercial License 1.0.0 in
LICENSE, with the Required Notice in NOTICE. Its permitted noncommercial uses
include changes and distribution under those terms. For a commercial use outside
those permissions, request separate approval from Dynatomic through a GitHub issue:
https://github.com/Dynatomic/AfterGlow/issues

The protocol documentation referenced below is MIT-licensed. Afterglow implements
the documented protocol facts independently; no upstream executable code, third-
party libraries or artwork are bundled. The reference retains its own licensing.

CONTROLLING A LIGHT
Connect opens or reuses a permitted light, retrieves the selected profile's fresh
services/characteristics and authenticates supported DayBetter QC controllers.
Connection/authentication alone never changes lighting or replays disconnected
control edits. Use the controls deliberately after connection is ready.

The color wheel/native picker selects a Solid color. Eight Scenes select static
color/brightness combinations. Effects offers 3/7-color Fade and Jump, 7-color
Strobe, Full color wheel, and four controller-microphone Music modes. Brightness
preserves the selected mode; Speed adjusts animation speed. Leaving Music sends
rhythm-off and microphone-off before the destination mode. Controller-mic Music
needs compatible hardware; it does not listen to computer playback digitally.

Full color wheel has two distinct behaviors:
- The sample display can animate without a connection and sends no commands.
- When authenticated, a deliberate selection or Start light animation streams
  paced RGB commands from this page. Connecting later does not promote a sample
  preview into hardware streaming automatically.

The monitor image, marker and displayed colors illustrate requested settings,
not measured light state. Built-in effect previews use approximate palette/timing
without changing the desired static RGB or adding Bluetooth writes. Music has no
simulated audio response. Hiding the tab pauses browser animations; Resume is
explicit. Manual mode/color changes, Off, disconnect, profile changes and Quit
cancel relevant queued/timed work. An already submitted packet may finish.

DEVICE MEMORY AND TROUBLESHOOTING
Only the last successfully ready device ID/name and selected verified profile are
remembered, never RGB/brightness state. Page opening makes at most one bounded
reconnect attempt using an exact previously granted ID if getDevices is available.
No chooser or recurring retry runs automatically. Grants/storage depend on browser
profile and origin; changing to a new HTTPS origin needs a new first permission.

Live refresh testing reported that saved-device lookup was unsupported in that
browser context. Automatic reconnect is not live-verified. Serving on localhost
or HTTPS does not guarantee that getDevices is available. Use Connect when lookup/grant/reachability is unavailable. Saved-device selection
can also guide you through the chooser when this API is unavailable.

Advanced setup shows page/API support, profile, light name, link/auth status and
last issue. Names alone do not establish protocol compatibility. Expert settings keeps
custom verified UUID/packet profiles and import/export. No guessed protocol
probing is performed. Copy troubleshooting report provides local selectable-text
fallback and redacts known device IDs/token fields; Clear log does not disconnect.

COMPATIBILITY AND VERIFICATION
The P001 FF10/FF12/FF11 signature, CCHIP authentication and steady blue at 25% were
verified on this strip using a separate hardware client and user observation.
P01B, P026, P02C and P031 mappings are implemented but unverified on this hardware.
UI/mock checks are not physical acceptance of every effect or firmware behavior.

Protocol reference:
https://github.com/DoudGeorges/daybetter-leds/blob/main/PROTOCOL.md
No protocol files are fetched at runtime. Custom profiles require an independently
verified service, writable characteristic and packet protocol. Custom templates
support {r}/{g}/{b} with software brightness scaling; custom Music sends one raw
configured packet. Unsupported protocols are not inferred from device names.

LOCAL FALLBACK
Double-click Start controller.cmd. Python's local server runs hidden on
127.0.0.1:8766 and opens Chrome after readiness. Repeated launches reuse the
verified instance. No terminal needs to remain open. A port owned by another
program or another project copy is reported and left alone.

Local Quit app disconnects Bluetooth, cancels pending work and stops its server.
Hosted Quit disconnects/cancels only the page. Updated same-origin controller tabs
are notified. Neither action closes Chrome or powers off the light; controller
presets/music can continue while browser-driven streaming stops at its last RGB.
Normal tab close does not stop the local server. Restart with Start controller.cmd.
Stop controller.cmd is a verified server-only fallback if the tab is closed; it
cannot remotely disconnect an existing tab's Bluetooth link. Older tabs without
the updated shutdown listener need to be closed/disconnected manually.

Manual local lifecycle (Python standard library; no packages required):
  python controller.py --start
  python controller.py --stop
If Chrome is not found automatically, open http://localhost:8766 yourself.
Only selected app assets are served; shutdown is a same-origin capability-checked
POST. Keep root index.html and controller.py together for the local fallback.

DEVELOPER CHECKS
Run from the project folder with Node.js and Python installed. JavaScript suites
use mocked browser/device capabilities and never access a Bluetooth adapter.
The Python lifecycle suite uses isolated temporary loopback ports/processes and
leaves the normal app instance alone.

  node tests/protocol.test.cjs
  node tests/hosted.test.cjs
  node tests/effects.test.cjs
  node tests/spectrum.test.cjs
  node tests/advanced.test.cjs
  node tests/preset-preview.test.cjs
  node tests/full-wheel-display.test.cjs
  node tests/updates.test.cjs
  node tests/architecture.test.cjs
  python tests/lifecycle_test.py

The shared DOM fixture is tests/helpers/dom-fixture.cjs. The two runtime HTML
copies intentionally remain standalone: root local-server lifecycle versus dist
client-only lifecycle. Keep shared control behavior synchronized between them.
INTERNAL ARCHITECTURE
See ARCHITECTURE.txt for catalog, driver, transport, capability and private saved-
device boundaries and migration rules. Version 1.0.3 includes these internal
boundaries while preserving the approved interface and existing controls.

Windows ELK Bluetooth strips
The Windows browser controller has distinct ELK-BLEDOB and ELK-BLEDOM profiles.
Connect can recognize either documented model; Advanced setup also allows an
explicit profile. Padded advertised names are accepted, while the full trimmed
model and its expected Bluetooth channel must match. Unknown ELK-BLED variants
are not assigned guessed commands. The browser chooser shows nearby BLE candidates;
channel validation still happens after selection.
Power, RGB, brightness, Jump/Fade/Strobe, Speed and Full color wheel are available.
Music is unavailable for these profiles. Connecting or reconnecting sends no
lighting settings and does not set the strip clock. Firmware variants need a live
check. The native Android 1.0.4 APK now includes these separate ELK drivers too; it
preserves Device Option and uses native Android BLE permissions and connections.

Nearby Bluetooth device support labels (Windows)
Connect shows the browser's broad nearby BLE chooser.
Chrome owns that popup, so Afterglow cannot add support labels inside it. After
selection, Afterglow shows the selected device name and its support status below
the header. Support remains unverified until the expected channels are checked. Unsupported selections stay visible,
send no light commands and do not replace your last ready saved light. Supported built-in models
are matched automatically when you select a different light.

Automatic built-in profile matching (Windows)
Changing lights automatically matches DayBetter QC, ELK-BLEDOB or ELK-BLEDOM,
regardless of the previously selected built-in profile. The matched profile and
saved device update only after its exact channels are checked and ready (QC also
authenticates). Failed/cancelled selections preserve the last successful record.
Saved reconnect uses the exact recorded device and driver/channel; it does not
silently assign another driver if the device changes. No lighting settings replay.
Custom verified profiles remain an intentional advanced override and are not
replaced automatically from a device name.

Windows device management
Connect opens the nearby Bluetooth chooser. Save device adds the current supported
ready light to a local list (up to 32); successful connections still maintain the
separate last-ready reconnect record. Use Saved devices to reconnect an exact
saved controller. Within the current page, previously ready device handles are reused without
opening another chooser. After reload, if Chrome lacks saved-device lookup, select
that same controller in the chooser; a different Chrome device ID is rejected.
Rename changes only the local label, not the Bluetooth name or driver matching.
Forget removes the selected target and disconnects it if active, cancelling queued
commands and stale reconnects. Forgetting an inactive target leaves another active
light connected. Disconnect keeps the list and leaves the light's setting alone.
The displayed Target identifies which device Rename/Forget will manage.
Advanced setup is on the same toolbar row as Saved devices. It opens a modal popup
with Close and Escape support. Update results and the release link are at its bottom.
Choose a light and Identify only controls and their obsolete helper text were removed.

Same-page saved-device switching
After a controller becomes ready, its BluetoothDevice handle is retained only in
this page's memory, keyed by its exact browser ID. Selecting saved devices reuses
those handles before attempting saved-device lookup or showing a chooser. Every
reconnection still validates its channels/driver (and authenticates QC), with no
lighting replay. Failed cached reconnects discard the handle; Forget and Quit also
remove it. Reloading or closing the page clears these handles, so this does not
change Chrome's saved-device lookup limitation or enable background reconnect.
