# Paper 0.3.0 verification

Paper is a product of Avyntora.

Local checks on 19 November 2026:

- Android debug APK assembled successfully; version code 4, application ID `app.paper.print`.
- Android lint: zero errors (twenty warnings remain).
- Platform-independent JUnit suite: 80 tests passed, including real local HTTP exchanges with a simulated IPP printer.
- Seven Compose renders generated and visually inspected: light/dark main screen, welcome, layout controls, presets, printer management, and manual-duplex setup. Modal content is rendered directly; Android's separate dialog windows and their lifecycle are not exercised by the host renderer.
- APK signature verification passed with the existing development certificate, matching the prior locally supplied APKs.
- The G2010 native encoder is unchanged from 0.2.0. The user confirmed that Paper 0.2.0 printed successfully on their G2010 on 19 November 2026; exact modes/page counts were not specified. Its earlier offline synthetic tests are separate evidence.

## Hosted verification

The [Avyntora-branded source build on GitHub Actions](https://github.com/klpmaleesha/Paper/actions/runs/44559689022) passed its Android build/lint, 80 protocol/planning tests and seven Compose render checks on a clean Linux runner. The native Canon library was built from the vendored sources using the pinned NDK and CMake toolchain. An [earlier clean source run](https://github.com/klpmaleesha/Paper/actions/runs/44558529452) also passed after correcting the SDK setup step.

The uploaded APK was built and signature-checked locally using the existing development signing certificate, so it can update earlier locally supplied builds. CI artifacts use the runner's development signing certificate and are not substitutes for that update APK.

Still unverified: the full G2010 feature matrix and 0.3.0 physical regression test; network-printer output; Android print-service enablement/discovery/cancellation on a phone; accessibility and large-font behaviour on-device; and runtime behaviour on 16 KB-page devices. The development APK is not a production-signed store release.

See `COMPATIBILITY.md` for the physical test checklist and `ROADMAP.md` for public-beta gates.
