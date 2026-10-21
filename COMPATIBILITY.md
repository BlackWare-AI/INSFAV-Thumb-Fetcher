# Compatibility

## Files

Version 0.5.0 writes `.thumbnails/<normalized-username>.webp` at 256 × 256, WebP quality 78. Normalization trims, removes one leading @ and lowercases with Locale.ROOT. Filenames are not derived from display names. Valid old 128 × 128 WebP remains usable and is not changed automatically. The original JSON is never rewritten.

Fixed 256 × 256 output may upscale a smaller source; it adds no real detail. The temporary source viewer never claims that encoded thumbnail dimensions are the source resolution.

## Main INSFAV source inspection

Read-only inspection of BlackWare-AI/INSFAV `main`, commit `a4dc21db4f1780f0039bf22c53120c91f5827af9`:

- `LocalImages.java` accepts the `.thumbnails` child of the selected parent and indexes username files.
- `ImageCodec.bounds` accepts bounded JPEG/PNG/WebP dimensions, without requiring exactly 128 pixels. Therefore both 128 and 256 WebP inputs pass the same validation path.
- `ImageCodec.render(..., false)` currently resamples list image data to 128 pixels. This does not reject 256-pixel source files, but the list transport does not retain all 256 pixels.
- `web/style.css` keeps `.avatar` at 40 × 40 CSS pixels in the Android WebView. No style change is necessary for the helper output.
- `ImageCodec.render(..., true)` handles bounded full-image decoding. The existing main viewer and its caption are not changed or verified on a device here.

The helper's own source viewer shows actual decoded-source dimensions and labels any memory-sampled display. This is static compatibility evidence, not a demonstrated end-to-end main-app integration. Main INSFAV was not modified by this standalone branch.

## Reference

Archived working helper tag `v0.2.2-working` remains at `a153102dd95243465144efc3ec74b5cb11186e09` (actual application version 0.3.0). Its runtime sources and original signed APK are unchanged.
