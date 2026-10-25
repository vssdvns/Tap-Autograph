# v0.16 reader and vector experiment

## Physical baseline

The supplied v0.15 tests used the same 1558-byte TAG3 autograph (9 chunks), 1 ms polling:
7 normal USB runs averaged 1.499 s; 6 Turbo runs averaged 1.1332 s. Turbo's chunk reader
exchange total was about .876 s; USB/host overhead about .092 s. These logs exclude initial
phone discovery and final order offer. No sub-500-ms transfer has been measured.

## Optional bulk SPI

The existing BusIO write_then_read uses an individual SPI transfer for each received byte.
The PN532 driver reads a 255-byte response buffer per exchange. The new optional path uses
one buffered transfer for those data clocks. The same SPI clock (1 MHz), mode, bit order,
command, 0xFF dummy bytes, and uninterrupted chip-select interval are retained. Frame length,
checksum, postamble, response code, and APDU bounds checks are unchanged. No wiring changes.

Bulk reads default off. `start-reader-turbo.bat` explicitly selects old SPI reads;
`start-reader-bulk.bat` enables the new read path. Both use 1 ms polling, 460800 baud and
Base64 USB replies. The SPI command is capability-gated. Returning to Turbo disables bulk
without reflashing. Restarting the board also defaults bulk off.

A block transfer is a candidate optimization, not proof that the old byte loop accounts for
the measured .876 s. Four new measurements distinguish the work:

- pn532_write_seconds: command write and first settling delay.
- pn532_ack_seconds: ACK readiness, ACK frame read and second settling delay.
- pn532_wait_seconds: waiting for the command response after the ACK.
- pn532_read_seconds: copying the response buffer over SPI.

These sum chunk-reply measurements, excluding failed exchanges and other transfer work.
phase_measured_chunks states coverage. The response-wait field includes chip, RF and phone
latency; it is not a pure radio or phone-processing measurement. Tiny remaining differences
between phase sums and exchange_seconds include extra ready checks and frame validation.
Do not add these phase totals to reader_exchange_seconds: they are portions of it.

## Lossless predicted coordinate columns

TAG4 uses the TAG3 column layout, but stores second differences of x/y coordinates before
compression. Each stroke resets its predictor. Reconstructing the original deltas gives
exactly the same quantized coordinates, timing, pressure and tools as TAG1/TAG3. It introduces
no further simplification or image loss. The phone chooses the smallest of TAG1/2/3/4, so a
worse predictor candidate is not sent. Saved originals are unchanged. Updated merchant required.

## Install and test

1. Extract the kit; run setup-tools.bat once for a new folder.
2. Close the bridge and Serial Monitor. Flash once using flash-firmware.bat and COM5.
3. Start the updated merchant. Install the v0.16 APK over the current app, keeping designs.
4. Pick one autograph and keep it fixed. Run five transfers with start-reader-turbo.bat.
5. Stop that bridge, then run five with start-reader-bulk.bat.
6. Copy both full logs, including failed attempts. Compare equal byte/chunk counts.
   compare-transfers.py now groups by bulk mode and prints the phase averages.

The TAG3->TAG4 bytes/chunks changes measure the new encoding effect. Same-packet Turbo vs
Bulk isolates the SPI read change. Old format fallbacks remain supported by the new merchant.
No merchant cache, first-tap artwork prefetch, Wi-Fi download, SPI overclock, or real bank payment.

## Validation

- ESP32 firmware build passed (PlatformIO telemetry disabled).
- Android APK and all 83 Android unit/native-graphics tests passed.
- All 27 Node tests and 30 Python bridge tests passed: 140 automated tests total.
- Host tests under ASan/UBSan checked all 255 SPI buffer lengths for identical bytes,
  command/CS/dummy-byte behavior and guard bytes. Serial command tests covered all
  204 reply lengths, settings validation, handshake, fallback and STATUS bounds.
- Kotlin TAG4 packets replayed through Node and Python with integrity checks and retry.
- Randomized drawings plus maximum reversals/multiple strokes retained TAG1 values.
- The exact Android source used for the APK was compared byte-for-byte with the repository.
- Hardware was mocked for host tests; no physical SPI speedup has been measured here.

Local native-font benchmarks, identical prepared geometry, Samsung fonts may differ:

Artwork | TAG1 bytes | TAG3 bytes | TAG4 bytes | selected bytes | chunks
--- | ---: | ---: | ---: | ---: | ---:
Name Flow | 1806 | 1447 | 1349 | 1349 | 8
Name Classic | 1450 | 1108 | 1077 | 1077 | 6
Name Clean | 1304 | 1052 | 1036 | 1036 | 6
Name Monogram | 866 | 634 | 555 | 555 | 3
Doodle 0 | 357 | 305 | 251 | 251 | 2
Doodle 1 | 520 | 417 | 336 | 336 | 2
Doodle 2 | 338 | 291 | 271 | 271 | 2
Doodle 3 | 425 | 367 | 337 | 337 | 2
Doodle 4 | 453 | 392 | 286 | 286 | 2
Doodle 5 | 481 | 417 | 326 | 326 | 2

Synthetic vector: TAG3 283 bytes / 2 chunks; TAG4 368 bytes / 2 chunks.

The synthetic TAG4 candidate above is larger, so the app retains TAG3 for that example.
Saved artwork is unchanged. Native-font packet improvements are not NFC timing results.
