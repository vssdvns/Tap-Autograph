# Compact checkout / 0.11

Payments remain simulated, using the existing private TAP2 AID and reader firmware. There is no real wallet, bank-card or commercial POS integration.

## Install and try

1. Install the updated APK over the previous Tap Autograph app (keep saved designs).
2. Turn on **Automatic autograph**, choose Tap Autograph as the default demo wallet, and allow tap notifications. Existing users can press **Allow tap notifications** on Wallet.
3. For the reader speed improvement, replace the Reader Kit folder with the new ZIP and run its merchant/USB bridge as before. No ESP32 reflash or wiring changes.
4. Start a merchant order. With the phone screen awake, unlock and tap. If Android requests unlock during a tap, unlock and tap again.
5. A compact bottom card appears when the system allows it; it disappears into a blue confirmation check only after the merchant's acceptance ACK. Background sharing does not require this surface to open.
6. Choose tip on the customer display. Retap or keep holding the phone to send the autograph automatically. Progress represents chunks served; success waits for the merchant's verified Finish acknowledgement.
7. The 45-second autograph window still starts at payment acceptance. Hide dismisses only the surface; Skip cancels the optional autograph. Expiry clears the grant and closes the surface.

If Samsung suppresses the automatic surface, tap the **Tap checkout** notification. The full wallet is not required for the HCE exchange, but default-wallet routing, NFC enabled, an unlocked awake phone and the explicit automatic-sharing opt-in are required. No unlock bypass, overlay permission, full-screen notification, or silent NFC enabling is used. Actual Samsung background/unlock behavior needs device testing.

## Emote wheel

Studio → Gallery → **Add to emote wheel**. Star up to six saved drawings, imported memes or signatures. Wallet's wheel changes your saved default; the checkout wheel changes only the current session. The center sends the saved default. Missing/deleted designs are ignored. A session choice is accepted only before the final offer starts sending; a racing selection cannot mutate a packet in flight or restart the deadline.

## Speed work

- The old 60-second rearm cooldown is reduced to a short 1.5-second guard. Completed sessions still clear their packet before a fresh order can bind.
- Cold SELECT now returns an asynchronous HCE response after loading/encoding the saved design, rather than rejecting the first tap while the cache is empty. Link deactivation/destruction invalidates pending responses.
- An NFC-only copy removes redundant pen points with a geometric error threshold of 0.001 canvas heights, retaining stroke endpoints, corners, tool and significant pressure changes. Saved originals are unchanged. Two-point meme scanlines are unchanged.
- Long handwritten playback is scaled to at most 1.2 seconds in the NFC copy. This reduces receipt animation waiting, separate from transport latency.
- Bridge progress events are limited to four per second plus the final chunk rather than a blocking HTTP event for every 192 bytes. SHA-256/session/chunk verification, retries and publish-before-Finish are retained.
- Bridge output includes measured transfer seconds. Existing TAG1/TAP2 envelopes, 192-byte chunks and firmware limits remain unchanged. Dense image artwork still costs more transfers than a simple autograph; no hardware speedup is claimed before measurement on the kit.

## Validation

Android build and unit tests cover packet simplification, original preservation, pressure/corners, unchanged meme scanlines, wheel selection/expiry/progress, cold HCE startup, locked rejection and pending-link cancellation. Python fixtures check throttling and publish-before-Finish. Physical Samsung, radio transfer timing and compact UI appearance must be checked on the device.
