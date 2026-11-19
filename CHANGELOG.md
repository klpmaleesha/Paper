# Changelog

## 0.3.0 — Multi-printer development milestone

- Introduces capability and compatibility models; no printer is presented as physically verified without evidence.
- Adds saved print presets, four built-in starting points, and up to twenty custom presets. Page selection and copy count are not stored in a preset. Fixes photo-paper preference persistence.
- Adds a four-page/two-sheet manual duplex setup document and locally saved reload instructions for the G2010 with A4 portrait, long-edge binding.
- Adds local IPP/IPPS discovery, manual printer addresses, capability checks and a printer management screen. Non-PDF network printers are explicitly unsupported by this backend.
- Adds a Paper Android PrintService for eligible network printers. Queries capabilities again before sending, submits PDF once, tracks printer job states, and requests remote cancellation where available. No credentials, redirect following, certificate bypass, automatic document resend or cloud relay.
- Exposes only reported paper, colour and automatic duplex modes in Android's Print menu. The main app's duplex preparation remains manual; leave Android duplex off when printing those prepared passes.
- Detects unsupported USB printers without pretending they have a driver. USB jobs use the selected device ID and retain the G2010 identity validation.
- Adds malformed-IPP and real loopback HTTP tests, source-based native CMake builds, Linux CI configuration, a compatibility-report template, and contribution/privacy documentation.

This is not a public production release. The user confirmed successful G2010 printing with 0.2.0 on 19 November 2026; the full mode matrix and new network service still require device/printer testing.
