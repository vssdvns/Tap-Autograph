> GitHub source build note: signing keys are excluded. Debug builds use your local Android debug key and cannot update the previously distributed Lab APK without matching its signing key. Historical release notes below describe packaged APKs.

# Tap Autograph Lab 0.6 — autograph studio

Install this APK as an update over Lab0.4 without uninstalling. Same com.tapautograph.app.lab package and retained development signing key. The first read migrates the selected existing autograph into the new gallery without changing its ID, points, preferences or transaction history. Keep the original non-Lab app installed separately if you use it.

## New features

- Gallery: up to24 named designs; select one for the existing screen/NFC path, rename/delete entries, and choose a receipt-preview design independently. Total gallery point budget120,000; each new saved design must fit the unchanged TAG1 codec. No packet format/reader firmware change.
- Name studio: enter a name (up to32 characters), preview four on-device font-based designs (Flow, Classic, Clean, Monogram), adjust slant, underline and color, then save. Outlines and timing are generated, not personal handwriting or AI inference. Font coverage and exact appearance depend on the device.
- Six original geometric mood doodles: sunglasses, side eye, cat, ghost, approval mark and happy bean. Save/color them like any other design.
- Paper import: choose a photo with Android's selective photo picker or invoke the phone Camera app. Correct EXIF orientation, rotate90°, straighten, trim four sides, adjust ink threshold, speckle cleanup, color and line weight. Bounded skeleton tracing produces vector paths on-device. Photos do not preserve pen order/speed; playback is approximate. Best input is a close, evenly lit photo of dark ink on plain white paper. No handwriting recognition, automatic perspective correction, semantic signature segmentation or arbitrary photographic-sticker support is claimed.
- Receipt art: independent choice/enable switch, black-and-white preview at384 or576 dots, boldness adjustment, and artwork-only PNG export through the system document picker. This does not send anything to a printer/POS or include receipt preferences in NFC packets.

## Try it

Home → Open your gallery. Your previous Lab autograph should appear there. Save a name design/doodle or import paper ink, then select Use for screen & NFC. Choose Share selected design with NFC to use the existing reader kit. Transfers retain the same explicit one-transfer consent and60-second window.

For receipts, choose Use for receipt preview on a gallery item, open Receipt preview & PNG export, select a dot width and print weight, then export the artwork. Real printer support depends on printer/POS software and physical testing.

## Persistence and images

Gallery metadata and extracted vectors are stored in the app's existing private AtomicFile JSON store with backups disabled. The photo picker grants access only to selected content; no broad photo/storage permission is added. Camera capture uses an external camera intent and a FileProvider limited to studio-camera under app cache. Temporary camera files are deleted after decoding or cancellation; an interrupted camera/process can leave a cache file until OS cleanup. Chosen source photos are not copied into the gallery. Decoded source bitmap is held only in memory for editing. Sharing sends vectors only, after the existing explicit consent. Name/photo/doodle creations use generated reveal timing.

Receipts can resolve saved inactive gallery entries by ID. Editing an entry changes its future/past app previews; deleting it can remove the drawing from historical receipt display. Reset clears the gallery, preferences, history and current active design. Renaming leaves geometry unchanged.

## Development

JDK17, SDK35, pinned Gradle8.11.1/AGP8.9.1/Kotlin2.1.20. ./gradlew assembleDebug testDebugUnitTest. Tests now include Robolectric4.14.1 with native Android35 graphics for font paths, paper tracing, EXIF/provider behavior and actual binary PNG output; first run downloads Android test artifacts from Maven. These are test-only dependencies, not shipped in the APK. EXIF handling uses AndroidX ExifInterface1.4.0.

The packaged APK used a retained development key; this repository excludes that key. Never use it for production or real payments. Existing NFC service, merchant QR demo and reader software are separate prototypes; see NFC-PROTOCOL.md and COMPACT-INK-FORMAT.md. See VALIDATION.md for exactly what was checked in this release.

---

## Historical Lab0.4 documentation

# Tap Autograph Lab 0.4 — experimental NFC service + reader simulator

Install the new Lab APK over Lab 0.3. Same com.tapautograph.app.lab package and retained public development key; saved Lab handwriting should remain. Keep the original app installed separately.

## Try today without hardware

1. Open NFC payload lab with a saved autograph.
2. Scroll to Test without hardware and run the simulated reader.
3. Export reader test for laptop (.json), then move that file to your laptop.
4. Extract the updated TapAutograph-Merchant ZIP. With Node20+, run start-merchant.bat on Windows, or node server.cjs.
5. Open http://localhost:8787/nfc-lab. Select the exported JSON. The page verifies the transcript, reassembles and decodes the packet, and replays your autograph. This is file-assisted simulation, not a wireless phone-to-laptop connection.

## Real reader mode (hardware unverified)

In payload lab, enable NFC on the phone, choose Enable one transfer, and confirm. Keep the screen open and phone unlocked. A compatible experimental reader can request chunks for up to 60 seconds. Leaving/backgrounding the screen stops sharing. No need to change default payment wallet. NFC routing and PN532 compatibility still need real hardware checks. No firmware/serial driver is included yet.

This is plaintext autograph sharing with an unauthenticated reader; no card data, payment authorization or checkout integration. Don't use a sensitive signature. See NFC-PROTOCOL.md and VALIDATION.md. The existing QR checkout remains separate and works as before.

## Developer checks

JDK17/SDK35: ./gradlew assembleDebug testDebugUnitTest. Kotlin tests generate app/build/hce-fixtures/reader.json and reader.tai. Node: node nfc/reader.cjs nfc/fixtures/reader.json in the merchant project. npm test runs merchant and protocol tests. Signing keys are excluded from this repository.

---

## Historical 0.3 notes

# Tap Autograph Lab — Android prototype 0.3

This build adds TAG1 compact handwriting encoding/decoding, packet byte sizes, original/decoded replay comparison, and explicit .tai export. See COMPACT-INK-FORMAT.md.

## Install and try

Install TapAutograph-Lab.apk alongside the original app. Package com.tapautograph.app.lab. The original signing key was lost during workspace cleanup. The separate Lab app preserves the original installation and data. Draw and save a new test autograph in Lab, return home, scroll to NFC payload lab, and choose Open payload lab. Compare both previews and optionally export a copy.

This step does not implement NFC or encryption. NFC hardware, Android HCE, transport sessions and authenticated encryption remain future work. Packets contain handwriting, not card credentials or payment authorization. Block counts are planning estimates.

The working v0.2 merchant package is unchanged; its QR launcher opens the original app. To try LAN checkout in Lab, manually paste a pairing link under Connect to merchant.

## Development

Use JDK17, SDK35 and the Gradle wrapper: ./gradlew assembleDebug testDebugUnitTest. This repository uses the local Android debug key. Never use it for production or real payments. Release builds need independent production configuration.

Node reference decoder: node tools/decode-compact.cjs file.tai. No dependencies. Unit tests generate synthetic fixtures under app/build/codec-fixtures; node tools/verify-codec.cjs checks interoperability. See VALIDATION.md.

## Historical v0.2 documentation

Installation/update instructions below describe the earlier app, not the separate Lab build.

# Tap Autograph — Android prototype 0.2

An independent Samsung Wallet-inspired concept by SubhaSai Vinnakota: capture a personal autograph with S Pen or finger and replay the exact strokes during an optional demo checkout.

## New in 0.2: connected merchant checkout

Install the updated APK over version 0.1 to retain your saved autograph. It uses the same package and development signing key.

Run the separately supplied **TapAutograph-Merchant** package on a laptop with Node.js 20 or newer. Open `http://localhost:8787`, choose the laptop's Wi-Fi IPv4 address, and start a checkout. Scan the QR with the phone camera, open Tap Autograph, and confirm the connection. A manual pairing-link field is available under **Connect to merchant** if your camera/browser does not open the app link.

Flow: **pair → simulate card tap on phone → choose tip on laptop → review final amount on phone → share or skip autograph → terminal animation → both receipts**.

Both devices must use the same Wi-Fi. The relay uses local unencrypted HTTP and expiring per-checkout capabilities. Use playful sample handwriting. There is no actual NFC or payment processing.

The original standalone demo remains available and works offline. An active LAN connection is needed only for connected checkout.

## What works

- Native Kotlin / Jetpack Compose wallet with two selectable fictional cards.
- Autograph editor with stylus pressure, historical motion samples, finger support, undo/redo, clear, ink colors, and pen thickness.
- Stroke-by-stroke playback with original relative timing, capped idle gaps, three replay speeds, and reduced motion.
- Local autograph, preferences, and demo history persistence.
- Harbor Coffee checkout: simulated payment, subtotal-based tip choices, validated custom tips, optional autograph, terminal-style animation, and receipt.
- Light/dark/system appearance, handwriting deletion, history deletion, reset, and confirmation dialogs.
- Optional local HTTP connection to a merchant relay, with explicit pairing and autograph-sharing consent. No real payment details, payment processing, Samsung SDK integration, or NFC implementation.

This is an independent concept, not affiliated with Samsung or Google. “Simulate second tap” is a clearly labeled button, not an NFC transaction. Handwriting is personalization, not identity verification or payment authorization.

## Open in Android Studio

1. Extract this project ZIP to a normal folder on your computer.
2. In Android Studio, choose **Open** and select the `TapAutograph` folder containing `settings.gradle.kts`.
3. Use **JDK 17** for Gradle. Android Studio can download a JDK if necessary.
4. Allow Gradle sync and install Android SDK Platform 35 and Build Tools 35.0.0 if prompted.
5. Connect your phone with USB debugging enabled and press **Run**.

The app supports Android 8.0 (API 26) and newer. Physical S Pen testing is intended for the user's Samsung S25 Ultra. S Pen is not required; finger input works on other Android devices.

### Commands

macOS/Linux:

```sh
./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

If using the separately supplied APK on your phone, download it, open it, and allow installation from the app used to open the file when Android requests it. Revoke that permission afterward if desired. The debug APK uses a development signing key and is for this prototype, not Play Store release.

## First demo: approximately one minute

1. Open **Tap Autograph** and tap **Create autograph**.
2. Draw a short playful autograph with your S Pen. Try undo/redo and preview.
3. Save, replay, and try 0.75× and 1.5× speed.
4. Return to the wallet and select a fictional card.
5. Start a demo checkout, simulate payment, select a tip, and continue.
6. Share the autograph or use the simulated second-tap button.
7. Watch the handwriting appear in the terminal panel, then view the receipt.
8. Repeat once using **Finish without autograph**.

## Architecture

- `MainActivity.kt`: Compose theme, navigation, wallet, editor, preview, checkout, receipt, and settings.
- `InkView.kt`: native Android motion input and shared stroke renderer hosted by Compose. Pen events do not recompose the entire screen for every point.
- `Models.kt`: serializable data models, cents-based money calculations, coordinate fitting, playback timeline normalization, and idempotent history updates.
- `WalletViewModel.kt`: StateFlow UI state, retained editor draft, checkout transitions, pairing/polling/reconnect state, and app-private atomic JSON persistence.
- `LiveCheckout.kt`: validated local-only pairing links, protocol models, and coroutine-based HTTP client.
- `LiveScreen.kt`: connected checkout, final-amount review, and autograph sharing/skipping.
- `ModelsTest.kt`: JVM tests for geometry, money validation, playback timeline, serialization, and duplicate transaction prevention.

### Handwriting representation

Each stroke stores normalized x/y positions, relative monotonic timestamps, pressure, and tool type. A fixed original aspect ratio preserves proportions across portrait/landscape rendering. The same drawing surface fits within the editor in either orientation; it does not stretch existing strokes.

Rendering uses anti-aliased, rounded line segments with restrained pressure variation. It is not a handwriting recognition system and does not invent or replace the user's strokes. Long idle gaps are capped only during playback; original timestamps remain stored.

### Persistence and privacy

`wallet.json` is written using Android `AtomicFile` under `noBackupFilesDir`. Backup is disabled. The app uses app-private storage but adds no separate application-layer encryption. Handwriting is not logged. Connected checkout sends it only after explicit sharing to the paired laptop over local HTTP; the relay releases it after completion/cancellation/expiry. The merchant browser can retain its displayed copy until refresh/new checkout/close. Receipts reference a saved autograph ID rather than storing repeated copies. Editing or deleting the saved autograph can make earlier receipts display “Autograph no longer stored.”

Saved content survives app restarts. In-progress drawings and active checkout survive configuration changes through the ViewModel, but unfinished work is not restored after Android kills the process. Cancelled input discards the unfinished stroke.

## Additional connected-device checks

- [ ] Update the previous APK without losing saved handwriting.
- [ ] Scan a real QR with Samsung Camera, open the landing page, then open the app.
- [ ] Verify manual link paste fallback.
- [ ] Pair, simulate the first tap, choose a laptop tip, and verify identical totals on both screens.
- [ ] Share actual S Pen handwriting and compare the terminal's stroke order and proportions.
- [ ] Repeat with no autograph and no tip.
- [ ] Interrupt Wi-Fi and restore it; verify automatic reconnection without duplicated transactions.
- [ ] Cancel or expire a checkout and verify both clients handle it.

## Physical-device verification checklist

- [ ] Draw slow curves, fast strokes, dots, and small letters with S Pen.
- [ ] Test finger-only drawing.
- [ ] Rest a palm while an S Pen stroke is active; additional pointers must not draw. Full hardware palm rejection is not guaranteed, especially if a palm touches before the stylus.
- [ ] Test undo/redo; drawing a new stroke should clear redo.
- [ ] Clear, preview, save, close the app, and reopen.
- [ ] Rotate while editing; completed strokes should remain and proportions should be preserved. An actively cancelled stroke may be discarded.
- [ ] Verify all playback speeds and reduced motion.
- [ ] Enter invalid and negative custom tips; continue must remain disabled.
- [ ] Try autograph checkout and skipping autograph.
- [ ] Start without a saved autograph, create one from checkout, and return to the same purchase.
- [ ] Repeatedly tap checkout actions; history should contain one entry per checkout.
- [ ] Delete handwriting and confirm earlier receipts no longer render it.
- [ ] Try large fonts, TalkBack, landscape, and gesture navigation.
- [ ] Complete the entire flow in airplane mode.

## Next phase

Validate the QR-paired flow on physical devices, then investigate an Android NFC experiment with hardware we control. Existing payment readers and wallet apps do not automatically support this prototype.

## Toolchain

Pinned reproducible baseline: Gradle 8.11.1, Android Gradle Plugin 8.9.1, Kotlin 2.1.20, Compose BOM 2025.04.01, compile/target SDK 35, JDK 17. These are deliberately pinned stable versions, not a claim to be the newest available releases. See `VALIDATION.md` for checks actually performed in the build environment.
