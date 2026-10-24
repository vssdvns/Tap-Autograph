# Tap polish / 0.12

Install the APK over the existing app to keep saved artwork. No Reader Kit update, ESP32 reflash, or wiring change is required for this release. Demo payments remain simulated.

## Payment feedback

An original short two-note chime and double-click haptic fire once when the reader acknowledges the accepted order. Feedback belongs to the NFC session, so hiding, opening or rotating the compact panel cannot replay the payment chime. A recovered final offer can trigger it if the first ACK was lost. Expired and unaccepted sessions cannot trigger it.

Settings → Tap feedback has separate sound and haptic switches. Audio uses Android notification volume and follows the phone's sound/Do Not Disturb policy. The WAV was synthesized for this project, rather than copied from another wallet. Sound loading/playback is dispatched outside the NFC response path.

## Autograph dial

After the blue acceptance check, a small round preview represents the current autograph. Leave it alone to send the default on the next tap. Drag from the circle to bloom the wheel, rotate toward a favorite, then release to choose it. Move back to the center or far outside to cancel. A subtle highlight and optional haptic identify slot changes. A shrinking ring around the small button shows the remaining time and turns amber in the last few seconds. Tap the circle to browse and tap a favorite if dragging is inconvenient; Use default and Keep current are also available.

Lift the phone after the first accepted tap if you want time to choose an emote. Holding it in place after the tip is confirmed can start sending the default immediately, as before.

Up to six favorites are starred in Studio → Gallery. The checkout choice is temporary; the Wallet dial changes the saved default. Once transfer starts the dial collapses and locks so an in-flight packet cannot change. Smooth expansion and highlight animations respect Reduced motion. The existing order binding, first-acceptance 45-second deadline, verification and success acknowledgement remain unchanged.

## Samsung unlock/retap screen

The system message “Tap the NFC reader again … to complete the payment” is Android's unlock/retap UI, not Tap Autograph's checkout panel or a failed transfer indicator. Android documents displaying this after unlock because the device may have moved away from the reader. Unlock, then bring the phone back to the reader. If it was already unlocked, retap once and check the default-wallet setting if the message repeats.

This release shortens the NFC service description to “Tap Autograph,” so system wording no longer includes the old experimental-reader label. It keeps `requireDeviceUnlock=true` and the service's unlocked/awake check. It does not bypass the device lock or claim to replace Samsung's system dialog. The notification fallback remains available if the compact activity cannot launch.

Reference: https://developer.android.com/develop/connectivity/nfc/hce#ScreenOffBehavior

## Validation

Build/unit checks cover six-sector geometry, dead zone/cancellation, nonfinite input and once-per-accepted-order feedback including repeated ACKs, retaps, recovered final offers and expiry. Actual sound audibility, gesture feel and Samsung system-screen behavior require a phone test.
