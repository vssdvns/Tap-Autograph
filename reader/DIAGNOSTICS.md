# PN532 diagnostic firmware

This revision keeps the tap-reader-0.5 serial protocol and the existing SPI pins, AID and APDU limits. It adds three startup attempts, checks the chip identifier, and reports the failed initialization stage in STATUS. The bridge now allows 30 seconds for STATUS because startup retries can exceed its previous four-second deadline.

1. Leave the existing wiring unchanged: PN532 VCC to ESP32 3V3, GND to GND, SCK to D18, MISO to D19, MOSI to D23, SS to D27. IRQ and RSTO are unused. Switch 1 OFF, switch 2 ON. Change wiring or switches only with USB unplugged.
2. Close all reader/serial terminals. Connect USB.
3. Run setup-tools.bat if this is a new extracted folder.
4. Run flash-firmware.bat, type FLASH, then enter COM5 (or the actual port). Wait for a successful upload. This replaces the ESP32 firmware; it does not change the phone app.
5. Run diagnose-reader.bat and enter the same port. Share the printed JSON.
6. If it passes, close diagnostics and start the merchant and reader normally.

Stages:
- spi_begin_failed: host SPI initialization failed.
- firmware_read_failed: no valid PN532 chip-version response. Check mode, signal wiring, power and solder continuity. This cannot distinguish a damaged chip from a connection fault.
- sam_config_failed: chip version was read, but SAM configuration failed.
- retry_config_failed: chip version and SAM configuration succeeded, but polling retry configuration failed.
- ready: all checks passed.

The chip_version field is a packed firmware value, not an electrical measurement. Software cannot measure solder continuity or prove that the board is undamaged. No GPIO voltage tests or automatic interface changes are performed.
