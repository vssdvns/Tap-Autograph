# Combined tip and signature screen / 0.14

Update both the Android APK and Reader Kit, then restart the merchant and USB bridge. Install over the previous app to preserve saved artwork. No ESP32 firmware or wiring change is needed. Payments remain simulated.

## Phone previews

The selected autograph is visible inside the dial's center and in a small preview box above it. Both previews crop to the ink bounds without modifying the saved autograph or its transfer packet. Favorite previews retain the same tight crop. Choosing a favorite updates the current checkout preview; the saved default stays unchanged.

## One customer signing screen

After payment acceptance, the customer sees tip choices, the current total, and a signature pad together. There is no extra Continue step to reach an autograph-wait page.

- **Manual:** choose a tip, draw with a mouse, finger or stylus, then press Continue. The order completes and its receipt shows the manually drawn signature.
- **NFC:** choose a tip (including explicit No tip), wait for Ready, then tap the phone. That tap confirms the selection and supplies the saved autograph; the receipt finishes automatically. Continue is not pressed.
- **Optional:** Continue without signature finishes without artwork.

Drawings survive tip changes and screen redraws during the order. Clear removes the local draft and disables manual Continue until new ink is drawn. A custom tip is validated before either path can finish. The same signing form remains present while a confirmed NFC transfer is pending, with its final tip locked.

## State and privacy

Manual Continue sends the tip selection revision and bounded autograph data together. Invalid drawings, stale tip revisions, wrong checkout capabilities, changed totals and finished/cancelled orders are rejected. Retries of the same accepted manual signature are idempotent; a late competing manual or NFC signature cannot overwrite the completed receipt.

Manual ink stays in the browser until Continue. The server validates it but retains only a completion hash and metadata; the receipt image stays in customer-page memory. Reloading a completed receipt restores totals/status, not erased artwork. The phone has no merchant network connection to notify it of a manual completion, so its unused optional autograph window expires normally or can be hidden.

## Validation

Android assembleDebug and all 78 Android tests passed. All 24 Node tests and 19 Python tests passed. New API checks cover manual submission, stale/invalid data, replay, capability/origin boundaries and races with NFC.

Browser automation exercised mouse drawing, Clear, drawing preservation after tip changes, manual Continue with a selected tip, NFC completion without Continue, tablet touch drawing, explicit No tip and unsigned completion. Desktop/tablet layouts were inspected. NFC in that browser test was explicitly simulated; physical Samsung preview appearance and real reader interactions remain an on-device check.
