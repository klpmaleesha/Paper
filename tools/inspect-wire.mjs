import {readFile,writeFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
const root=resolve(import.meta.dirname,'..');
const data=await readFile(join(root,'driver-research/captures/g2010-official-test.prn'));
const text=data.toString('latin1');
const tags=['<?xml','BJLSTART','BJLEND','StartJob','SetConfiguration','SetPageConfiguration','SendData','EndJob','PWGRaster','BJRaster','RaS2'];
const summary={bytes:data.length,firstBytes:data.subarray(0,192).toString('hex'),markers:Object.fromEntries(tags.map(tag=>[tag,text.indexOf(tag)])),printableHeader:text.slice(0,2400).replace(/[^\x20-\x7e\r\n]/g,'.')};
console.log(JSON.stringify(summary,null,2));
await writeFile(join(root,'driver-research/captures/official-wire-summary.json'),JSON.stringify(summary,null,2));
