import {readFile,writeFile,mkdir,copyFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
const root=resolve(import.meta.dirname,'..');
const info=JSON.parse(await readFile(join(root,'driver-research/source.json'),'utf8'));
if(info.revision!=='55c8322936569f2b2f57db357d95ad934453d73b')throw Error('Unexpected upstream version; review the new source before vendoring');
const source=join(root,'driver-research/canon-universal-driver-'+info.revision);
const core='io/github/noble6/canonivec/core',base='io/github/noble6/canonivec';
for(const name of ['Model.kt','Pwg.kt','Ivec.kt','Status.kt','PrintJob.kt']){
 const dir=join(root,'app/src/main/java',core);await mkdir(dir,{recursive:true});
 await copyFile(join(source,'android/core/src/main/kotlin',core,name),join(dir,name));
}
for(const name of ['UsbPrinter.kt','PageRenderer.kt']){
 const dir=join(root,'app/src/main/java',base);await mkdir(dir,{recursive:true});
 await copyFile(join(source,'android/app/src/main/kotlin',base,name),join(dir,name));
}
const assets=join(root,'app/src/main/assets');await mkdir(assets,{recursive:true});
const db=JSON.parse(await readFile(join(source,'models/ivec_models.json'),'utf8'));
const model=db.models.find(x=>x.id==='canong3010');if(!model)throw Error('Required experimental profile missing');
await writeFile(join(assets,'ivec_g3010_experimental.json'),JSON.stringify({source:db.source,models:[model]},null,2));
await copyFile(join(source,'LICENSE'),join(root,'LICENSE'));
await copyFile(join(source,'LICENSE'),join(assets,'GPL-3.0.txt'));
await writeFile(join(root,'THIRD_PARTY_NOTICES.md'),`# Open-source driver attribution\n\nPaper incorporates portions of [noble6/canon-universal-driver](${info.repository}), revision \`${info.revision}\`, licensed GPL-3.0-or-later. The original SPDX notices and package names are preserved. Paper is distributed under GPL-3.0-or-later; the complete corresponding source is in this folder.\n\nIncluded: the IVEC protocol and PWG raster encoder, model/status types, USB transport, banded page renderer, and the G3010 profile. This profile is used experimentally for IVEC-capable G2010 devices. No dedicated upstream G2010 profile exists at this revision.\n\nLocal changes to the USB transport: configuration-index fallback for device identification, package-scoped permission action, and rejecting zero-length transfer completions instead of assuming successful transmission. Local changes to the band renderer: preserve physical page scale and crop to the hardware printable area; reject a USB job above the working memory budget. Paper implements its own foreground service, job UI, document preparation, manual duplex planning, and cancellation lifecycle.\n\nNo Canon driver DLL, firmware, or other proprietary binary is included.\n`);
console.log('Vendored reviewed Kotlin driver source and GPL license.');
