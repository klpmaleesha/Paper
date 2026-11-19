// Optional portable Windows compiler used only for the offline native smoke test.
import {writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {spawn} from 'node:child_process';
import {resolve,join} from 'node:path';
const root=resolve(import.meta.dirname,'..'),dest=join(root,'.tooling/zig-host'),archive=join(root,'.tooling/zig-host.zip');
const url='https://files.pythonhosted.org/packages/e0/0c/66f4710aedf5544e15779ff9c6e770322986e15daf9299b1a4df0be19e09/ziglang-0.13.0-py3-none-win_amd64.whl';
const response=await fetch(url);if(!response.ok)throw Error('Compiler download failed: '+response.status);
const bytes=Buffer.from(await response.arrayBuffer());
if(createHash('sha256').update(bytes).digest('hex')!=='b64b2aa936ebbfeecfbb2611df331a5474edebe1246b98c2f56d6ae4d58b71fc')throw Error('Compiler checksum mismatch');
await mkdir(dest,{recursive:true});await writeFile(archive,bytes);
await new Promise((ok,no)=>{const p=spawn('tar.exe',['-xf',archive,'-C',dest],{stdio:'inherit'});p.on('exit',c=>c===0?ok():no(Error('Compiler extraction failed')));p.on('error',no)});
console.log('Offline test compiler ready.');
