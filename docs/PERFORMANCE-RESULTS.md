# Physical transfer results — v0.15 / v0.16

These measurements were supplied by the project author from the working Android + PN532 + ESP32 + Windows USB bridge setup. They are not emulator timings or a universal payment-terminal benchmark. No real payment was processed.

## Matched USB comparison

Fixed payload: 1,558 bytes, TAG3, 9 chunks, 1 ms readiness polling. No merchant artwork cache or first-tap prefetch.

| Mode | Successful transfer times (seconds) | Mean |
| --- | --- | ---: |
| 115200 baud, hex | 1.463, 1.486, 1.506, 1.511, 1.478, 1.525, 1.524 | 1.499 |
| 460800 baud, Base64 | 1.146, 1.155, 1.138, 1.175, 1.069, 1.116 | 1.1332 |

The reduction in mean transfer time is **24.4%**. Mean measured chunk USB/host overhead fell from approximately 0.4096 s to 0.0921 s (77.5%). Mean reader exchange time stayed around 0.876–0.877 s, so the observed gain is primarily outside the reader exchange.

These are completed-transfer samples, not every attempted tap. The normal log also contained phone-removal and exchange failures. No attempt-level failure rate can be inferred reliably from these excerpts. `retries: 0` in a successful result does not mean the whole session had no failed attempts.

## Bulk-SPI experiment

The subsequent batch changed artwork between transfers. All five completed results had merchant acknowledgement and zero reported chunk retries:

| Bytes | Format | Chunks | Total s | Reader exchange s | Response wait s | SPI read s |
| ---: | --- | ---: | ---: | ---: | ---: | ---: |
| 851 | TAG2 | 5 | 0.706 | 0.4785 | 0.4533 | 0.0105 |
| 963 | TAG2 | 6 | 0.744 | 0.5476 | 0.5173 | 0.0126 |
| 931 | TAG2 | 5 | 0.735 | 0.5133 | 0.4883 | 0.0105 |
| 1126 | TAG2 | 6 | 0.879 | 0.6164 | 0.5863 | 0.0126 |
| 1558 | TAG3 | 9 | 1.172 | 0.8726 | 0.8275 | 0.0190 |

Conditions: 460800 baud, Base64 replies, 1 ms polling, bulk SPI enabled. About 95% of the measured reader exchange is response waiting. This field includes chip, RF and phone latency; it does not isolate any one of them. Phase fields are parts of reader exchange, not extra times to add to it.

The 1,558-byte bulk result is not faster than the previous 1.1332 s Turbo mean, but one run cannot establish a regression either. Different packet sizes cannot isolate the SPI change. **No additional physical bulk-SPI speedup has been established.** A Windows `ClearCommError` access-denied error ended the batch; it is a serial-port failure, not evidence of a packet-decoder failure or damaged NFC module.

TAG4 was not selected in this physical batch. The app chooses the smallest valid TAG1/2/3/4 candidate; a TAG2/TAG3 result is expected when it wins. Local TAG4 byte-size benchmarks in [READER-BULK.md](READER-BULK.md) are not physical NFC timings.

## Timing boundaries

Bridge `seconds` is the tool's reported transfer duration. It excludes initial phone discovery and the final order offer. It does not include all user motion, tip selection, browser animation or a real payment authorization. Publish time is small in these samples; optimizing animation would change perceived completion but not the packet transfer itself.

## Repeatable next experiment

1. Keep the same phone, artwork, packet bytes/chunks, wiring and phone position.
2. Collect at least five Turbo and five bulk runs, interleaving modes if practical.
3. Record every attempt, including failed exchanges, removals and reconnects.
4. Compare mean, median, tail timing and attempt-level completion rate separately.
5. Keep transport timing separate from browser playback and end-to-end user timing.

Current demo choice: Turbo. Further optimization is paused; no sub-500-ms transfer is claimed.
