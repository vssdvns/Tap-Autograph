# Tap Autograph wallet V2 (0.9)

An independent wallet-style prototype with overlapping, swipeable fictional cards, Wallet/Studio tabs, automatic autograph sharing and a 45-second window. It has no affiliation with Google or Samsung and cannot make bank-card payments or intercept their wallet transactions. Only our private TAP2 AID and merchant/reader are supported.

## Install and test

Install the signed 0.9 APK over the previous Lab APK; the app label is now Tap Autograph. The package and development signing identity stay the same so saved designs remain available. Use the updated Reader Kit merchant/bridge. The working ESP32 diagnostic firmware does not need another flash.

1. Open Studio, select a saved autograph for screen and NFC, then return to Wallet.
2. Swipe the stacked cards to select Everyday or Weekend. The selection persists and the updated merchant receipt reflects it.
3. Enable Automatic autograph and accept its one-time explanation. This is permission to share on a subsequent tap after a demo payment, without a second phone button.
4. Choose default wallet using the Android system dialog if testing from outside the app. Selecting this app replaces the current default wallet; switch back to your regular wallet before real purchases. Alternatively use Open tap screen for foreground testing.
5. Start merchant and reader. In merchant 0.10, open the cashier on port 8787, pair its customer display on 8788, add products and choose Proceed to payment. The original combined demo is retained at /classic.
6. Keep the phone screen awake. Tap. Android may require unlocking, then another tap. A matching unlocked TAP2 offer starts a demo checkout; the HCE service attempts to bring its screen forward. Samsung/Android may suppress that activity launch, so Open tap screen remains the fallback.
7. The merchant acknowledges fictional payment acceptance. The phone shows blue confirmation, haptic feedback and a short notification tone if the UI is visible and audio settings allow it. The 45-second window starts at that acknowledgement.
8. Choose and confirm a tip on the merchant. Tap again, or leave the phone nearby. The bound final-total offer automatically enables the saved autograph transfer. No Share autograph button is required in automatic mode.
9. On reader acknowledgement, show success then return to Wallet. If the 45 seconds elapse first, discard the session, stop sharing and finish the checkout activity. No process kill or force-stop is attempted. Merchant checkout can still be finished without autograph.

## Android behavior and limits

The HCE declaration uses the payment category with our private prototype AID and requireDeviceUnlock=true. Android 15+ uses the Wallet role; older releases use the default payment app request. No EMV or PPSE AIDs are registered. Selecting the role does not grant access to existing card tokens. Default-wallet eligibility, locked-screen prompts, background UI launch and NFC routing require validation on the actual Samsung device. Secure NFC and manufacturer settings affect behavior; screen-off tapping is not promised and NFC must be enabled by the user.

Automatic mode survives leaving the screen but transacts only while unlocked and interactive. Returning to a locked screen cancels any active checkout when another APDU arrives; elapsed deadlines still apply if no APDU arrives. Process death loses any active order, so restart the merchant checkout. From 0.11, a short 1.5-second in-memory cooldown after finish/cancel/timeout avoids immediately rearming a held phone without blocking the next checkout for a minute. Preview tap card explicitly starts a new attempt and clears it. This cooldown is not a security boundary and does not survive process death.

The three-minute initial waiting window is separate from the 45-second post-acceptance window. Repeated first acknowledgements, SELECTs and final offers cannot extend the autograph deadline. Final offers must match the same order ID and nonce; later final-total changes are rejected. Legacy manual and payload-lab flows retain explicit sharing. Signature payloads and merchant offers remain unencrypted and unauthenticated; this is a controlled prototype, not a production payment or identity-signing protocol.

## Validation

Automated checks cover bound automatic sharing, selected-card propagation, replay rejection, timeout boundary, repeated acknowledgements, skip/disarm and the existing manual transfer flow. The Android APK is built and signed separately using the retained development distribution key; that key is excluded from this repository.

Build validation: 58 Android tests, 17 merchant tests and 14 bridge tests passed (89 total). The automatic Kotlin transcript was replayed through the Python bridge with no approval-button call. The APK compiled successfully; Samsung UI, lock-screen and background behavior are pending physical-device testing.

Device checklist:
- foreground first/second tap; held-in-field after tip; both card selections;
- no tip, custom tip, skip, walk away for 45 seconds;
- lock during active window: no handwriting until unlocked;
- default Wallet role accepted/declined, app in background, process cold start;
- changing signature in Studio updates next transfer;
- restore Samsung/Google Wallet as default and confirm this demo no longer takes payment-category routing.

Platform references: https://developer.android.com/develop/connectivity/nfc/hce
