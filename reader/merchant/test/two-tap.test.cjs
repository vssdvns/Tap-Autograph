const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const crypto=require('node:crypto');const {createApp}=require('../server.cjs');
test('two-tap checkout binds first acceptance and autograph to one final order, retries ACK and rejects changed grants',async()=>{
 const {server}=createApp();await new Promise(r=>server.listen(0,'127.0.0.1',r));const base=`http://127.0.0.1:${server.address().port}`;
 try{
 const cookie=(await fetch(base)).headers.get('set-cookie').split(';')[0];const token=(await(await fetch(base+'/api/reader/config')).json()).token;
 const call=async(p,body,reader=false)=>{const r=await fetch(base+p,{method:body===undefined?'GET':'POST',headers:{cookie,'Content-Type':'application/json',...(reader?{authorization:'Bearer '+token}:{})},body:body===undefined?undefined:JSON.stringify(body)});return {status:r.status,data:await r.json()};};
 const s=(await call('/api/checkouts',{cart:[{id:'latte',quantity:1}],address:'127.0.0.1',mode:'two-tap'})).data;
 const context=async()=> (await call('/api/reader/checkout',undefined,true)).data.checkout;
 assert.equal((await call('/api/reader/checkout')).status,403);const c=await context();assert.equal(c.phase,1);assert.equal(c.amount,598);
 assert.equal((await call('/api/checkouts',{cart:[{id:'latte',quantity:1}],address:'127.0.0.1',mode:'two-tap'})).status,409);
 assert.equal((await call('/api/reader/accepted',{id:s.id,nonce:'bad'},true)).status,409);
 assert.equal((await call('/api/reader/accepted',{...c,card:9},true)).status,400);
 await call('/api/reader/accepted',{...c,card:1},true);await call('/api/reader/accepted',{...c,card:1},true);assert.equal((await call(`/api/checkouts/${s.id}`)).data.card,1);assert.equal((await call('/api/reader/accepted',{...c,card:0},true)).status,409);assert.equal((await context()).phase,1);
 await call(`/api/checkouts/${s.id}/tip`,{tip:200});const final=await context();assert.equal(final.phase,2);assert.equal(final.amount,798);assert.equal(final.nonce,c.nonce);
 const packet=fs.readFileSync(__dirname+'/../nfc/fixtures/reader.tai');const body={type:'packet',session:'aa'.repeat(8),simulated:false,sha256:crypto.createHash('sha256').update(packet).digest('hex'),packet:packet.toString('base64'),checkoutId:s.id,nonce:'bad'};
 assert.equal((await call('/api/reader/event',body,true)).status,409);body.nonce=c.nonce;
 assert.equal((await call('/api/reader/event',body,true)).data.checkoutId,s.id);assert.equal((await call('/api/reader/event',body,true)).data.duplicate,true);
 assert.equal((await call(`/api/checkouts/${s.id}`)).data.stage,'animating');await call(`/api/checkouts/${s.id}/complete`,{});
 assert.equal((await call('/api/reader/event',body,true)).data.duplicate,true);assert.equal(await context(),null);
 }finally{await new Promise(r=>server.close(r));}
});
