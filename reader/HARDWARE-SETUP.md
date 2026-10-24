# Hardware bring-up checklist (provisional)

Intended board: classic **ESP32-WROOM-32 development board**, configured as PlatformIO esp32dev. Do not flash this binary/profile to an ESP32-S3/C3/S2 without adapting/rebuilding for that model. Intended NFC board: your PN532 breakout in SPI mode.

## Confirm before wiring

- Read the ESP module shield text and front/back breakout labels.
- Identify the PN532 power input, logic levels, SPI pins and mode-switch/jumper truth table from the actual board documentation. Switch positions vary by module; do not infer them from another brand's photo.
- Confirm whether headers are already soldered. If not, have them soldered securely; loose pins pushed through holes are unreliable.
- Have a USB data cable that fits the ESP32 (many WROOM boards use Micro-USB; verify yours), a breadboard and appropriate male/female jumper wires.
- Keep both boards unpowered while wiring. Avoid metal contact beneath the exposed PCBs/antenna.

## Compiled signal profile — final mapping awaits board photos

| ESP32 signal | Default GPIO | PN532 signal label |
| --- | --- | --- |
| SCK | 18 | SCK / SCLK |
| MISO | 19 | MISO |
| MOSI | 23 | MOSI |
| Chip select | 27 | SS / SSEL / CS |
| Ground | GND | GND |
| PN532 power | **Not selected yet** | Verify the module input requirements first |

ESP32 GPIO uses 3.3V logic; do not feed 5V into its signal pins. The PN532 module's supply voltage and level shifting must be confirmed separately. Do not guess a VCC pin or attach the module to ESP32 VIN based only on the product title. Firmware does not use a separate PN532 IRQ or RESET wire in this SPI profile. An external LED/buzzer/display is optional and not required for the first test.

## Diagnostics

| Observation | First checks |
| --- | --- |
| No COM port | Data cable, USB port, correct board USB-UART driver. Identify the chip before installing a driver. |
| COM port busy | Close Serial Monitor and any other bridge instance. |
| ESP32 connected, PN532 missing | Power off; inspect module power, common ground, SPI mode and signal labels; then restart. |
| Phone found, request declined | Enable sharing again in Lab0.4; keep screen open/unlocked and NFC on. |
| Repeated read failures | Hold steady, try phone antenna position, remove metal obstruction/case; confirm driver/board profile. |
| Packet verified, acknowledgement lost | Laptop has the decoded packet; phone completion was not confirmed. Remove the phone before a new explicit grant. |
| No automatic playback | Open localhost:8787/nfc-lab, confirm updated server and bridge use the same port; stop an older server. |

The reader sends only our experimental autograph AID/APDUs. It does not read credit-card payment data. A matching HCE service on the phone is required; an S50 card/key fob supplied with PN532 will not provide an autograph.
