import {mkdir, writeFile, createWriteStream, existsSync, readdirSync, unlinkSync, renameSync} from 'node:fs';
import {promisify} from 'node:util';
import {pipeline} from 'node:stream/promises';
import {Readable} from 'node:stream';
import {spawn} from 'node:child_process';
import {join, resolve} from 'node:path';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
const root=resolve(import.meta.dirname,'..'), dir=join(root,'.tooling');
await promisify(mkdir)(dir,{recursive:true});
async function download(url,file,sha){
 if(existsSync(file)&&sha&&createHash('sha256').update(readFileSync(file)).digest('hex')!==sha)unlinkSync(file);
 if(!existsSync(file)){console.log('Downloading',new URL(url).hostname, file.split(/[\\/]/).at(-1));const r=await fetch(url);if(!r.ok)throw Error(`${r.status}: ${url}`);await pipeline(Readable.fromWeb(r.body),createWriteStream(file+'.part'));renameSync(file+'.part',file);}
 if(sha&&createHash('sha256').update(readFileSync(file)).digest('hex')!==sha)throw Error('Download checksum mismatch: '+file);
}
function run(exe,args,opts={}){return new Promise((ok,no)=>{const p=spawn(exe,args,{stdio:'inherit',...opts});p.on('exit',c=>c===0?ok():no(Error(exe+' exited '+c)));p.on('error',no);});}
async function unzip(zip,dest){await promisify(mkdir)(dest,{recursive:true});await run('tar.exe',['-xf',zip,'-C',dest]);}
const jdkDir=join(dir,'jdk'), gradleDir=join(dir,'gradle-8.11.1'), sdk=join(dir,'sdk');
await Promise.all([
 (async()=>{if(existsSync(jdkDir)&&readdirSync(jdkDir).some(x=>x.startsWith('jdk-')))return;const a=await (await fetch('https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows')).json();const pkg=a[0].binary.package;const zip=join(dir,'jdk.zip');await download(pkg.link,zip,pkg.checksum);await unzip(zip,jdkDir);})(),
 (async()=>{if(existsSync(gradleDir))return;const url='https://services.gradle.org/distributions/gradle-8.11.1-bin.zip';const sha=(await (await fetch(url+'.sha256')).text()).trim();const zip=join(dir,'gradle.zip');await download(url,zip,sha);await unzip(zip,dir);})(),
 (async()=>{if(existsSync(join(sdk,'cmdline-tools','latest','bin','sdkmanager.bat')))return;const xml=await(await fetch('https://dl.google.com/android/repository/repository2-1.xml')).text();const block=xml.match(/<remotePackage path="cmdline-tools;latest"[\s\S]*?<\/remotePackage>/)?.[0];const archive=block?.match(/<archive>[\s\S]*?<\/archive>/g)?.find(x=>x.includes('<host-os>windows</host-os>'));const url=archive?.match(/<url>([^<]+)<\/url>/)?.[1];if(!url)throw Error('Cannot locate official SDK command line tools');const zip=join(dir,'sdk-windows.zip');await download('https://dl.google.com/android/repository/'+url,zip);await unzip(zip,join(dir,'sdk-unpack'));await promisify(mkdir)(join(sdk,'cmdline-tools'),{recursive:true});const {cp}=await import('node:fs/promises');await cp(join(dir,'sdk-unpack','cmdline-tools'),join(sdk,'cmdline-tools','latest'),{recursive:true});})()
]);
const java=join(jdkDir,readdirSync(jdkDir).find(x=>x.startsWith('jdk-')));
const env={...process.env,JAVA_HOME:java,ANDROID_HOME:sdk,ANDROID_SDK_ROOT:sdk,GRADLE_USER_HOME:join(dir,'gradle-cache'),ANDROID_USER_HOME:join(dir,'android-user'),PATH:join(java,'bin')+';'+process.env.PATH};
await promisify(writeFile)(join(root,'local.properties'),'sdk.dir='+sdk.replaceAll('\\','/')+'\n');
await promisify(writeFile)(join(dir,'environment.json'),JSON.stringify({java,sdk,gradle:join(gradleDir,'bin','gradle.bat')}));
console.log('Installing Android SDK 35 and build tools from official Google archives');
const repository=await(await fetch('https://dl.google.com/android/repository/repository2-1.xml')).text();
for(const [pkg,target] of [['platforms;android-35','platforms/android-35'],['build-tools;35.0.0','build-tools/35.0.0']]){
 const block=repository.split('<remotePackage path="'+pkg+'"')[1]?.split('</remotePackage>')[0];
 if(!block)throw Error('SDK package unavailable: '+pkg);
 const archives=block.match(/<archive>[\s\S]*?<\/archive>/g);
 const archive=archives.find(a=>a.includes('<host-os>windows</host-os>'))??archives.find(a=>!a.includes('<host-os>'));
 const url=archive.match(/<url>([^<]+)<\/url>/)[1];const sha=archive.match(/<checksum[^>]*>([^<]+)<\/checksum>/)[1];
 const zip=join(dir,pkg.replaceAll(';','-')+'.zip');await download('https://dl.google.com/android/repository/'+url,zip);
 if(createHash('sha1').update(readFileSync(zip)).digest('hex')!==sha)throw Error('SDK checksum mismatch');
 const staging=join(dir,pkg.replaceAll(';','-')+'-unpack');await unzip(zip,staging);
 const child=readdirSync(staging,{withFileTypes:true}).find(x=>x.isDirectory()).name;
 const {cp}=await import('node:fs/promises');await cp(join(staging,child),join(sdk,target),{recursive:true});
}
await promisify(mkdir)(join(sdk,'licenses'),{recursive:true});
await promisify(writeFile)(join(sdk,'licenses','android-sdk-license'),'8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n');
console.log('Android build environment ready. Run node tools/build.mjs');
