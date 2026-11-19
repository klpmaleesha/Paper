// Run the platform-independent driver/page-order tests without loading the native UI renderer.
import {readdir,access,readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve,join,delimiter} from 'node:path';
import {homedir} from 'node:os';
import {spawn} from 'node:child_process';
const root=resolve(import.meta.dirname,'..'),dir=join(root,'.tooling');
await mkdir(dir,{recursive:true});
const env=await readFile(join(dir,'environment.json'),'utf8').then(JSON.parse).catch(()=>({java:process.env.JAVA_HOME}));
if(!env.java)throw Error('Set JAVA_HOME or run tools/bootstrap.mjs first.');
const caches=[process.env.GRADLE_USER_HOME??process.env.PAPER_GRADLE_CACHE??join(process.env.TEMP??dir,'paper-android-gradle-01a1e420'),join(homedir(),'.gradle'),join(dir,'gradle-cache')];
async function jar(group,artifact,version){
 for(const cache of caches){
  const base=join(cache,'caches/modules-2/files-2.1',group,artifact,version);
  try{for(const hash of await readdir(base)){const path=join(base,hash,`${artifact}-${version}.jar`);try{await access(path);return path}catch{}}}catch{}
 }
 throw Error(`Missing cached test library: ${group}:${artifact}:${version}`);
}
const dependencies=await Promise.all([jar('org.jetbrains.kotlin','kotlin-stdlib','2.1.20'),jar('org.json','json','20250517'),jar('junit','junit','4.13.2'),jar('org.hamcrest','hamcrest-core','1.3')]);
const cp=[join(root,'app/build/tmp/kotlin-classes/debugUnitTest'),join(root,'app/build/tmp/kotlin-classes/debug'),...dependencies].join(delimiter);
const classes=['app.paper.print.G2010ProfileTest','app.paper.print.DriverProtocolTest','app.paper.print.PrintPlanTest','app.paper.print.UsbReliabilityTest','app.paper.print.PresetsAndCapabilitiesTest','app.paper.print.IppProtocolTest','app.paper.print.IppTransportTest'];
let output='';
const code=await new Promise((ok,no)=>{const p=spawn(join(env.java,'bin',process.platform==='win32'?'java.exe':'java'),['-Dpaper.profile='+join(root,'app/src/main/assets/ivec_g3010_experimental.json'),'-cp',cp,'org.junit.runner.JUnitCore',...classes],{cwd:root,stdio:['ignore','pipe','pipe']});for(const stream of [p.stdout,p.stderr])stream.on('data',b=>{process.stdout.write(b);output+=b});p.on('exit',ok);p.on('error',no)});
await writeFile(join(dir,'protocol-test-result.txt'),output);
process.exitCode=code??1;
