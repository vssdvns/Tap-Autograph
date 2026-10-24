# Reader performance experiment 1

This update changes only the reader and laptop bridge. Keep the installed Android app.
No wiring changes, cache, lower image quality, bank integration, or packet format changes.

## Changes

- Firmware emits each APDU response with one bounded UART write instead of per-byte printf.
- PN532 readiness polling can be switched between 10 ms and 1 ms at bridge startup.
- Default remains 10 ms; the existing command settling delays remain in place.
- Timeout uses elapsed millis, including wraparound-safe subtraction, rather than counted sleeps.
- Firmware reports exchange_us around inDataExchange. This includes SPI/driver waiting and
  phone NFC response, not pure over-air time. The bridge reports the sum for chunk requests.
- Serial rate stays 115200 and SPI stays 1 MHz. Chunk size stays 192 bytes.
- Existing firmware is accepted by normal start-reader.bat; profile launchers require capability performance=1.

## Install

1. Extract the updated kit into a new folder. Close the reader bridge and Arduino Serial Monitor.
2. Run flash-firmware.bat, confirm FLASH, and choose the working ESP32 port (currently COM5).
   Keep the board connected during flashing. This is the same classic ESP32 profile.
3. Start the merchant normally. Start start-reader-baseline.bat for the first series.
4. Keep the same phone app, autograph, tip, position, and USB cable. Run 10 transfers and
   save all console lines (including errors) to baseline.txt.
5. Close the bridge, then start start-reader-fast.bat. Run the same 10 transfers and save fast.txt.
   Never run two bridges on the same serial port. Each launcher sets the profile explicitly.
6. Optional summary: .venv\Scripts\python.exe compare-transfers.py baseline.txt fast.txt

The new 10 ms baseline already includes buffered UART output. Compare it to the earlier
logs to assess that change, then compare 1 ms against 10 ms to isolate polling.
The normal launcher uses the firmware's current profile; a hardware reset restores 10 ms.
Use the explicit baseline launcher to restore 10 ms without reflashing.

## Read the measurements

- seconds: metadata through completion reporting; excludes initial discovery, payment/tip,
  and final offer. This retains the previous measurement boundaries.
- read_seconds: all chunk-reading time, including retry/reconnect and progress HTTP calls.
- chunk_roundtrip_seconds: sum of successfully returned chunk APDU round trips (also includes
  malformed returned chunks that trigger retry). Failed exchanges/timeouts are not included.
- reader_exchange_seconds: firmware inDataExchange totals for measured chunk replies.
- usb_host_seconds: matching round trips minus firmware time; includes UART/USB, formatting,
  Windows scheduling and Python. This is not exclusively USB wire time.
- measured_chunks: number of replies with firmware timing. Can exceed chunks after retries.
- packet_format: TAG1 or TAG2 from actual transferred bytes; lets us inspect codec eligibility.
- poll_ms: explicitly selected profile.

Keep failed runs in the logs. A zero retry count on a successful result does not erase earlier
failed attempts. Error messages cannot reliably be converted to a unique-attempt failure rate.
Compare equal byte/chunk counts; do not average different artwork together to claim a gain.

## Baseline supplied by physical testing

| Bytes | Chunks | Runs | Mean seconds |
| ---: | ---: | ---: | ---: |
| 931 | 5 | 2 | 0.923 |
| 972 | 6 | 4 | 1.074 |
| 1514 | 8 | 6 | 1.450 |
| 1994 | 11 | 3 | 1.882 |

No physical speedup or sub-500-ms result has been measured for this update. Do not treat
fixture replay timings as radio benchmarks. If 1 ms adds failures, return to the baseline
profile. Next step depends on measured reader versus USB/host time, and actual packet format.

## Local validation

- PlatformIO espressif32 6.10.0 / Arduino 2.0.17 build succeeded for esp32dev.
- 22 Python bridge tests and 24 Node merchant/protocol tests passed.
- Host-compiled actual firmware command handler passed maximum 204-byte reply and
  invalid TUNE checks under address/undefined-behavior sanitizers (PN532 mocked;
  leak checking disabled because the runner cannot inspect process threads).
- Log summarizer checked with synthetic input; simulated transfers excluded.
- No physical reader is attached here. Phone artwork encoding is unchanged.
