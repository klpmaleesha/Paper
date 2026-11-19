import {mkdir,cp,copyFile,readFile,writeFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
const root=resolve(import.meta.dirname,'..'),out=join(root,'deliverables');
const metadata=JSON.parse(await readFile(join(root,'app/build/outputs/apk/debug/output-metadata.json'),'utf8'));
const version=metadata.elements[0].versionName;
if(!/^\d+\.\d+\.\d+$/.test(version))throw Error('Unexpected APK version');
const apkName=`Paper-${version}-debug.apk`;
const staging=join(root,'.tooling',`source-package-${version}`);
await mkdir(out,{recursive:true});await mkdir(staging,{recursive:true});
for(const path of ['app/src','native','.github','driver-research/gutenprint-source.json','app/build.gradle','build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','gradle','tools','.gitignore','.gitattributes','README.md','LICENSE','THIRD_PARTY_NOTICES.md','COMPATIBILITY.md','CONTRIBUTING.md','CHANGELOG.md','PRIVACY.md','ROADMAP.md','VERIFICATION.md']){
 await cp(join(root,path),join(staging,path),{recursive:true});
}
await copyFile(join(root,'app/build/outputs/apk/debug/app-debug.apk'),join(out,apkName));
await new Promise((ok,no)=>{const p=spawn('tar.exe',['-a','-cf',join(out,'Paper-Android-source.zip'),'-C',staging,'.'],{stdio:'inherit'});p.on('exit',c=>c===0?ok():no(Error('Source packaging failed')));p.on('error',no)});
const apk=await readFile(join(out,apkName));const sha=createHash('sha256').update(apk).digest('hex');
await writeFile(join(out,`Paper-${version}-SHA256.txt`),sha+'  '+apkName+'\n');
console.log(JSON.stringify({apk:join(out,apkName),source:join(out,'Paper-Android-source.zip'),bytes:apk.length,sha256:sha},null,2));
