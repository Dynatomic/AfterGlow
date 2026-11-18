AFTERGLOW — Bluetooth RGB light controller

Source and manual releases: https://github.com/Dynatomic/AfterGlow

ANDROID DEVELOPMENT PREVIEW
Release v1.0.3 also includes Afterglow-Android-v1.0.3.apk, a native Android
8.0+ (API 26+) development build. Download the APK from the GitHub release page,
transfer/open it on the phone and permit installation from that file source if
Android asks. Install over an existing Afterglow preview without uninstalling
to retain local saved devices; this provided APK uses the same debug signer.
Other developers' independently signed builds cannot update that installation.

The APK is version 1.0.0-android-preview.11 (code 11), debug-signed rather than
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
The published APK is the exact user-tested preview11 build; source organization
and web version/documentation changes do not rebuild or modify its bytes.

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
Copies 1.0.2 and earlier use the previous repository address. Download 1.0.3
manually once from the AfterGlow release page; this copy uses the new address.
This copy is version 1.0.3. Check for updates in the top bar makes one anonymous
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
or HTTPS does not guarantee that getDevices is available. Use Connect or Advanced
setup > Choose a light when lookup/grant/reachability is unavailable.

Advanced setup shows page/API support, profile, light name, link/auth status and
last issue. Identify only reads a picker name without connecting or writing;
a name does not establish brand or protocol compatibility. Expert settings keeps
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
