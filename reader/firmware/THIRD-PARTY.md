# PN532 driver provenance

Vendored Adafruit PN532 1.3.4, source commit bf9f3f31961474fdbca23cb57d604c7f27269baf, BSD license retained in lib/Adafruit_PN532/license.txt and source headers.
https://github.com/adafruit/Adafruit-PN532/tree/bf9f3f31961474fdbca23cb57d604c7f27269baf

Depends on pinned Adafruit BusIO 1.17.2. ESP32 Arduino framework is supplied by PlatformIO espressif32 6.10.0; its third-party licenses remain with installed packages.

Local PN532 changes:

- Increase internal packet buffer from64 to255 bytes for normal SPI frames. The 192-byte ink chunk has a 204-byte APDU response including session/index/status; it does not fit the upstream64-byte buffer.
- In inDataExchange reject normal-frame length below3 or length+7 beyond the buffer; verify frame sum and postamble before copying.
- Reject nonzero PN532 status, including unsupported continuation, and reject a response beyond the caller buffer instead of truncating silently.
- Limit inListPassiveTarget response wait to1000ms instead of30000ms; configure finite passive activation retries in firmware.

This patched library is used here only for SPI and bounded short APDUs (commands≤45 bytes, responses≤204 bytes). It is not a general extended-frame or I2C/HSU compatibility claim. Physical behavior still requires board testing.
