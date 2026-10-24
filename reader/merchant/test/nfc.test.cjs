const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const http=require('node:http');
const {receive}=require('../nfc/reader.cjs');const {createApp}=require('../server.cjs');
const fixture=()=>JSON.parse(fs.readFileSync(path.join(__dirname,'../nfc/fixtures/reader.json'),'utf8'));
test('Kotlin reader transcript reassembles exact packet with retry/reconnect',()=>{const result=receive(fixture());assert.deepEqual(result.packet,fs.readFileSync(path.join(__dirname,'../nfc/fixtures/reader.tai')));assert.equal(result.summary.retries,1);assert.equal(result.summary.reconnects,1);});
test('large multi-chunk Kotlin transfer matches exact bytes',()=>{const t=JSON.parse(fs.readFileSync(path.join(__dirname,'../nfc/fixtures/large.json'),'utf8'));const result=receive(t);assert.ok(result.summary.chunks>26);assert.deepEqual(result.packet,fs.readFileSync(path.join(__dirname,'../nfc/fixtures/large.tai')));});
test('incomplete, stale, conflicting and changed transcripts are rejected',()=>{
 const missing=fixture();missing.exchanges.pop();assert.throws(()=>receive(missing));
 const stale=fixture();stale.exchanges[1].command=stale.exchanges[1].command.slice(0,10)+'00'.repeat(8)+stale.exchanges[1].command.slice(26);assert.throws(()=>receive(stale));
 const conflict=fixture();const e=conflict.exchanges[2];e.response=e.response.slice(0,20)+'FF'+e.response.slice(22);assert.throws(()=>receive(conflict));
 const changed=fixture();changed.exchanges[0].response='FF'+changed.exchanges[0].response.slice(2);assert.throws(()=>receive(changed));
 const badHash=fixture();const chunk=badHash.exchanges.at(-2);chunk.response=chunk.response.slice(0,20)+(chunk.response.slice(20,22)==='FF'?'00':'FF')+chunk.response.slice(22);assert.throws(()=>receive(badHash),/hash mismatch/);
 const after=fixture();after.exchanges.push(after.exchanges[0]);assert.throws(()=>receive(after));
 assert.throws(()=>receive({protocol:'TAP1',simulated:true,exchanges:Array(801).fill({})}));
});
test('localhost endpoint imports without retaining handwriting or changing checkout',async()=>{
 const {server,sessions}=createApp();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base=`http://127.0.0.1:${server.address().port}`;
 try {const page=await fetch(base+'/nfc-lab');assert.equal(page.status,200);const cookie=page.headers.get('set-cookie').split(';')[0];
 const send=body=>fetch(base+'/api/nfc-lab',{method:'POST',headers:{cookie,'Content-Type':'application/json'},body:JSON.stringify(body)});
 const result=await send(fixture());assert.equal(result.status,200);assert.equal((await result.json()).summary.simulated,true);assert.equal(sessions.size,0);
 assert.equal((await send({})).status,400);
 const cross=await fetch(base+'/api/nfc-lab',{method:'POST',headers:{cookie,'Content-Type':'application/json',Origin:'http://evil.test'},body:'{}'});assert.equal(cross.status,403);
 }finally {await new Promise(resolve=>server.close(resolve));}
});
