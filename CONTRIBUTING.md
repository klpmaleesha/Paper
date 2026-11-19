# Contributing to Paper

Paper is GPL-3.0-or-later, with third-party notices in `THIRD_PARTY_NOTICES.md`. Contributions must be compatible with that licence. Do not add proprietary printer binaries, vendor credentials, tracking SDKs or unlicensed artwork.

## Build and checks

This repository is the standalone Android project. Run commands from its root; `.github/workflows/android.yml` checks source builds and generates review artifacts on GitHub Actions.

Use JDK 17, Android SDK 35/build tools 35.0.0, NDK 27.2.12479018 and CMake 3.22.1. The Gradle wrapper pins Gradle 8.11.1. Normal Gradle builds compile the vendored Canon driver from source through CMake; they do not package the checked-in development `.so`.

```sh
bash gradlew compileDebugUnitTestKotlin assembleDebug lintDebug --console=plain
node tools/test-protocol.mjs
```

The independent JUnit runner avoids loading the UI renderer for protocol, transport, preset and planning tests. It supports Windows/Linux/macOS when dependencies have been resolved by Gradle and `JAVA_HOME` is set.

On this isolated Windows workspace the existing partial NDK is compiled by `node tools/build-native.mjs`. `node tools/build.mjs ...` explicitly selects `-PrebuildNative=false` to package that output. This is a development convenience, not the source-build path used by CI.

UI snapshots use `recordPaparazziDebug`; inspect the generated images before accepting a layout change. Render success does not prove TalkBack, physical printing or Android permission/lifecycle behaviour. Those need device tests.

## Printer contributions

Add a separate driver/profile rather than weakening another model's identity checks. Keep connection handling, capability discovery, encoding and job-state reporting separate. Never infer PDF/PWG support from the printer brand or IPP/IVEC support alone.

Use independent protocol fixtures and a local simulated printer for encoding/transport tests. Include malformed responses, unsupported settings, cancellation and uncertain-submission cases. A submitted job must never be retried automatically after transmission could have started.

Update `COMPATIBILITY.md` with the exact scope and evidence. Only classify a printer as verified after physical testing with the checklist. Report unsupported features explicitly.

## Releases

GitHub development prereleases are published as Paper by Avyntora. Debug APKs are for testing. Production/store releases need a deliberately chosen application ID, a stable release signing key held outside the repository/CI artifacts, current Android/Google Play compliance checks, a published privacy policy, and matching tagged corresponding source. Never commit a keystore or its password. Production signing and store publication are separate from the GitHub development release.
