"""Read firmware diagnostics without the merchant server or a phone."""
import json
import sys
import time


def main():
    import serial
    port = sys.argv[1] if len(sys.argv) > 1 else input('ESP32 COM port (example COM5): ').strip()
    try:
        with serial.Serial(port, 115200, timeout=.2, write_timeout=2) as device:
            device.dtr = False
            device.rts = False
            time.sleep(2)
            device.write(b'STATUS 1\n')
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                raw = device.readline(2049)
                if len(raw) > 2048:
                    continue
                try:
                    status = json.loads(raw)
                except (ValueError, UnicodeDecodeError):
                    continue
                if not isinstance(status, dict) or status.get('id') != 1:
                    continue
                print(json.dumps(status, indent=2))
                if status.get('diagnostics') != 1:
                    print('Old firmware: flash the diagnostic firmware from this kit first.')
                elif status.get('pn532') is True:
                    print('PASS: PN532 firmware read and configuration succeeded. You can start the merchant and reader.')
                else:
                    print('FAIL at stage: ' + str(status.get('stage', 'unknown')))
                    print('This does not prove board damage. Unplug USB before checking wiring, SPI switches (1 OFF / 2 ON), or solder joints.')
                return 0 if status.get('pn532') is True else 1
            print('No STATUS reply within 30 seconds. Close other serial apps and verify the COM port.')
            return 1
    except serial.SerialException as error:
        print('Serial connection failed: ' + str(error))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
