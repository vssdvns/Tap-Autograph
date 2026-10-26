# Tap Autograph

**Your signature, a doodle, or a meme—shared with another tap.**

A working Android and NFC checkout prototype: create artwork once on your phone, complete a simulated payment, then tap again to send it to a customer-facing display.

**Kotlin / Jetpack Compose · Android HCE · ESP32 · PN532 · SPI · Python · Node.js**

**Current version: 0.16.0.** Physical phone-to-reader transfers and the checkout workflow have been tested by the author. Payments are simulated. There is no Google Wallet, Samsung Wallet, Apple Pay, payment-network or commercial-POS integration. Autographs are optional personalization, not payment authorization or identity verification.

## Demo

[Watch the project demo and discussion on LinkedIn](https://lnkd.in/p/gwWEqyTk).

The idea started with the random scribble left on a checkout signature screen. This prototype explores whether that moment could be more personal, while letting customers share saved artwork, draw on the screen or skip it.

## How it works

1. The merchant selects items; a separate customer display updates with the cart.
2. The customer taps an unlocked Android phone to the DIY reader for a **simulated payment**.
3. The phone shows success feedback and starts a 45-second autograph window.
4. The customer selects a tip and a saved autograph. A favorites dial lets them switch designs.
5. A second tap confirms the selected tip and transfers the artwork without another Continue button. The customer display animates it and shows a receipt.

Alternatively, draw in the customer display's signature pad and press Continue, or finish without an autograph. Android launch and lock-screen behavior vary by device; opening the app's tap screen remains the foreground fallback.

## Features

| Component | Implemented |
| --- | --- |
| Android wallet | Overlapping swipeable fictional cards, compact tap panel, success feedback, optional sound/haptics, favorites dial and artwork previews |
| Artwork studio | Handwriting, font-based name designs, original doodles, photo/meme import, crop/rotate and local plain-background removal |
| Merchant demo | Cashier/customer displays, live cart updates, tipping, manual signature pad, NFC artwork animation, skip and receipts |
| NFC transport | Custom HCE/APDU protocol, TAG1–TAG4 compact payload selection, bounded chunk reads, integrity checks and merchant acknowledgement |
| Reader | ESP32/PN532 SPI firmware, Python USB bridge, baseline/Turbo modes and optional bulk-SPI experiment |
| Receipt artwork | Black-and-white previews and PNG export; physical printer integration is future work |

The **autograph data travels over NFC**; the phone does not join the merchant's Wi-Fi for this flow. The ESP32 connects to the laptop by USB. A separate customer-display device needs LAN access to the laptop server; a portable monitor displays a second browser window.

No merchant artwork cache or first-tap artwork prefetch is used. Name designs use fonts, not AI handwriting generation. Background removal targets plain backgrounds, not general semantic segmentation.

## Physical performance

Matched author-run tests used the same **1,558-byte TAG3 autograph, 9 chunks and 1 ms polling**:

| USB mode | Completed runs | Mean transfer time |
| --- | ---: | ---: |
| 115200 baud / hexadecimal replies | 7 | 1.499 s |
| Turbo: 460800 baud / Base64 replies | 6 | 1.133 s |

Approximately **24.4% less transfer time** for that sample. These successful-transfer timings come from the bridge; they are not total checkout time or bank authorization time. Failed attempts are not included in the means.

Later bulk-SPI tests completed differently sized packets in 0.706–1.172 s. They do **not** establish another speedup: the 1,558-byte bulk run took 1.172 s. Most measured reader time was spent waiting for the PN532 response. **Turbo is the demo choice; bulk SPI remains experimental.**

See [measurements and methodology](docs/PERFORMANCE-RESULTS.md).

## Quick start

### Requirements

- NFC-capable Android phone; Android 8 / API 26 minimum. NFC must be enabled by the user.
- Classic ESP32-WROOM-32 development board, PN532 SPI breakout, soldered headers, jumpers and USB data cable.
- Laptop with Node.js 20+ and Python 3.10+ (3.11/3.12 recommended by the kit).
- Android Studio, JDK 17 and Android SDK 35 for a source build.

### Android

Open `android/` in Android Studio, or run:

```bash
cd android
./gradlew assembleDebug testDebugUnitTest
```

On Windows use `gradlew.bat`. The debug package is `com.tapautograph.app.lab`. Local builds use your own debug key and cannot update an APK signed with another key. Preserve saved artwork before changing installations. Distribution signing keys are excluded from GitHub.

Save/select artwork for screen and NFC in Studio, enable automatic autograph sharing for the two-tap flow, keep the phone unlocked and open the tap screen. The optional default-wallet setting applies only to this custom reader's prototype AID; it does not enable real card payments.

### Reader and merchant on Windows

From `reader/`:

1. Run `setup-tools.bat` once in the checkout/folder.
2. Wire the unpowered boards using [the tested hardware profile](reader/HARDWARE-SETUP.md).
3. Close Serial Monitor and any bridge. Run `flash-firmware.bat`, verify the classic ESP32 target, type `FLASH` and select the actual COM port.
4. Run `start-merchant.bat`.
5. Run `start-reader-turbo.bat` and select the ESP32 COM port. COM5 was used by the author; yours may differ.
6. Open **http://localhost:8787** for the cashier. Use **Open customer display** for the paired page on port **8788**. Move it to the second monitor or use the paired LAN link on another browser device.
7. Add items and proceed to payment. Follow the two-tap workflow and hold the phone steady until acknowledgement.

One program can own the serial port at a time. Power LEDs show power, not successful communication. See [diagnostics](reader/DIAGNOSTICS.md).

Standalone merchant: `node merchant/server.cjs`. One Node process serves both ports. The self-contained reader kit includes a synchronized merchant copy under `reader/merchant/`.

### Without hardware

After starting the merchant, run `reader/simulate-reader.bat`, or use the Android NFC payload lab and exported fixtures. These test the software path, not physical NFC. The earlier Wi-Fi/QR demo remains available separately.

## Architecture

Android HCE answers the custom reader's APDUs. ESP32 firmware forwards exchanges over USB to the Python bridge. The bridge reassembles and verifies the packet, publishes it to the Node merchant server and acknowledges the phone after merchant acceptance. The customer browser renders the artwork.

The merchant server owns cart, amount and checkout state for both displays. An order ID and nonce correlate taps; they do not authenticate the merchant or encrypt artwork.

## Documentation

| Guide | Contents |
| --- | --- |
| [Android](android/README.md) | App build and behavior |
| [Reader](reader/README.md) | Firmware, launchers and troubleshooting |
| [Hardware setup](reader/HARDWARE-SETUP.md) | Tested wiring |
| [Dual displays](docs/DUAL-DISPLAY.md) | Pairing, ports and LAN setup |
| [Combined signing](docs/COMBINED-SIGNING.md) | Tip, manual signature and phone tap |
| [Photo and meme import](docs/PHOTO-MEME.md) | Import modes and limits |
| [Performance results](docs/PERFORMANCE-RESULTS.md) | Physical measurements |
| [v0.16 notes](docs/RELEASE-v0.16.md) | Scope and validation |
| [NFC protocol](android/NFC-PROTOCOL.md) | Commands and lifecycle |
| [Artwork encoding](android/COMPACT-INK-FORMAT.md) | Compact packet formats |

Version-specific guides retain their release context. This overview and the v0.16 notes describe the current prototype. Earlier overview: [development archive](docs/archive/README-v0.16-development.md).

## Validation

The full v0.16 build previously passed **83 Android tests, 27 Node tests and 30 Python bridge tests** (140 total), plus firmware compilation and host-side serial/SPI checks. Automated hardware checks use mocks; physical results are separate author-run tests.

```bash
cd merchant
node --test
```

From the repository root:

```bash
python3 -m unittest discover -s merchant/bridge -p 'test_*.py'
```

See component validation files and [SPI validation](docs/READER-BULK.md). The 140-test count records the earlier full v0.16 validation; it is not a new Android/firmware build for this documentation update.

## Limits and next steps

Artwork/merchant exchanges are unencrypted and the reader is unauthenticated. Use sample artwork on a trusted demo setup. This is not a production financial or electronic-signature system. Commercial wallet/POS support needs explicit integration; selecting the wallet role does not expose another wallet's card tokens.

Future work: matched-packet benchmarks, fewer safe NFC round trips, interrupted-transfer reliability, additional Android device testing and physical printer integration. Optimization is currently paused while gathering demo feedback.

Third-party notices and dependency licenses are retained per component. Private keys, local SDK configuration, build products and personal signatures are excluded from this repository.
