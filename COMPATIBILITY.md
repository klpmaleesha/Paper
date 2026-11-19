# Printer compatibility

Paper 0.3.0 is a development build. On 19 November 2026 the user confirmed successful physical USB printing on their G2010 with Paper 0.2.0. The individual modes/page counts tested were not specified, so this is evidence for basic printing rather than verification of the full feature matrix. The native raster encoder is unchanged in 0.3.0. No model is classified as fully verified yet.

| Printer or protocol | Connection | Status | Scope |
| --- | --- | --- | --- |
| Canon PIXMA G2010 | USB OTG, ARM64 Android | Experimental; basic printing user-confirmed | Paper 0.2.0 printed successfully on the user's printer. The Gutenprint G2100-family raster encoder is unchanged in 0.3.0. Individual colour/grayscale, paper, duplex and recovery results remain to be documented. |
| Capability-checked PDF-over-IPP printers | Local network, IPP/IPPS | Experimental | Requires advertised PDF, Print-Job, Get-Job-Attributes, one-sided mode, a known paper size, a valid resolution and a recognised colour mode. Automatic duplex is exposed only when reported by the printer. |
| IPP printers requiring PWG Raster, URF or PCLm | Network | Unsupported by this backend | A format encoder is needed. Advertising IPP alone is insufficient. |
| Other proprietary USB printer families | USB OTG | Unsupported | Discovery does not imply driver support. No guessed print data is sent. |
| Authenticated print servers | Network | Unsupported in this version | Authentication credentials are not requested or stored. |

IPPS uses Android's normal TLS certificate checks. There is no certificate bypass or silent downgrade. Plain IPP is unencrypted and is labelled in the printer list. DNS-SD discovery runs for twelve seconds when requested in Paper or when Android asks the enabled Paper print service to discover printers.

The first network backend uses the printer's configured media type and tray. Specialised photo-paper modes, borderless printing, printable-margin negotiation, authenticated servers and raster-only network printers are not implemented. It does not infer colour hardware from an `auto` colour mode: explicit `color` or `monochrome` support is required.

## Verification criteria

For an exact model and connection to become verified, record the app version, phone/Android version, driver/profile, and physical results for:

1. One A4/Letter page, readable text, correct scale and printable margins.
2. Colour and grayscale, if advertised. Do not equate grayscale with black-tank-only ink use.
3. Every advertised paper size and relevant quality mode.
4. Multi-page documents and collated copies, including an odd-page document.
5. Supported long-edge and short-edge duplex. For manual duplex, use the four-page, two-sheet setup document and confirm matched fronts/backs and upright orientation.
6. Out of paper, an offline printer, USB/network disconnection, cancellation during submission and during printing, and screen locking/backgrounding.
7. Cancellation and completion must be distinguished from merely transmitting bytes. Check for missing or duplicate sheets after failures.

Use the printer-report issue template. Supply a synthetic test document or Paper's sample, never a private document. A passing codec test or an upstream driver support claim does not verify physical output on Paper.
