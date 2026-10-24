# TAP1 — experimental autograph APDU transport

This is an independent non-payment HCE experiment using ISO-DEP APDUs. It neither implements EMV nor communicates with Samsung Wallet/Google Wallet. Category `other`, experimental unregistered AID `F054415041555401`. Real deployments need an appropriate registered AID; no uniqueness claim. No change of default wallet is required by this design.

## Consent and lifetime

The user confirms sharing from the payload lab. A snapshot exists only in process memory for 60 seconds, measured using a monotonic clock. Leaving the screen, backgrounding the activity, stopping manually, expiration, or an accepted finish removes the live packet. Process death loses the grant. The service rejects locked/screen-off requests. The service does not load saved ink or encode it in the APDU callback. Snapshots are encoded before arming; replies only copy small pieces.

A reader in range can copy the enabled packet. This version is plaintext and does NOT authenticate merchants, encrypt handwriting, bind it to a purchase, or authorize a payment. SHA256 and CRC are unkeyed integrity checks, not proof of origin. A malicious reader may keep its copy or acknowledge before persisting. UI says “acknowledged by reader,” not delivery proof.

## Commands

All numbers are big endian except fields inside TAG1. Short APDUs, basic logical channel only. Success response ends `9000`.

| Operation | Command | Response data before status |
| --- | --- | --- |
| Select | `00 A4 04 00 08 F054415041555401 00` | version u8=1, session 8 random bytes, TAG1 length u32, chunk size u16=192, chunk count u16, SHA256 32 bytes |
| Read | `80 10 00 00 0A` + session8 + index u16 | session8 + index u16 + packet chunk (1–192 bytes) |
| Finish | `80 20 00 00 28` + session8 + SHA25632 | empty |

Metadata is 49 bytes. Max read response is 204 bytes including session/index/status. Index is zero-based. Last chunk may be shorter. TAG1 limit is 65,536 bytes. Reader must verify packet length, SHA256, and the TAG1 decoder before finishing. It must not render partial packets.

Read retries are idempotent. Read order may vary. Each index must be served before Finish is accepted. Link deactivation resets selection, but keeps the consent snapshot and served indices until expiry. Re-select within that grant returns the same session and metadata; the reader may resume only if metadata matches exactly. A fresh grant has a new session. A repeated Finish on the same selected link returns success without restoring ink. Re-select after completion fails; a lost final acknowledgement across a disconnected link requires explicit new consent, and the merchant must avoid duplicate display itself. There is no automatic repeat arm.

Errors: `6985` off/expired/unselected/stale/incomplete/wrong digest; `6700` malformed length; `6E00` unsupported class; `6D00` unsupported instruction; `6A86` bad parameters; `6B00` chunk index out of range. Extended APDUs/optional Le on custom commands are unsupported. Readers must send the exact experimental commands above.

## Simulation

The Android simulator uses a separate instance of the same command engine, repeats the first read, simulates link deactivation, selects again, reads remaining chunks, validates the packet and finishes. It exports JSON `{protocol:"TAP1", simulated:true, exchanges:[{command:HEX,response:HEX}]}`. This contains the full unencrypted autograph. It does not consume/enable the live sharing grant.

The laptop's `nfc/reader.cjs` checks transcript structure, metadata consistency, chunk session/index/size, consistent duplicates, completeness and hashes, then decodes TAG1. It cannot authenticate a transcript; anyone can generate one. The merchant `/nfc-lab` endpoint accepts only loopback clients, processes in memory without retaining ink, and does not alter checkout state. A USB/serial APDU adapter for PN532/ESP32 is the next hardware step and is not implemented here.

Reference: https://developer.android.com/develop/connectivity/nfc/hce
