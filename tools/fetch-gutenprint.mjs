import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {createWriteStream,existsSync} from 'node:fs';
import {pipeline} from 'node:stream/promises';
import {Readable} from 'node:stream';
import {spawn} from 'node:child_process';
import {resolve,join} from 'node:path';
import {createHash} from 'node:crypto';
const root=resolve(import.meta.dirname,'..'),dest=join(root,'driver-research');
await mkdir(dest,{recursive:true});
const url='https://downloads.sourceforge.net/project/gimp-print/gutenprint-5.3/5.3.5/gutenprint-5.3.5.tar.xz';
const file=join(dest,'gutenprint-5.3.5.tar.xz');
if(!existsSync(file)){const r=await fetch(url);if(!r.ok)throw Error(`Source download failed: ${r.status}`);await pipeline(Readable.fromWeb(r.body),createWriteStream(file));}
const bytes=await readFile(file);const md5=createHash('md5').update(bytes).digest('hex');
if(md5!=='18968a1376a0fb0155afea4ce832d7fd')throw Error('Gutenprint release archive does not match the published release checksum');
await new Promise((ok,no)=>{const p=spawn('tar.exe',['-xf',file,'-C',dest],{stdio:'inherit'});p.on('exit',c=>c===0?ok():no(Error('Extraction failed')));p.on('error',no)});
await writeFile(join(dest,'gutenprint-source.json'),JSON.stringify({url,md5,sha256:createHash('sha256').update(bytes).digest('hex')},null,2));
console.log('Verified and extracted Gutenprint 5.3.5.');
