import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {existsSync,statSync} from 'node:fs';
import {inflateRawSync} from 'node:zlib';
import {resolve,join,dirname,sep} from 'node:path';
const root=resolve(import.meta.dirname,'..'), dest=join(root,'.tooling','ndk');
const url='https://dl.google.com/android/repository/android-ndk-r27c-windows.zip';
await mkdir(dest,{recursive:true});
async function range(start,end){
 const r=await fetch(url+`?paper_range=${start}-${end}`,{headers:{Range:`bytes=${start}-${end}`,'Accept-Encoding':'identity'},signal:AbortSignal.timeout(180000)});
 if(r.status!==206)throw Error('The Android archive server did not support bounded downloads: '+r.status);
 const cr=r.headers.get('content-range');if(cr!==`bytes ${start}-${end}/${length}`)throw Error('Unexpected archive range: '+cr);
 const b=Buffer.from(await r.arrayBuffer());if(b.length!==end-start+1)throw Error('Incomplete archive range');return b;
}
const probe=await fetch(url+'?paper_probe=0',{headers:{Range:'bytes=0-0','Accept-Encoding':'identity'},signal:AbortSignal.timeout(30000)});
if(probe.status!==206)throw Error('Android NDK range probe: '+probe.status);
const length=Number(probe.headers.get('content-range')?.split('/')[1]);await probe.arrayBuffer();
if(!Number.isSafeInteger(length)||length<65557)throw Error('Archive size unavailable');
const tail=await range(length-65557,length-1);let eocd=-1;for(let p=tail.length-22;p>=0;p--)if(tail.readUInt32LE(p)===0x06054b50){eocd=p;break}
if(eocd<0)throw Error('Archive directory not found; HEAD size='+length+'; tail='+tail.subarray(-40).toString('hex'));
const cdLength=tail.readUInt32LE(eocd+12),cdOffset=tail.readUInt32LE(eocd+16);
const cd=await range(cdOffset,cdOffset+cdLength-1),files=[];
const prefix='android-ndk-r27c/toolchains/llvm/prebuilt/windows-x86_64/';
for(let p=0;p<cd.length;){
 if(cd.readUInt32LE(p)!==0x02014b50)throw Error('Unexpected ZIP directory entry');
 const n=cd.readUInt16LE(p+28),extra=cd.readUInt16LE(p+30),comment=cd.readUInt16LE(p+32);
 const name=cd.subarray(p+46,p+46+n).toString('utf8');
 const rel=name.slice(prefix.length);
 const wanted=name.startsWith(prefix)&&!name.endsWith('/')&&(
  /^bin\/(clang(?:-[0-9]+)?\.exe|ld\.lld\.exe|llvm-readelf\.exe|[^/]+\.dll)$/.test(rel)||
  rel.startsWith('sysroot/usr/include/')||
  /^sysroot\/usr\/lib\/aarch64-linux-android\/(26\/[^/]+|[^/]+\.(?:a|so))$/.test(rel)||
  /^lib\/clang\/[^/]+\/include\//.test(rel)||
  /^lib\/clang\/[^/]+\/lib\/linux\/(?:aarch64\/)?(?:libclang_rt\.builtins.*aarch64.*\.a|libclang_rt\.builtins\.a|libunwind\.a)$/.test(rel)
 );
 if(wanted&&(!existsSync(join(dest,rel))||statSync(join(dest,rel)).size!==cd.readUInt32LE(p+24)))files.push({name,rel,method:cd.readUInt16LE(p+10),crc:cd.readUInt32LE(p+16),compressed:cd.readUInt32LE(p+20),size:cd.readUInt32LE(p+24),offset:cd.readUInt32LE(p+42),nameLength:n});
 p+=46+n+extra+comment;
}
console.log(`Preparing ${files.length} compiler/header files; ${Math.round(files.reduce((s,f)=>s+f.compressed,0)/1048576)} MB compressed (instead of the full NDK).`);
files.sort((a,b)=>a.offset-b.offset);
// Coalesce adjacent files to avoid a network request per header.
const batches=[];for(const file of files){const end=file.offset+30+file.nameLength+65535+file.compressed;let batch=batches.at(-1);if(!batch||file.offset-batch.end>131072||end-batch.start>48*1048576){batch={start:file.offset,end,files:[]};batches.push(batch)}batch.end=Math.max(batch.end,end);batch.files.push(file)}
const table=new Uint32Array(256);for(let i=0;i<256;i++){let c=i;for(let b=0;b<8;b++)c=c&1?0xedb88320^(c>>>1):c>>>1;table[i]=c>>>0}
function crc32(b){let c=0xffffffff;for(const v of b)c=table[(c^v)&255]^(c>>>8);return(c^0xffffffff)>>>0}
for(let i=0;i<batches.length;i++){
 const batch=batches[i];console.log(`Downloading compiler range ${i+1}/${batches.length} (${Math.ceil((batch.end-batch.start)/1048576)} MB)`);
 const data=await range(batch.start,Math.min(batch.end,length-1));
 for(const file of batch.files){
  const p=file.offset-batch.start;if(data.readUInt32LE(p)!==0x04034b50)throw Error('Invalid local ZIP record');
  const start=p+30+data.readUInt16LE(p+26)+data.readUInt16LE(p+28),packed=data.subarray(start,start+file.compressed);
  const bytes=file.method===8?inflateRawSync(packed):file.method===0?packed:null;
  if(!bytes||bytes.length!==file.size||crc32(bytes)!==file.crc)throw Error('Compiler archive integrity failure: '+file.rel);
  const path=resolve(dest,file.rel);if(!path.startsWith(resolve(dest)+sep))throw Error('Invalid archive path');
  await mkdir(dirname(path),{recursive:true});await writeFile(path,bytes);
 }
}
await writeFile(join(dest,'source.json'),JSON.stringify({url,archiveBytes:length,files:files.map(({name,crc,size})=>({name,crc,size}))},null,2));
console.log('Native Android C compiler is ready.');
