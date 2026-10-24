# Faster transfer and tap-to-confirm tips / 0.13

Install **both the updated APK and Reader Kit**, then restart the merchant and USB bridge. The merchant needs the TAG2 decoder. No ESP32 firmware reflash or wiring changes are needed. Payments remain simulated.

## Phone UI

The checkout dial shows a simple star rather than a preview of the current autograph, including while sending. Favorite thumbnails crop to their ink bounds with small padding on a white preview surface. This is a display-only crop: the saved original and the transmitted canvas are unchanged.

## Tip workflow

After the first accepted payment tap, select a preset, No tip, or a valid custom tip on the customer display. Wait until the hint says Ready. You can either:

- Press **Confirm total**, then tap for the autograph (or keep holding the phone).
- Lift and retap the phone to **confirm the selected tip and send the autograph** in that presentation.

Selection is stored as a pending tip; it does not change the final order by itself. A fresh reader presentation commits its exact selection revision. Invalid/blank custom input clears the pending choice; No tip must be explicitly selected for tap confirmation. A stale revision, changed order or wrong nonce is rejected. A phone already held in the field does not intentionally confirm an unconfirmed draft tip. Existing final-order/nonce binding and the phone's 45-second window still apply.

## Transfer changes

- **TAG2:** compatible opaque, still scanline memes can use a zlib-compressed one-bit pixel mask on their original grid. The receiver reconstructs scanlines for the existing receipt renderer. TAG2 is selected only if it is smaller than TAG1; handwriting and unsupported artwork retain the vector format.
- The APDU session, 192-byte chunks, CRC, SHA-256, retry validation and publish-before-Finish are unchanged. TAG2 is compact encoding, not encryption.
- Bulk serial reads consume available bytes in batches instead of one Python read call per byte. The existing 115200-baud firmware remains compatible.
- The bridge avoids an extra status APDU when an automatic final offer already reports ready-to-send, shortens idle polling pauses, and reports metadata/read/publish timing separately.
- Still-image receipts no longer wait an artificial 700 ms after drawing. Animated handwriting keeps its playback, followed by a shorter 150 ms settle period. Playback is separate from transport latency.

## Evidence and limits

All 78 Android tests, 22 Node tests and 19 Python tests passed. A synthetic 128×96 meme shrank from **315 bytes / 2 chunks (TAG1)** to **63 bytes / 1 chunk (TAG2)**; every source scanline was recovered. Kotlin-generated TAG2 and reader fixtures are decoded and replayed by Node and Python, including retry/reconnect and acknowledgement checks.

Browser automation tested customer preset selection followed by simulated NFC confirmation without clicking Confirm, custom-tip button confirmation, invalid input clearing the pending choice, explicit No tip, and a complete TAG2 receipt. No browser errors occurred. A local simulated packet-to-complete receipt check took about 280 ms; this excludes real NFC and is not a hardware benchmark.

Actual phone/reader timing remains to be measured. The bridge's printed JSON separates `metadata_seconds`, `read_seconds`, `publish_seconds` and total `seconds`. The 2.5–3 second observed delay may include presentation detection, radio/USB exchange, browser polling and artwork playback. Smaller packets do not imply a proportional reduction in total latency, and no sub-second hardware guarantee is made.
