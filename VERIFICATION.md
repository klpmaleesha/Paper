# Paper 0.3.0 verification

Local checks on 19 November 2026:

- Android debug APK assembled successfully; version code 4, application ID `app.paper.print`.
- Android lint: zero errors (twenty warnings remain).
- Platform-independent JUnit suite: 80 tests passed, including real local HTTP exchanges with a simulated IPP printer.
- Seven Compose renders generated and visually inspected: light/dark main screen, welcome, layout controls, presets, printer management, and manual-duplex setup. Modal content is rendered directly; Android's separate dialog windows and their lifecycle are not exercised by the host renderer.
- APK signature verification passed with the existing development certificate, matching the prior locally supplied APKs.
- The G2010 native encoder is unchanged from 0.2.0. The user confirmed that Paper 0.2.0 printed successfully on their G2010 on 19 November 2026; exact modes/page counts were not specified. Its earlier offline synthetic tests are separate evidence.

Still unverified: the full G2010 feature matrix and 0.3.0 physical regression test; network-printer output; Android print-service enablement/discovery/cancellation on a phone; accessibility and large-font behaviour on-device; a hosted run of the new CI configuration; a clean full-NDK source build through Gradle/CMake. The local Windows build uses the existing native library compiled from vendored source. The development APK is not a production-signed public release.

See `COMPATIBILITY.md` for the physical test checklist and `ROADMAP.md` for public-beta gates.
