# Current INSFAV compatibility

The current main application's ThumbnailPolicy normalizes a filename stem by trim, one leading @ removal and Locale.ROOT lowercase. It recognizes supported image extensions and matches that stem to imported usernames. This revision therefore writes exactly `.thumbnails/<normalized username>.webp`. It does not modify INSFAV or depend on the old draft hash-support PR.

Identity.outputFilename owns the new filename contract. The old Identity.filename SHA-256 utility is retained only for historical compatibility; no new hash-named output is generated. Normalization and the existing three-format JSON importer are preserved.

OutputFolder accepts a selected parent or `.thumbnails` itself. It reuses the exact existing folder, validates 128 × 128 WebP bytes, skips good files, recovers recognized partials and stages verified writes before rename. It never deletes unrelated files or old `.thumnails` outputs. Selecting the old `.thumnails` child asks the user to select its parent rather than nesting the new folder inside it.

The private queue schema migration preserves accounts/order and resets terminal statuses to PENDING for revalidation under the new output contract. No source JSON or external thumbnail is rewritten by migration. Old valid `username.webp` files are read and skipped; old SHA files remain in place for manual retention. No automatic copying/migration of media is performed.

Refresh the folder in INSFAV after downloading. If a username changes, its new filename differs. Existing successful files are never refreshed without an explicit future refresh feature; none is added here.

Physical-device interoperability and live Instagram retrieval remain acceptance checks. Automated SAF/image fixtures and actual filename-policy comparisons cannot replace those checks.
