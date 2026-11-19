import {readFileSync} from 'node:fs';
import {cp} from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {resolve,join} from 'node:path';
const root=resolve(import.meta.dirname,'..'), dir=join(root,'.tooling');
const c=JSON.parse(readFileSync(join(dir,'environment.json'),'utf8'));
const cache=process.env.PAPER_GRADLE_CACHE??join(process.env.TEMP??dir,'paper-android-gradle-01a1e420');
const env={...process.env,JAVA_HOME:c.java,ANDROID_HOME:c.sdk,ANDROID_SDK_ROOT:c.sdk,ANDROID_USER_HOME:join(dir,'android-user'),GRADLE_USER_HOME:cache,PATH:join(c.java,'bin')+';'+process.env.PATH};
// The isolated Windows toolchain compiles native sources explicitly with build-native.mjs.
// Standard Gradle/CI builds default to rebuilding them through CMake.
const args=['/d','/c',c.gradle,...(process.argv.slice(2).length?process.argv.slice(2):['testDebugUnitTest','assembleDebug','lintDebug']),'-PrebuildNative=false','--console=plain'];
for(let attempt=0;attempt<5;attempt++){
 let output='';
 const code=await new Promise((ok,no)=>{const p=spawn('cmd.exe',args,{cwd:root,env,stdio:['inherit','pipe','pipe']});for(const stream of [p.stdout,p.stderr])stream.on('data',b=>{process.stdout.write(b);output+=b.toString();});p.on('exit',ok);p.on('error',no);});
 if(code===0){process.exitCode=0;break;}
 // Work around Gradle 8's Windows immutable-workspace rename failure. Copy only the
 // exact completed cache workspace named in Gradle's error, never unrelated paths.
 const match=output.match(/Could not move temporary workspace \(([^)]+)\) to immutable location \(([^)]+)\)/);
 const allowed=resolve(cache,'caches').toLowerCase()+'\\';
 if(!match||!match.slice(1).every(x=>resolve(x).toLowerCase().startsWith(allowed))||attempt===4){process.exitCode=code??1;break;}
 console.log('Recovering a completed Gradle cache workspace after a Windows rename failure.');
 await cp(match[1],match[2],{recursive:true});
}
