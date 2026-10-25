# Two-tap demo checkout — 0.8

## Run on Windows

1. Install the supplied Lab 0.8 APK over the previous Lab app. Keep the original app separately if desired.
2. Stop the old merchant and bridge. Extract the updated reader kit to a new directory; run setup-tools.bat there once.
3. Run start-merchant.bat; open http://localhost:8787/ and hard-refresh if an old tab is open.
4. Run start-reader.bat and enter the ESP32 port (COM5 on the demonstrated setup). Existing tap-reader-0.5 firmware works; no reflash is required.
5. Select **Two-tap NFC demo** under Transfer method and start an order.
6. On the unlocked phone, choose a gallery design for screen & NFC, then open **Two-tap NFC checkout** from Home. Enable NFC in settings. Tap **Start two-tap demo**.
7. First tap: hold near the reader until the phone shows **Demo card accepted** with blue feedback and a vibration. No money moves. Remove the phone from the reader.
8. On the merchant choose a tip or No tip and confirm the total.
9. Second tap: hold near the reader. The phone shows the final total. Choose **Share autograph** while holding near the reader, or **Skip autograph**.
10. Keep holding until the phone reports merchant receipt. The merchant animates the autograph and completes its demo receipt. Remove the phone.

The app must remain foreground and unlocked throughout. Leaving the screen, backgrounding/pausing the app, locking the phone, or the three-minute demo deadline cancels phone consent. Autograph transfer has a separate 60-second grant after approval. If a cancelled phone session cannot resume an order, cancel that merchant order and start a new one. A lost first-tap acknowledgement can retry on the same order; if final review times out, remove and retap. Do not move loose wiring during taps; solder headers for reliable operation.

## TAP2 protocol

Uses the existing experimental non-payment AID `F054415041555401` and standard ISO-DEP APDUs. No change to ESP32 framing or NFC driver is necessary; every command remains at most 45 bytes.

| Command | Data | Successful response before `9000` |
| --- | --- | --- |
| SELECT existing AID | Existing SELECT bytes | Version 2 + state byte while Two-tap screen is active; old lab retains version 1 |
| `80 30 00 00 25` | Order UUID (16 bytes), random merchant nonce (16), USD cents (uint32 big-endian), phase (1 byte) | State byte |
| `80 31 00 00 00` | None | State byte |
| `80 32 00 00 01 01` | First-tap merchant acknowledgement | Empty |
| `80 33 00 00 00` | None; only after explicit phone approval | Existing TAP1 ink metadata |
| `80 10 ...`, `80 20 ...` | Existing read/finish protocol | Existing bounded chunks / acknowledgement |

States: 0 ready, 1 waiting for merchant acceptance, 2 demo card accepted, 3 final total under review, 4 ink approved/available, 5 merchant receipt acknowledged, 6 skipped.

The first tap offers the base total; the phone shows demo acceptance only after the bridge successfully records acceptance at the merchant and sends command 32. After tip confirmation, the second tap offers the final total. The order UUID and nonce must match the first tap; a changed final amount after review is rejected. No ink metadata or chunks are available until the customer presses Share autograph. The bridge includes the order/nonce with its packet publication and acknowledges the phone only after merchant acceptance. Packet retries with the same accepted session/hash are idempotent, including after receipt completion. Only one two-tap merchant order can reserve the reader at once.

## Limits

This is a demonstration with fictional card acceptance, not Samsung/Google Wallet integration, identity verification or payment authorization. NFC transport and reader-merchant correlation are not encrypted or authenticated. A nonce binds messages within this prototype but does not prove merchant identity. The phone uses the fixed Harbor Coffee/USD demo presentation. The app does not enable system NFC, launch from a locked phone or unlock the device. The signature is optional personalization.

Phone completion means the merchant server accepted the ink; the merchant browser may still be finishing its animation. This release has no persistent phone receipt history for the two-tap mode.

## Separate cashier and customer displays

Open http://localhost:8787 for the cashier. Use its Open customer display link to pair port 8788, then move that window to your portable monitor. For another browser device, select the laptop Wi-Fi address before copying the link. See [DUAL-DISPLAY.md](DUAL-DISPLAY.md). No ESP32 reflash or phone APK update is required.

## App 0.11 compact checkout

The updated bridge throttles progress events and reports transfer duration. Replace this kit folder for the speed update; keep the existing ESP32 firmware and wiring. See `COMPACT-CHECKOUT.md` for the floating phone card, emote wheel and device test steps.

## 0.13 app compatibility

Use this kit with app 0.13 for compact TAG2 memes and tip selection confirmed by a fresh phone tap. Restart both merchant and bridge after updating. No ESP32 reflash is needed. Read `FAST-TRANSFER.md` for the complete workflow and timing limitations.

## 0.14 combined customer signing

Tips and the signature pad share one customer screen. Draw then Continue, or choose a tip and retap the phone to finish automatically without Continue. See `COMBINED-SIGNING.md`. Replace the kit and restart the merchant/bridge; no ESP32 reflash is required.

## Performance experiment

See [PERFORMANCE-TEST.md](PERFORMANCE-TEST.md) for buffered USB replies, optional 1 ms polling, instrumentation, and the 10-run A/B procedure. Requires one firmware update; keep the current phone app.

## v0.15 turbo transfer

See [TURBO-TRANSFER.md](TURBO-TRANSFER.md) for the updated phone encoding and optional
460800-baud/Base64 USB mode. Run start-reader-turbo.bat after one firmware flash; baseline
mode remains available. Update this merchant before installing the v0.15 phone app.

## v0.16 bulk SPI experiment

See [READER-BULK.md](READER-BULK.md) for optional buffered SPI reads, phase measurements,
and lossless TAG4 coordinate prediction. Compare start-reader-turbo.bat with
start-reader-bulk.bat on the same autograph after updating firmware, merchant and APK.
