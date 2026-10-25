# v0.15: vector columns and optional fast USB

Payments remain simulated. This update still sends the selected artwork in full on the
second tap; no merchant cache, first-tap prefetch, Wi-Fi signature download, or new hardware.

## Why these changes

Ten physical runs at 10 ms polling averaged 1.8307 seconds for 1994 bytes / 11 chunks.
Ten at 1 ms averaged 1.7988 seconds: only a 1.7% gain. The latter measured roughly
1.11 seconds inside reader exchanges and 0.52 seconds outside them during chunk reads.
The latter is UART/USB/formatting/host scheduling, not exclusively USB wire time.

## Phone format

TAG3 separates the same TAG1 quantized x deltas, y deltas, time deltas, pressure bytes,
and tool bytes into columns before zlib compression. This introduces no additional
quantization or stroke simplification. The existing NFC-only simplification and playback
cap remain as before; saved originals are untouched. Choose the smallest TAG1/TAG2/TAG3
packet, so a candidate that increases bytes is not selected. No caching is involved.

The updated merchant accepts all three formats. Update the merchant before using the new
phone app. The ESP32 treats artwork packets as opaque bytes; APDU framing, chunk size,
SHA-256 checks, final acknowledgement, and order binding remain unchanged.

## USB transport

`start-reader-turbo.bat` selects 1 ms polling and negotiates 460800 baud (four times the
previous UART rate). APDUB replies encode bytes as Base64 instead of hexadecimal, reducing
the data characters for a full 204-byte response from 408 to 272. This is an exact binary
representation, not image compression, encryption, or a fourfold end-to-end speed promise.

The board starts at 115200. The bridge discovers transport capability 2, sends BAUD, reads
the acknowledgement at the old speed, changes the PC speed, and verifies STATUS at the
new speed before using APDUB. Firmware flushes its ACK before switching after 25 ms.
A clean bridge close requests 115200; after three seconds with no valid commands the
firmware also returns to 115200 and clears partial input. Bridge startup waits 3.5 seconds.
Only 115200 and 460800 are accepted. Invalid settings are rejected. No EEPROM changes.

The normal/baseline launchers keep 115200/hex. Fast polling alone remains available.
Use baseline if turbo introduces serial errors; no second flash is needed to change modes.

## Install and compare

1. Extract the kit. For a new folder run setup-tools.bat first. Close the bridge and Serial Monitor.
2. Run flash-firmware.bat once, confirm FLASH, choose the working ESP32 COM port (currently COM5).
3. Restart the merchant from the new kit, then install the APK over the existing app.
   Do not uninstall: the signing key/package are retained and designs should remain.
4. Run start-reader-baseline.bat and repeat one chosen autograph five or ten times.
5. Stop it, run start-reader-turbo.bat and repeat that same autograph equally often.
6. Copy all console output, including failed attempts, to separate text logs.
   compare-transfers.py baseline.txt turbo.txt groups by bytes/chunks/polling/baud/encoding.

New packet_format, serial_baud, usb_encoding, reader_exchange_seconds and usb_host_seconds
help distinguish compression from transport effects. Compare equal-sized packets for the
USB effect. TAG1->TAG3 changes in bytes/chunks show the phone encoding effect.

A sub-500-ms physical transfer has not been established. Fixture/native-graphics timings
are not NFC benchmarks. Do not convert error-message counts into an attempt failure rate.

## Validation and local packet-size results

- Android APK build and 82 Android unit/native-graphics tests passed.
- 26 Node merchant/protocol tests and 26 Python bridge tests passed.
- ESP32 esp32dev build passed with PlatformIO telemetry disabled.
- Host command-handler test under ASan/UBSan checked all response lengths 1..204,
  exact Base64/hex data, baud negotiation, invalid settings, STATUS bounds, and idle recovery.
  Hardware was mocked; leak checking is disabled in this runner.
- Kotlin-generated TAG3 fixture decoded in Node and replayed through Python, including
  injected interruption/reconnect, packet SHA/CRC checks and publish-before-Finish.
- 50 randomized multi-stroke drawings preserved every TAG1 quantized value in TAG3.

Local Android native-graphics rendering of “Subha Sai” (fonts can differ on Samsung):

Artwork | TAG1 bytes | TAG3 bytes | selected bytes | chunks
--- | ---: | ---: | ---: | ---:
Name Flow | 1806 | 1447 | 1447 | 8
Name Classic | 1450 | 1108 | 1108 | 6
Name Clean | 1304 | 1052 | 1052 | 6
Name Monogram | 866 | 634 | 634 | 4
Doodle 0 | 357 | 305 | 305 | 2
Doodle 1 | 520 | 417 | 417 | 3
Doodle 2 | 338 | 291 | 291 | 2
Doodle 3 | 425 | 367 | 367 | 2
Doodle 4 | 453 | 392 | 392 | 3
Doodle 5 | 481 | 417 | 417 | 3

Synthetic vector: TAG1 396 bytes / 3 chunks; TAG3 283 bytes / 2 chunks.

TAG1 and TAG3 comparisons above use identical prepared geometry, not additional
point removal. Packet reductions are not measured physical transfer speedups.
