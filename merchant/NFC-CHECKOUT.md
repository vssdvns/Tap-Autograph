# Tap Autograph Reader Kit 0.7 — NFC checkout

Your existing Lab 0.6 app and flashed ESP32 firmware work with this update. No reflash or new APK is needed.

## Windows: full checkout

1. Stop the old merchant server and reader bridge with Ctrl+C. Extract this entire kit to a new folder.
2. Run setup-tools.bat once in this folder (or use the existing kit environment after replacing its merchant folder).
3. Run start-merchant.bat. Open http://localhost:8787/ on the laptop, not /nfc-lab.
4. Run start-reader.bat and enter COM5 (or the port Device Manager shows).
5. Add items. Select **NFC reader · no phone Wi-Fi** under Transfer method, then **Start checkout**.
6. Click **Simulate card accepted**. Choose a tip or No tip, then **Confirm total**.
7. Click **Receive NFC autograph**. This reserves the reader for this order for 60 seconds.
8. On the unlocked phone choose a gallery design for screen & NFC, open NFC payload lab, enable one transfer and confirm. Keep that screen open and hold the phone near PN532.
9. A newly verified autograph animates in the checkout and the receipt completes. Remove the phone after success. Use New checkout to repeat.

**Finish without autograph** remains available. Wi-Fi mode still supports the original paired-phone checkout. No actual card/payment integration exists; the first card step is a laptop simulation.

The phone does not receive the order or final total over this NFC protocol. Check the total on the merchant screen before sharing. The server associates the next new transfer with the explicitly armed order; it is not a cryptographically transaction-bound signature. Only one checkout may reserve the reader at a time. Existing lab packets and duplicate transfers are never attached retroactively. Simulation packets are marked SIMULATED READER on the receipt.

## Validation

16 Node tests passed, including new checkout integration coverage for fresh-transfer gating, duplicates, reservation conflicts, expiry, skip and receipt cleanup. 8 Python bridge tests passed. The new full checkout has not yet been tested with physical hardware here. The user demonstrated the preceding reader lab successfully on their hardware.

Firmware and hardware instructions remain in HARDWARE-SETUP.md and USB-PROTOCOL.md. The PN532 connections must be mechanically secure; loose pins can interrupt transfers.
