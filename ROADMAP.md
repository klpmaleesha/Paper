# Release roadmap

## 0.3 — Implemented development milestone

Capability/compatibility models; saved presets; manual duplex setup; local PDF-over-IPP discovery and print-service integration; single-submission protection; source-based native builds; protocol/loopback tests and visual review renders.

These implementations are experimental until exercised on phones and physical printers. Configuring CI is not proof that a hosted CI run has passed.

## Before a public beta

- Complete the G2010 feature matrix using `COMPATIBILITY.md`; basic printing was user-confirmed in 0.2.0. Capture reproducible evidence for each supported mode; resolve failures rather than relabelling them as verified.
- Test IPP and IPPS on real printers from more than one manufacturer, with Android 8/current Android and different phone vendors. Exercise discovery, service enable/disable, print-menu page ranges, copies, automatic duplex, status, TLS, cancellation, backgrounding and process interruption.
- Validate saved presets, reload guidance, TalkBack, large text and dialog behaviour on a device. The host renderer can inspect screen/sheet content but cannot verify Android's separate dialog windows or lifecycle.
- Run the new standard CMake/NDK build and CI workflow on a clean Linux runner. Verify APK native-library alignment on 16 KB-page devices; benchmark CPU, memory and storage.
- Choose the final application ID and release signing arrangement. Complete current store/distribution requirements and publish matching source and privacy policy. Keep signing keys out of source and test artifacts.

## Further printer support

- Negotiate and encode additional standard document formats such as PWG Raster where useful, keeping PDF pass-through for printers that accept it.
- Add native USB printer families as separate drivers backed by exact identity checks, tested capability profiles and compatible licences.
- Add reported printable margins, media types, trays and specialised quality settings to the network service. Add authentication only with deliberate credential storage and lifecycle design.
- Consider an optional local print-server bridge for printers whose proprietary drivers cannot be supported directly on Android.

## Document and everyday usability

- Page thumbnail selection, reordering, document merging and booklet imposition.
- Preserve vector PDFs when transformations/driver formats allow, avoiding repeated rasterization.
- Explicit job history and recovery based on user-confirmed printed sheets. Never silently restart an uncertain job.
- Broader localisation, tablet/foldable layouts, accessibility testing and optional community-supported diagnostics.

Scanning, OCR and direct Office conversion follow reliable document printing rather than blocking the initial release.
