# Photo & Meme importer (Android 0.10)

Studio → Photo & meme importer (or Gallery → Import photo or meme) accepts a local gallery image or camera photo. Choose Sticker, Photo dots, Outline, or the existing Paper ink style. Crop, rotate, straighten, adjust the threshold and optionally invert the artwork. The final compact-artwork and black-and-white receipt previews show what will be saved.

## Background removal

Enable Remove background, choose a corner containing the background colour, and adjust Background tolerance. Matching colours connected to the crop's outer edge are erased. Lower tolerance if part of the subject disappears; turn removal off to restore the original. Existing transparent PNGs retain their transparency. Transparent areas appear grey in the photo preview.

This is local edge-connected colour removal, not AI subject segmentation. It works best on plain or nearly plain backgrounds, sticker graphics and bold memes. Busy backgrounds, similar subject/background colours, hair and shadows may require an externally prepared transparent image. It does not promise automatic portrait cutouts.

## NFC and receipts

The importer converts the result into bounded scanline artwork carried by the existing TAG1 vector format. Detailed images are automatically simplified until the stroke budget is met, rather than clipping the bottom or silently dropping paths. This is monochrome artwork with an optional single ink colour, not a full-colour image upload. Photo dots approximates shading; small captions may become unreadable, so crop tightly and inspect the preview.

Photo-based Sticker/Outline/Photo dots artwork has zero-duration timing so the existing app and merchant reveal it at once. Paper ink retains approximate traced playback. No new NFC protocol, firmware flash, merchant version or network connection is needed. Both merchant screens continue to use the existing receipt flow. Receipt previews are 384-dot black-and-white images; direct thermal-printer integration is not included.

Choose Save & use artwork, give it a name and select it from the gallery as usual. Existing saved handwriting and doodles remain compatible. Source photos remain in memory during editing and are not copied into the saved gallery or uploaded to a cloud background-removal service. Only the saved compact design is sent when sharing is authorized. The existing gallery and NFC size limits still apply.

## Validation

Android assembleDebug and all 64 unit tests passed, including six new graphics tests covering reversible edge removal, enclosed details, corner/tolerance controls, transparent PNG preservation, all three artwork styles through NFC encode/decode and reader simulation, complexity reduction, crop/invert and blank-image rejection. Generated black-and-white receipt previews were visually inspected. Phone UI and physical NFC with a user-selected meme remain device checks.
