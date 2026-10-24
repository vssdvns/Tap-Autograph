# Tap Autograph Merchant 0.5 — automatic USB reader input

New: bridge/reader.py drives our ESP32/PN532 serial APDU proxy, reassembles chunks with bounded retry/reconnect, sends the verified packet to the local server, and only then requests the phone's completion acknowledgement. The browser at http://localhost:8787/nfc-lab automatically plays new received packets. Lab0.4 on the phone remains compatible. The original QR checkout and manual reader-transcript import remain available.

For the easiest Windows setup use the complete TapAutograph-Reader-Kit ZIP, which includes this merchant folder, firmware, an isolated Python tool setup and launchers. Standalone: install Node20+ and Python3.10+, run python -m pip install -r bridge/requirements.txt, then in separate terminals node server.cjs and python bridge/reader.py --simulate nfc/fixtures/large.json. Open localhost:8787/nfc-lab first. Simulation requires no serial library/hardware; USB mode uses pyserial3.5. For real mode: python bridge/reader.py --list, then --port COM5. Close other serial monitors. No automatic port scanning/opening is performed.

The new local reader endpoints accept loopback requests only and require a loopback Host header. The bridge obtains an ephemeral server token; restart it after server restart. Cross-origin writes/config access are rejected. This is local prototype access control, not merchant identity verification or encrypted transport.

The server holds at most one received autograph in memory for 60 seconds or until Clear. The browser retains its displayed copy until cleared/closed. Recent session/hash keys are retained for two minutes to suppress replay of the same transfer; these keys contain no handwriting. No packet or APDU is written to disk by the bridge. Synthetic fixtures are bundled. Physical firmware/USB/RF behavior and Windows launcher execution await hardware tests. See VALIDATION.md.

---

## Previous 0.4 documentation

# Tap Autograph Merchant 0.4

New: a local NFC reader lab at http://localhost:8787/nfc-lab. It imports the reader JSON from Android Lab 0.4, verifies/reassembles the simulated APDU transfer, and replays the autograph. Run with Node20+: start-merchant.bat on Windows or node server.cjs. Existing QR checkout is unchanged.

On the phone: NFC payload lab → Run simulated reader → Export reader test for laptop. Move that JSON to the laptop and choose it on the lab page. This is a file-assisted simulation, not physical NFC. You do not need to enable live NFC sharing to run the simulation. Imports are accepted only from loopback clients; use localhost on the laptop. No handwriting is retained by the new endpoint. Clear received copy removes the current browser copy.

Developer CLI: node nfc/reader.cjs nfc/fixtures/reader.json. npm test runs the original checkout tests and new protocol/import tests. Synthetic fixtures only are bundled. PN532/ESP32 firmware and USB serial integration are future work; NFC-PROTOCOL.md describes the phone commands. No payment network, card credentials, encryption or merchant authentication is implemented.

---

## Existing QR checkout documentation

# Tap Autograph — Merchant demo 0.2

A real two-device prototype: a laptop acts as the merchant terminal, while the native Android app pairs by QR code and shares handwriting over the same Wi-Fi. All payments are simulated.

## Start on your laptop

Requirements: **Node.js 20 or newer**, a laptop, a phone running **Tap Autograph 0.2**, and both devices on the same Wi-Fi. No npm install or cloud account is required; the QR generator is bundled.

1. Extract this ZIP to a normal folder. Do not run the program inside the ZIP viewer.
2. On Windows, double-click **start-merchant.bat**. On macOS/Linux, open Terminal in this folder and run `node server.cjs`.
3. Keep the terminal window open, then open **http://localhost:8787** on the laptop.
4. If Windows asks about Node.js network access, allow it on your trusted **private** network so your phone can reach the laptop.
5. On the page, choose your laptop's **Wi-Fi IPv4 address**. VPN, Docker, Ethernet, and virtual adapter addresses may also appear; choose the one on the same subnet as your phone.

To use a different port: `node server.cjs --port 8788`, then open http://localhost:8788.

## Complete workflow

1. Add/remove menu items on the laptop and click **Start checkout**.
2. Scan the QR with your phone's normal Camera app. It opens a small local page.
3. Tap **Open Tap Autograph**, then **Connect to this checkout** in the app.
4. If the app does not open, copy the full pairing link, including the part after `#`. In Tap Autograph choose **Connect to merchant → Open connected checkout**, paste the link, then review and connect.
5. Check the merchant/order on your phone, select a fictional card, and press **Simulate card tap**.
6. Choose a tip or no tip on the laptop and click **Confirm total**.
7. Review the final amount on the phone. Choose **Share autograph for this purchase** or **Finish without autograph**. You can create an autograph at this stage and return to the same order.
8. The laptop draws the actual strokes and then displays the receipt. The phone also shows completion and saves a demo transaction.
9. Click **New checkout** on the laptop for another attempt.

Normal updates arrive in approximately one second. The merchant page must stay open to animate and acknowledge completion. If playback gets interrupted, use its **Finish checkout** button.

## Test without a phone

Open **http://localhost:8787/simulator.html** in a second browser tab. Paste the pairing link from the merchant tab. Use **Pair test phone → Simulate card tap**. Choose the tip in the merchant tab, then use **Share generated demo ink** or **Finish without autograph** in the test tab.

This test client uses artificial sample strokes. It is a developer aid, not the customer interface.

## Common connection problems

- **QR page will not load:** verify the chosen Wi-Fi address; both devices must use the same network. The server window must remain open.
- **Guest Wi-Fi:** some routers isolate connected devices. Use a trusted network that permits laptop-to-phone connections.
- **VPN:** a VPN or virtual adapter can lead to the wrong selected address or block local traffic.
- **App will not open from QR:** use the manual paste fallback. Install version 0.2; version 0.1 does not have merchant pairing.
- **Code already paired or expired:** cancel/start a new checkout. A code pairs one phone and expires after 10 minutes.
- **Laptop restarted/server restarted:** all sessions are in memory and are gone. Start a fresh checkout.
- **Node is not found:** install Node.js, reopen Terminal, and try again.
- **Port in use:** use the alternative-port command above.
- **Refresh during receipt:** the handwriting image may disappear because the server already purged it. Purchase totals remain available until session expiry.

## Data behavior and limitations

This is a **local HTTP demo**, not a payment integration. Network traffic is unencrypted; use playful sample autographs on a trusted network. The Android app accepts only explicit local IPv4 HTTP pairing endpoints. It does not silently discover or scan your network.

Pairing uses a random, one-phone capability for the checkout, exchanged for a separate phone token. The merchant browser uses an HttpOnly cookie scoped to its own checkouts. QR pairing credentials are in the URL fragment rather than the landing-page request path. No real card information is exchanged.

The relay holds handwriting only in memory, never in files or a database. It releases its reference on completion/cancellation and removes expired sessions during requests or a cleanup pass (at most 30 seconds after expiry). This is ordinary memory deletion, not a claim of cryptographic memory wiping. The merchant tab keeps its displayed autograph in browser memory until a new checkout, refresh, or close. The Android app retains the user's locally saved autograph until they delete it.

No NFC, Samsung Wallet SDK, Google Wallet API, payment processor, or production authentication is integrated. Replacing QR pairing with NFC is a later experiment; existing terminals will need explicit support.

## Development

- `server.cjs`: dependency-free Node HTTP relay, session lifecycle, server-owned catalog/prices, auth checks, and QR generation.
- `public/app.js`: merchant cart, checkout stages, tip validation, and polling.
- `public/ink.js`: Android-compatible stroke scaling and timed playback.
- `public/pair.*`: camera-scanned landing page and app deep link.
- `public/simulator.*`: browser test phone with generated strokes.
- `vendor/qrcode.cjs`: MIT-licensed QR Code Generator by Kazuhiko Arase.

Run tests with `node --test test/*.test.cjs` (or `npm test`). See VALIDATION.md for actual checks and remaining physical-device work.

## Cashier + customer display

Run `node server.cjs` to serve the cashier on 8787 and the paired customer display on 8788. Start with the cashier pairing link; the customer chooses their tip and sees their autograph replay. See [DUAL-DISPLAY.md](DUAL-DISPLAY.md). The previous combined demo is available at `/classic`.
