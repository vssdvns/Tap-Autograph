# Validation — Reader Kit / Merchant 0.5 (2026-10-21)

Firmware: PlatformIO build PASS for espressif32@6.10.0, esp32dev, Arduino framework3.20017.241212+sha.dcc1105b, Xtensa8.4.0 toolchain. Adafruit BusIO1.17.2; vendored patched PN5321.3.4 at commit bf9f3f31961474fdbca23cb57d604c7f27269baf. Firmware RAM22,336/327,680 bytes (6.8%); flash298,653/1,310,720 bytes (22.8%). This validates compilation/linking for the chosen profile, not physical operation. No flashing was performed.

Node tests: 15 pass, zero failures. Includes prior eight QR checkout tests, four transcript/protocol tests, hub validation/deduplication/expiry, diagnostic issue retention, and Python-to-HTTP automatic packet delivery with local-token/Origin/Host access checks. Existing QR behavior remains covered.

Python bridge tests: 8 pass. Covers a 19,660-byte/103-chunk synthetic Android-generated packet with one injected link loss, exact reconstructed bytes, publishing before Finish, laptop rejection preventing Finish, stale grant rejection, packet hash corruption, lost Finish acknowledgement, bounded serial-line handling, boot-text/wrong-ID filtering and rejection of remote server URLs.

The integrated software test ran the actual bridge CLI against the merchant HTTP server. It validated and accepted the synthetic packet without manual file import and without creating a checkout session. APDU replay is simulated; it does NOT execute the ESP32 firmware or establish NFC/USB compatibility.

JavaScript syntax checks and Python byte compilation pass. The new browser polling path and Windows .bat launchers have not been visually/manually executed here. The existing merchant handwriting renderer was reused. No physical reader/ESP32/phone, RF antenna placement, electrical wiring, chip-specific USB drivers, UART timing or real link loss has been tested. Board power and mode switches remain unconfirmed. Lab0.4 APK is unchanged and compatible by the TAP1 command contract; physical HCE routing must be tested tomorrow.

The protocol is plaintext and unauthenticated, with no payment processing or checkout binding. Server holds at most one current received autograph for60s/clear; browser until cleared/closed. Recent session/hash keys are retained for2min for duplicate suppression. These keys contain no ink. Bridge writes no packets/APDUs to disk; fixtures are synthetic only.
