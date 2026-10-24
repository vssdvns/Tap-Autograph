# Two sides of the counter

Merchant demo 0.10 adds separate cashier and customer surfaces sharing one in-memory server state. Android remains 0.9; no APK reinstall or ESP32 reflash is required. The updated Reader Kit contains both surfaces and the same working NFC bridge/firmware.

## Start on Windows

1. Stop the old merchant server. Extract the new Reader Kit into a fresh folder. Run setup-tools.bat once for that folder, then start-merchant.bat and start-reader.bat (choose the working ESP32's COM port).
2. Open http://localhost:8787 on the laptop. This is the cashier: menu, quantities, order status, cancel and new order.
3. Under Connect the customer screen, choose Portable monitor / this laptop and click Open customer display. The paired link opens port 8788 in a second tab. Drag that tab into its own browser window, move it onto the portable monitor, and use F11 or the fullscreen button. On Windows, set the external display to Extend rather than Duplicate.
4. For a separate browser-capable tablet or computer, choose the laptop's Wi-Fi address in the cashier dropdown. Copy the resulting paired link to the device on the same local network. Allow Node through Windows Firewall on the private local network if prompted. Both devices must be able to reach the laptop; guest Wi-Fi/client isolation can block this. The phone transferring an autograph over NFC does not need this Wi-Fi connection.
5. Add products at the cashier. The customer screen updates within roughly one polling interval (250 ms plus network latency). Proceed to payment freezes the order and reserves the NFC reader.
6. Customer taps their Tap Autograph phone to the reader. This remains a fictional payment. The customer chooses No tip, 15%, 20%, or a custom dollar tip on their own display, then confirms the total. The cashier sees that total and the new stage.
7. The customer taps again (or keeps the phone near the reader), and automatic autograph mode transfers the selected design. The customer screen animates it, completes the checkout and shows the receipt. The cashier sees completion and chooses New order to clear both screens.
8. The customer can Finish without autograph. The cashier can cancel an active order, or finish an autograph checkout if the display is unavailable.

The customer pairing link is specific to the cashier's browser cookie and lasts six hours; keep the cashier open. It follows new orders on that register without rescanning a QR code. Browser reloads restore current server state. Restarting the server resets carts/orders and invalidates links; reopen the cashier and pair the display again. Autographs are discarded from the server after completion, so a customer-page reload can restore receipt totals but not the deleted handwriting image.

A portable monitor is a display for the laptop browser and needs no separate software or networking. Smart displays must be able to show the paired web page or an appropriate browser cast; no native Google Nest/Cast receiver is implemented by this update. Check the exact smart-display model separately before relying on it.

## Ports and controls

- Cashier: 8787 by default; full merchant controls. The original combined demo is retained at /classic and the reader lab at /nfc-lab.
- Customer: 8788 by default; paired customer state, tip, skip and autograph-completion actions only. It does not expose reader endpoints or merchant cart/checkout creation.
- One Node process serves both ports, so carts and orders cannot drift between separate server processes.
- Customize with `node server.cjs --port 8787 --customer-port 8788`. The reader bridge must target the cashier server if its port changes.
- The paired link contains a customer capability token in its URL fragment. Requests use an Authorization header, not a query token. Anyone with that link can view that register's customer screen and choose a tip/skip for its current order; share it only with demo devices. This is a local HTTP prototype, not an authenticated production POS.
- Prices and taxes are calculated by the server; browser-supplied prices are ignored. A live checkout freezes cart edits. Customer actions carry the current checkout ID and reject stale orders, invalid tips, unsupported actions and cross-origin writes.

## Validation

Automated API tests cover two-port state sharing, canonical cart prices, display isolation, blocked merchant/reader controls, stale and invalid customer actions, final-total synchronization, NFC autograph delivery, server-side signature deletion, retry acknowledgement, reset and link expiry. Browser testing covers two independent browser origins, menu-to-display updates, customer tip selection, autograph replay, receipt, reload/new order, and tablet sizing. NFC in the browser test uses an explicitly simulated fixture; physical NFC remains the existing hardware path to verify on your desk.
