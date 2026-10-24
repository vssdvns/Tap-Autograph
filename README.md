# Tap Autograph

An Android and merchant checkout prototype that lets a customer share a saved autograph, generated name design, or playful doodle and watch it animate on the merchant screen.

## Current prototype

- **Android Lab 0.6:** draw signatures, import paper signatures from photos, generate font-based name designs, and save original doodles in a gallery.
- **Wi-Fi checkout:** pair a phone with a laptop, simulate a card tap, select a tip, review the final total, share or skip an autograph, and view an animated receipt.
- **Experimental NFC:** compact vector payloads, Android host card emulation, and an ESP32/PN532 USB reader bridge.
- **Receipt artwork:** independent black-and-white PNG previews at 384 or 576 dots; physical printer integration is future work.

This is an independent prototype. It does not process payments or integrate with Samsung Wallet, Google Wallet, or payment networks. The autograph is optional personalization, not authentication or payment authorization.

## Run the Wi-Fi demo

1. Install Node.js 20 or newer on the laptop.
2. Run `node merchant/server.cjs` (Windows: `merchant/start-merchant.bat`).
3. Open http://localhost:8787 on the laptop and select its Wi-Fi address.
4. Open the Android project in Android Studio, use JDK 17 and Android SDK 35, and build the debug variant.
5. Connect both devices to the same Wi-Fi. In the app gallery, choose **Use for screen & NFC**.
6. Add items on the merchant page and start checkout. Paste the full pairing URL into Lab's connected checkout screen; the current QR landing page opens the original app scheme.
7. Connect, simulate the card tap, choose a tip on the laptop, confirm the total, and share the autograph on the phone.

Local builds use Android's standard debug key. They cannot update previously distributed Lab APKs signed with the original development key; use a separate test installation/device or preserve your data before changing installations.

## Repository map

| Directory | Contents |
| --- | --- |
| `android/` | Kotlin/Jetpack Compose app, signature tools, codec, HCE, tests, protocol docs |
| `merchant/` | Node.js checkout, browser animation, NFC lab, Python USB bridge |
| `reader/` | Self-contained ESP32/PN532 reader kit, firmware, launchers and its merchant copy |

## Validation

The prepared Android 0.6 build previously passed 49 unit tests. Eleven synthetic studio designs were decoded by the merchant NFC decoder with exact packet equality. See each component's `VALIDATION.md` for scope and limitations. Physical hardware results are separate from simulator results.

Run merchant checks with `cd merchant && node --test`. Run bridge checks with `python3 -m unittest discover -s merchant/bridge -p "test_*.py"`. See `android/README.md` for Android build instructions and `reader/README.md` for hardware setup.

## Development notes

The Wi-Fi prototype uses local HTTP, and the experimental NFC transfer is unauthenticated. Use test artwork. Signing keys, local SDK paths, build products and personal signature data are excluded from this repository. Third-party notices and bundled dependency licenses remain with each component.

## Two-tap demo 0.8

The feature branch adds first-tap fictional card acceptance and second-tap autograph sharing with final amount review on the unlocked foreground phone. See `docs/TWO-TAP-CHECKOUT.md` for setup, protocol and limits.

## Current NFC checkout

Reader kit 0.7 connects a new NFC autograph transfer to an explicitly armed merchant order after tip confirmation. See `merchant/NFC-CHECKOUT.md`. The phone needs no Wi-Fi for this mode; payment is simulated on the laptop.

## Next steps

- Test all studio design types through the Wi-Fi checkout on device.
- Improve pairing links for the Lab application.
- Validate the NFC reader across phone placements and larger designs.
- Build a transaction-aware two-tap demo with the phone unlocked and the app open.
- Explore receipt printer output.

## Wallet V2 (0.9)

Wallet/Studio tabs, swipeable demo cards, opt-in automatic autograph sharing and a 45-second window. Android default-wallet selection is available for controlled prototype-reader tests; real payments are not supported. See [setup and device validation](docs/WALLET-V2.md).

## Dual-display merchant demo (0.10)

Cashier on port 8787, paired customer display on port 8788. Live cart updates, customer-side tipping, NFC autograph playback and synchronized receipts. [Setup and controls](docs/DUAL-DISPLAY.md). The original combined demo remains at `/classic`. No Android update or ESP32 reflash is needed.

## Photo & Meme importer (Android 0.10)

Import photos, memes and transparent PNG stickers, remove a plain background locally, and choose Sticker, Outline, Photo dots or Paper ink. Includes crop/rotate, tolerance controls, NFC artwork and receipt previews. [Usage and limitations](docs/PHOTO-MEME.md). Existing merchant/reader versions work unchanged.

## Compact checkout and favorite emotes (0.11)

See [compact checkout](docs/COMPACT-CHECKOUT.md) for the floating tap card, blue payment check, automatic NFC autograph, six-slot favorites wheel, cold-start fix and transfer improvements. Install the updated app and Reader Kit; no ESP32 reflash. Samsung launch behavior still needs on-device verification.

## Tap polish (0.12)

[Payment chime and cursor-style autograph dial](docs/TAP-POLISH.md): once-per-order acceptance feedback, a small draggable selector after payment, optional sound/haptics, and clearer Samsung unlock/retap wording. App-only update; existing reader firmware and bridge remain compatible.

## Faster transfer and simpler checkout (0.13)

See [transfer changes](docs/FAST-TRANSFER.md) for tightly cropped favorite thumbnails, a clean sending dial, tip selection confirmed by the next phone tap, compact TAG2 meme masks, reduced bridge overhead and separate transport/playback timings. Update both APK and Reader Kit; existing ESP32 firmware remains compatible.

## Combined tip and signature screen (0.14)

[Combined signing](docs/COMBINED-SIGNING.md) restores the selected phone autograph in the dial and a preview box above it. The customer display presents tips and a signature pad together: draw and Continue, or select a tip and tap the phone to finish automatically. Update the app and Reader Kit; keep the current ESP32 firmware.
