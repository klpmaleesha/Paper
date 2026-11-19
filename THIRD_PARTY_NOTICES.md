# Open-source driver attribution

Paper incorporates portions of [noble6/canon-universal-driver](https://github.com/noble6/canon-universal-driver), revision `55c8322936569f2b2f57db357d95ad934453d73b`, licensed GPL-3.0-or-later. The original SPDX notices and package names are preserved. Paper is distributed under GPL-3.0-or-later; the complete corresponding source is in this folder.

Included: the IVEC protocol and legacy PWG raster encoder, model/status types, USB transport, banded page renderer, and G3010 profile. The PWG encoder and G3010 profile remain for research/tests; version 0.2.0 does not use them to encode USB jobs. No dedicated upstream G2010 profile exists at this revision.

Local changes to USB include configuration-index fallback, package-scoped permissions, rejection of invalid completion byte counts, distinct handling of request failure and timeout, acknowledged-byte reporting, status diagnostics, and a silent-transfer deadline. Legacy renderer/PWG changes support physical scale and disk spooling. Paper supplies its own foreground service, UI, preparation, duplex planning, cancellation, JNI bridge, and banded RGB provider.

Paper also incorporates Gutenprint 5.3.5 from its official release archive, licensed GPL-2.0-or-later. See `native/gutenprint/COPYING`, original file notices, `native/gutenprint/SOURCE.md`, and `driver-research/gutenprint-source.json`. Only the Canon printer and traditional colour modules are linked; the static module list, build configuration and version header are generated locally. Compiled source, headers, paper/dither data, and Canon printer definitions are included in the corresponding source archive. The G2100 profile is used experimentally for G2010 and is not a dedicated G2010 driver. Source URL: https://downloads.sourceforge.net/project/gimp-print/gutenprint-5.3/5.3.5/gutenprint-5.3.5.tar.xz

No Canon driver DLL, firmware, or other proprietary binary is included.
