# v0.16.0 — demonstrated prototype

This document consolidates the current project state for the public demo. It is not a production payment release, an app-store release or a claim of commercial terminal support.

## Current scope

- Android Wallet/Studio interface, saved designs, photo/meme import and plain-background removal.
- Compact tap card, payment feedback, selected artwork preview and favorites dial.
- Automatic autograph readiness after simulated payment, with a 45-second window.
- Synchronized cashier/customer displays on ports 8787/8788.
- Combined tip and manual signature screen: draw + Continue, or choose a tip + tap phone.
- Full autograph payload over the custom NFC protocol; no phone Wi-Fi requirement for this path.
- Compact TAG1/TAG2/TAG3/TAG4 selection; original saved artwork retained.
- Turbo USB mode and optional bulk-SPI phase instrumentation.

## Evidence

The author demonstrated physical phone-to-reader transfers and the checkout workflow. Matched USB tests reduced mean transfer time from 1.499 s to 1.1332 s for one 1,558-byte packet. See [physical results](PERFORMANCE-RESULTS.md) for raw completed timings and limits.

Earlier full v0.16 validation: 83 Android tests, 27 Node tests, 30 Python tests; firmware build and host SPI/serial checks passed. Mocked checks do not establish radio performance. The documentation refresh reruns Node and Python checks separately and does not rebuild the Android APK or firmware.

## Deployment and compatibility

Update the app, merchant decoder/bridge and reader firmware together when moving to v0.16 from older versions. Use the current source and component setup guides. No signing keys or APK binaries are committed. A local debug build has a different signing identity from the previously distributed development APK unless the same key is available.

Turbo is the practical demo mode. Bulk SPI is opt-in and has no demonstrated additional physical speedup. Keep the existing tested SPI clock and wiring.

## Limitations

Payments and cards are fictional. Default-wallet selection only routes this prototype's AID; it cannot access Google/Samsung/Apple payment credentials. Background launch/unlock behavior depends on the device and Android settings. Foreground unlocked operation remains the fallback.

The reader is unauthenticated and artwork transport is unencrypted. Order nonces correlate the prototype flow without proving merchant identity. Receipt previews do not drive a physical printer. Optimization is paused while gathering feedback.

## Demo

[LinkedIn project post](https://lnkd.in/p/gwWEqyTk).

