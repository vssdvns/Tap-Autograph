const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const crypto=require('node:crypto');const {createApp}=require('../server.cjs');
test('NFC checkout receives only a fresh armed transfer; isolates orders and preserves skip/expiry',async()=>{
 let time=Date.now();const {server}=createApp({now:()=>time});await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=`http://127.0.0.1:${server.address().port}`;
 try{
 const page=await fetch(base);const cookie=page.headers.get('set-cookie').split(';')[0];
 const config=await(await fetch(base+'/api/reader/config')).json();
 const call=async(url,body,auth)=>{const r=await fetch(base+url,{method:body===undefined?'GET':'POST',headers:{cookie,'Content-Type':'application/json',...(auth?{authorization:'Bearer '+auth}:{})},body:body===undefined?undefined:JSON.stringify(body)});return {status:r.status,data:await r.json()};};
 const packet=fs.readFileSync(path.join(__dirname,'../nfc/fixtures/reader.tai'));let count=0;
 const transfer=()=>({type:'packet',session:(++count).toString(16).padStart(16,'0'),simulated:false,sha256:crypto.createHash('sha256').update(packet).digest('hex'),packet:packet.toString('base64')});
 const create=async()=> (await call('/api/checkouts',{cart:[{id:'latte',quantity:1}],address:'127.0.0.1',mode:'nfc'})).data;
 const action=(s,a,b={})=>call(`/api/checkouts/${s.id}/${a}`,b);
 const a=await create();assert.equal(a.stage,'paired');assert.equal((await action(a,'arm-nfc')).status,409);
 await action(a,'demo-pay');await action(a,'tip',{tip:100});
 const early=transfer();await call('/api/reader/event',early,config.token);
 await action(a,'arm-nfc');assert.equal((await call(`/api/checkouts/${a.id}`)).data.stage,'autograph');
 await call('/api/reader/event',early,config.token);assert.equal((await call(`/api/checkouts/${a.id}`)).data.stage,'autograph');
 const b=await create();await action(b,'demo-pay');await action(b,'tip',{tip:0});assert.equal((await action(b,'arm-nfc')).status,409);
 const fresh=transfer();const result=await call('/api/reader/event',fresh,config.token);assert.equal(result.data.checkoutId,a.id);
 const received=(await call(`/api/checkouts/${a.id}`)).data;assert.equal(received.stage,'animating');assert.equal(received.total,698);assert.equal(received.autographSource,'nfc');assert.ok(received.autograph.strokes.length);
 assert.equal((await call('/api/reader/state')).data.latest,null);
 await action(a,'complete');assert.equal((await call(`/api/checkouts/${a.id}`)).data.autograph,undefined);
 await action(b,'arm-nfc');await call('/api/reader/event',fresh,config.token);assert.equal((await call(`/api/checkouts/${b.id}`)).data.stage,'autograph');
 time+=60001;await call('/api/reader/event',transfer(),config.token);assert.equal((await call(`/api/checkouts/${b.id}`)).data.stage,'autograph');
 await action(b,'arm-nfc');await action(b,'skip');await call('/api/reader/event',transfer(),config.token);assert.equal((await call(`/api/checkouts/${b.id}`)).data.autographShared,false);
 }finally{await new Promise(r=>server.close(r));}
});
