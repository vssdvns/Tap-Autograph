const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const crypto=require('node:crypto');const {createApp}=require('../server.cjs');
async function setup(){let clock=Date.now();const app=createApp({now:()=>clock});await new Promise(r=>app.server.listen(0,'127.0.0.1',r));await new Promise(r=>app.customerServer.listen(0,'127.0.0.1',r));const merchant=`http://127.0.0.1:${app.server.address().port}`,customer=`http://127.0.0.1:${app.customerServer.address().port}`;const cookie=(await fetch(merchant)).headers.get('set-cookie').split(';')[0];
 async function call(base,p,body,headers={}){const r=await fetch(base+p,{method:body===undefined?'GET':'POST',headers:{...headers,...(body===undefined?{}:{'content-type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body)});return {status:r.status,data:await r.json()};}
 const staff=(p,b)=>call(merchant,p,b,{cookie});const display=(await staff('/api/display',{})).data;const user=(p,b)=>call(customer,p,b,{authorization:'Bearer '+display.token});const readerToken=(await call(merchant,'/api/reader/config')).data.token;const reader=(p,b)=>call(merchant,p,b,{authorization:'Bearer '+readerToken});
 return {...app,merchant,customer,cookie,display,staff,user,reader,call,advance:ms=>clock+=ms,close:()=>new Promise(r=>app.server.close(r))};}
test('two ports share a priced draft, protect merchant controls and isolate display capabilities',async()=>{const h=await setup();try{
 assert.equal(h.display.customerPort,h.customerServer.address().port);
 assert.equal((await h.call(h.customer,'/api/display/state')).status,403);
 assert.equal((await h.call(h.customer,'/api/checkouts',{}, {cookie:h.cookie})).status,403);
 assert.equal((await h.call(h.customer,'/api/reader/config')).status,403);
 assert.equal((await h.call(h.customer,'/api/display/cart',{cart:[]},{cookie:h.cookie})).status,403);
 await h.staff('/api/display/cart',{cart:[{id:'latte',quantity:2,price:1}]});const state=(await h.user('/api/display/state')).data;assert.equal(state.subtotal,1100);assert.equal(state.cart[0].price,550);assert.equal(state.total,1196);
 const otherCookie=(await fetch(h.merchant)).headers.get('set-cookie').split(';')[0];const other=(await h.call(h.merchant,'/api/display',{}, {cookie:otherCookie})).data;
 assert.notEqual(other.token,h.display.token);assert.equal(other.cart.length,0);assert.equal((await h.user('/api/display/state')).data.cart.length,1);
 assert.equal((await h.call(h.customer,'/api/display/action',{action:'tip'},{authorization:'Bearer '+h.display.token,origin:'http://evil.invalid'})).status,403);
 await h.staff('/api/display/cart',{cart:[]});assert.equal((await h.user('/api/display/state')).data.total,0);
 }finally{await h.close();}});
test('customer tip and autograph playback finish the same NFC order seen by the cashier',async()=>{const h=await setup();try{
 const cart=[{id:'latte',quantity:1},{id:'croissant',quantity:1}];await h.staff('/api/display/cart',{cart});const checkout=(await h.staff('/api/checkouts',{cart,address:'127.0.0.1',mode:'two-tap',customerDisplay:true})).data;
 assert.equal((await h.user('/api/display/state')).data.checkout.id,checkout.id);assert.equal((await h.staff('/api/display/cart',{cart:[]})).status,409);
 assert.equal((await h.user('/api/display/action',{checkoutId:checkout.id,action:'tip',tip:0})).status,409);
 const context=(await h.reader('/api/reader/checkout')).data.checkout;await h.reader('/api/reader/accepted',{...context,card:1});
 assert.equal((await h.user('/api/display/action',{checkoutId:'stale',action:'tip',tip:0})).status,409);
 assert.equal((await h.user('/api/display/action',{checkoutId:checkout.id,action:'tip',tip:-1})).status,400);
 await h.user('/api/display/action',{checkoutId:checkout.id,action:'tip',tip:190});assert.equal((await h.staff('/api/display',{})).data.checkout.total,1223);
 const packet=fs.readFileSync(__dirname+'/../nfc/fixtures/reader.tai');const event={type:'packet',session:'dd'.repeat(8),simulated:true,sha256:crypto.createHash('sha256').update(packet).digest('hex'),packet:packet.toString('base64'),checkoutId:checkout.id,nonce:context.nonce};
 assert.equal((await h.reader('/api/reader/event',event)).status,200);
 const animation=(await h.user('/api/display/state')).data;assert.equal(animation.checkout.stage,'animating');assert.ok(animation.checkout.autograph);assert.equal(animation.checkout.pairingUrl,undefined);assert.equal(animation.checkout.nfcNonce,undefined);
 await h.user('/api/display/action',{checkoutId:checkout.id,action:'complete'});const receipt=(await h.staff('/api/display',{})).data.checkout;assert.equal(receipt.stage,'completed');assert.equal(receipt.card,1);assert.equal(receipt.autograph,undefined);assert.equal(h.sessions.get(checkout.id).autograph,null);
 assert.equal((await h.reader('/api/reader/event',event)).data.duplicate,true);
 await h.staff('/api/display/cart',{cart:[]});assert.equal((await h.user('/api/display/state')).data.checkout,null);
 }finally{await h.close();}});
test('customer can skip autograph but cannot pay, cancel or change another order',async()=>{const h=await setup();try{
 const cart=[{id:'cookie',quantity:1}];await h.staff('/api/display/cart',{cart});const s=(await h.staff('/api/checkouts',{cart,address:'127.0.0.1',mode:'two-tap',customerDisplay:true})).data;
 const c=(await h.reader('/api/reader/checkout')).data.checkout;await h.reader('/api/reader/accepted',c);
 assert.equal((await h.user('/api/display/action',{checkoutId:s.id,action:'cancel'})).status,400);
 await h.user('/api/display/action',{checkoutId:s.id,action:'tip',tip:0});await h.user('/api/display/action',{checkoutId:s.id,action:'skip'});
 assert.equal((await h.user('/api/display/state')).data.checkout.autographShared,false);
 h.advance(6*60*60*1000+1);assert.equal((await h.user('/api/display/state')).status,403);
 }finally{await h.close();}});

test('selected tip can be confirmed by a fresh reader tap; stale drafts and absent choices are rejected',async()=>{const h=await setup();try{
 const cart=[{id:'latte',quantity:1}];await h.staff('/api/display/cart',{cart});const s=(await h.staff('/api/checkouts',{cart,address:'127.0.0.1',mode:'two-tap',customerDisplay:true})).data;
 const c=(await h.reader('/api/reader/checkout')).data.checkout;await h.reader('/api/reader/accepted',c);
 assert.equal((await h.reader('/api/reader/confirm-tip',{id:s.id,nonce:c.nonce,tipRevision:0})).status,409);
 assert.equal((await h.user('/api/display/action',{checkoutId:s.id,action:'tip-select',tip:-1})).status,400);
 const choice=(await h.user('/api/display/action',{checkoutId:s.id,action:'tip-select',tip:110})).data.checkout;
 assert.equal(choice.stage,'tip');assert.equal(choice.tip,0);assert.equal(choice.pendingTip,110);
 const pending=(await h.reader('/api/reader/checkout')).data.checkout;assert.equal(pending.tipPending,true);
 await h.user('/api/display/action',{checkoutId:s.id,action:'tip-select',tip:0});
 assert.equal((await h.reader('/api/reader/confirm-tip',{id:s.id,nonce:c.nonce,tipRevision:pending.tipRevision})).status,409);
 await h.user('/api/display/action',{checkoutId:s.id,action:'tip-select',tip:null});assert.equal((await h.reader('/api/reader/checkout')).data.checkout.tipPending,undefined);
 const latest=(await h.user('/api/display/action',{checkoutId:s.id,action:'tip-select',tip:0})).data.checkout;
 assert.equal((await h.user('/api/reader/confirm-tip',{id:s.id,nonce:c.nonce,tipRevision:latest.tipRevision})).status,403);
 const ready=await h.reader('/api/reader/confirm-tip',{id:s.id,nonce:c.nonce,tipRevision:latest.tipRevision});assert.equal(ready.status,200);assert.equal(ready.data.checkout.phase,2);
 const shown=(await h.user('/api/display/state')).data.checkout;assert.equal(shown.stage,'autograph');assert.equal(shown.tip,0);assert.equal(shown.total,598);
 assert.equal((await h.user('/api/display/action',{checkoutId:s.id,action:'tip-select',tip:300})).status,409);
 }finally{await h.close();}});
