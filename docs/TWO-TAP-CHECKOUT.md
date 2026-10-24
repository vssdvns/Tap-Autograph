# Two-tap demo checkout

Status: planned; implementation is not part of the working baseline.

## Scope

The phone is unlocked, NFC is enabled, and Tap Autograph is in the foreground. No Samsung Wallet or Google Wallet control and no real payment processing.

1. Merchant prepares an order and reader exposes a demo checkout challenge.
2. First tap exchanges order ID, merchant label, currency, final amount and a fresh challenge over the existing reader/USB path.
3. Phone displays the order for approval. A short original pulse/check animation and vibration follow confirmed demo acceptance.
4. Phone prompts: Tap again to share your autograph. Sharing is optional, time limited and bound to the accepted order and challenge. Skip is available.
5. Second tap delivers the saved autograph for that order. Merchant verifies the binding before animating and completing the receipt.
6. Phone shows completion only after a merchant acknowledgement. Interrupted transfers can retry without applying the autograph twice.

## Required changes

- Android: foreground checkout screen and state machine; order review and approval; tap feedback; bounded second-tap sharing; cancellation and expiry.
- Protocol: versioned order/challenge exchange, final amount, explicit demo acceptance, transaction-bound autograph envelope and completion acknowledgement. Keep the existing standalone ink lab compatible.
- ESP32/Python bridge: carry checkout commands and statuses in both directions while preserving bounded frames and retries.
- Merchant: supply the active order, freeze the confirmed total, bind both taps, and report completion.

## Validation targets

Happy path, skip, expired challenge, changed order/amount, wrong order, duplicate taps, connection loss before and after acknowledgement, app backgrounding, and oversized/malformed messages. Hardware validation on the existing S25 Ultra and ESP32/PN532 follows automated tests.

## Limits

A checkout challenge is correlation, not merchant authentication. Do not claim a legal signature, payment authorization, encrypted transport or real wallet payment. Android system authentication can be added as a separately tested approval step; unlocking the phone alone is not consent to share handwriting.
