const {test}=require('node:test');const http=require('node:http');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const crypto=require('node:crypto');const {execFile}=require('node:child_process');const {promisify}=require('node:util');
const {createReaderHub}=require('../nfc/usb-hub.cjs');const {createApp}=require('../server.cjs');
const packet=fs.readFileSync(path.join(__dirname,'../nfc/fixtures/large.tai'));
const body=()=>({type:'packet',session:'0102030405060708',simulated:true,sha256:crypto.createHash('sha256').update(packet).digest('hex'),packet:packet.toString('base64')});
test('USB hub validates, deduplicates and expires retained packet',()=>{
 let time=1000;const hub=createReaderHub({now:()=>time});assert.equal(hub.event(body()).duplicate,false);const id=hub.snapshot().latest.id;
 assert.equal(hub.event(body()).duplicate,true);assert.equal(hub.snapshot().latest.id,id);
 assert.throws(()=>hub.event({...body(),sha256:'0'.repeat(64)}));assert.throws(()=>hub.event({...body(),packet:'A'.repeat(90000)}));
 assert.throws(()=>hub.event({...body(),simulated:undefined}));assert.throws(()=>hub.event({type:'state',code:'arbitrary',simulated:false}));
 time+=60000;assert.equal(hub.snapshot().latest,null);
 assert.equal(hub.event(body()).duplicate,true); // Clearing/expiry doesn't re-play the same consent grant.
});
test('USB hub preserves last diagnostic issue on disconnect and clears on new connection',()=>{
 const hub=createReaderHub();hub.event({type:'state',code:'pn532_missing',simulated:false});hub.event({type:'state',code:'disconnected',simulated:false});assert.equal(hub.snapshot().lastIssue,'pn532_missing');hub.event({type:'state',code:'connected',simulated:false});assert.equal(hub.snapshot().lastIssue,null);
});
test('Python bridge automatically delivers the Kotlin packet through HTTP without a file import',async()=>{
 const {server,sessions}=createApp();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const url=`http://127.0.0.1:${server.address().port}`;
 try{
 const page=await fetch(url+'/nfc-lab');const cookie=page.headers.get('set-cookie').split(';')[0];
 const result=await promisify(execFile)(process.env.TAP_PYTHON||'python3',['bridge/reader.py','--simulate','nfc/fixtures/large.json','--server',url],{cwd:path.join(__dirname,'..'),timeout:15000});assert.match(result.stdout,/"chunks": 103/);assert.match(result.stdout,/"retries": 1/);
 const state=await (await fetch(url+'/api/reader/state',{headers:{cookie}})).json();assert.equal(state.latest.bytes,19660);assert.equal(state.latest.simulated,true);assert.equal(sessions.size,0);
 const unauth=await fetch(url+'/api/reader/event',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body())});assert.equal(unauth.status,403);
 const denied=await fetch(url+'/api/reader/config',{headers:{Origin:'http://evil.test'}});assert.equal(denied.status,403);
 const rebinding=await new Promise((resolve,reject)=>{http.get(url+'/api/reader/config',{headers:{Host:'evil.test'}},res=>{res.resume();resolve(res.statusCode);}).on('error',reject);});assert.equal(rebinding,403);
 assert.equal((await fetch(url+'/api/reader/clear',{method:'POST',headers:{cookie}})).status,200);
 const cleared=await(await fetch(url+'/api/reader/state',{headers:{cookie}})).json();assert.equal(cleared.latest,null);
 }finally{await new Promise(resolve=>server.close(resolve));}
});
