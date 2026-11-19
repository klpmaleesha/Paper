// Compile the same Gutenprint sources and app settings for an offline Windows smoke test.
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {spawn} from 'node:child_process';
const root=resolve(import.meta.dirname,'..'),source=join(root,'native/gutenprint'),test=join(root,'.tooling/native-test');
await mkdir(test,{recursive:true});
let config=await readFile(join(source,'config.h'),'utf8');config=config.replace('#define HAVE_DECL_STRNDUP 1','#define HAVE_DECL_STRNDUP 0');
await writeFile(join(test,'config.h'),config);
const files=JSON.parse(await readFile(join(source,'sources.json'),'utf8')).map(x=>join(source,x));
const args=['-O1','-DHAVE_CONFIG_H=1','-D_GNU_SOURCE','-I'+test,'-I'+join(source,'include'),'-I'+join(source,'src'),'-I'+join(root,'native'),...files,join(root,'native/encoder_smoke.c'),'-o',join(test,'encoder-smoke.exe')];
const response=join(test,'args.txt');await writeFile(response,args.map(x=>'"'+x.replaceAll('\\','/')+'"').join('\n'));
const env={...process.env,ZIG_GLOBAL_CACHE_DIR:join(process.env.TEMP??test,'paper-zig-global'),ZIG_LOCAL_CACHE_DIR:join(process.env.TEMP??test,'paper-zig-local')};
async function run(exe,args){const code=await new Promise((ok,no)=>{const p=spawn(exe,args,{cwd:root,env,stdio:'inherit'});p.on('exit',ok);p.on('error',no)});if(code!==0)throw Error('Native smoke test failed: '+code)}
await run(join(root,'.tooling/zig-host/ziglang/zig.exe'),['cc','@'+response]);
await run(join(test,'encoder-smoke.exe'),['app/src/main/assets/gutenprint','.tooling/native-test']);
for(let i=0;i<5;i++){
 const bytes=await readFile(join(test,`case-${i}.prn`)),head=bytes.subarray(0,8192).toString('latin1'),tail=bytes.subarray(-4096).toString('latin1');
 if(!head.startsWith('<?xml')||!head.includes('>StartJob<')||!head.includes('>ModeShift<')||!head.includes('\x1b[K')||head.includes('PWGRaster')||!tail.includes('>EndJob<')||bytes.length<1000)throw Error('Incorrect native stream: '+i);
 const text=bytes.toString('latin1');if(text.split('>StartJob<').length!==2||text.split('>EndJob<').length!==2)throw Error('Duplicate job wrapping: '+i);
 console.log(`Validated native case ${i}: ${bytes.length} bytes, Canon raster with one job envelope`);
}
console.log('Native encoder smoke tests passed. This does not test Android JNI, USB, or physical output.');
