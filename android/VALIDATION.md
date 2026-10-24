# Validation — Lab0.6 studio (2026-10-21)

assembleDebug + testDebugUnitTest: PASS in an isolated temporary build directory. 49 tests, zero failures/errors: 34 existing model/pairing/codec/APDU tests,8 studio model/geometry tests,7 native Android graphics tests. Main source/build configuration compared byte-for-byte with the built snapshot before packaging.

Robolectric4.14.1 native graphics at Android API35 checked all four font styles, all six doodles, photo skeleton tracing with crop/rotation/straightening, exact PNG pixel colors at384/576 widths, bounded rendering for extreme legacy aspect ratios, camera FileProvider scope, actual EXIF rotation, and the AtomicFile repository's legacy migration/gallery/receipt-choice persistence. Generated sample PNGs were visually inspected: name styles, six doodles and synthetic extracted paper ink. These are Android reference graphics, not screenshots of the S25 Ultra UI.

Eleven generated assets (four names, six doodles, one synthetic paper signature) passed full APDU simulation and the existing Merchant0.5 Node receiver. Reconstructed packet bytes matched Kotlin output exactly. Doodles352–574 bytes (2–3 chunks), sample name styles1,200–2,867 bytes (7–15 chunks), sample paper ink760 bytes (4 chunks). Results are synthetic examples; real photos/names vary. No user handwriting/photo was bundled.

APK package com.tapautograph.app.lab, versionCode6/versionName0.6.0. V2 signature verifies; certificate SHA256 ba2acc166b8b032cf337c2c6eb1b6ad4438d45132767c79e8609a83e26c9f415 matches the retained Lab development key. Manifest permissions remain INTERNET, NFC and the AndroidX app-specific dynamic-receiver permission. No broad storage/photo/camera permission was added. External camera capture uses the system camera app and a scoped FileProvider URI.

## Still needs physical-device checks

APK installation/update on the user's phone, new Compose layouts/accessibility, native S Pen behavior in this version, system photo picker and camera app launch/result lifecycle, real photographed paper under varied light/perspective, and new-design physical NFC transfers have not been checked on a device here. The user reported the earlier reader kit working. The new tests validate protocol compatibility; they are not RF or hardware tests. No physical printer/POS integration was implemented or tested: receipt output is artwork-only PNG. Name fonts can render differently across devices.

## First phone check

Update the existing Lab APK without uninstalling; verify old ink/history remain. Home → Open your gallery. Try each creation mode, rename/select a design, and use Share selected design with NFC. A receipt selection should not change the screen/NFC selection. Test Delete with an unused design. Try a close paper photo using crop/threshold/cleanup and compare the extracted preview before saving. Export384/576 PNGs from receipt art. Existing reader kit/firmware can remain unchanged because TAG1/TAP1 are unchanged.
