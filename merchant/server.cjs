'use strict';
const http = require('node:http');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {createReaderHub,loopbackRequest}=require('./nfc/usb-hub.cjs');
const {receive:receiveNfcTranscript}=require('./nfc/reader.cjs');
const qrcode = require('./vendor/qrcode.cjs');
const TTL = 10 * 60 * 1000;
const CATALOG = [{ id: 'latte', name: 'Iced latte', detail: 'Espresso · milk · ice', price: 550 }, { id: 'croissant', name: 'Butter croissant', detail: 'Freshly baked · golden layers', price: 400 }, { id: 'matcha', name: 'Matcha latte', detail: 'Ceremonial green tea · milk', price: 625 }, { id: 'cookie', name: 'Chocolate cookie', detail: 'Dark chocolate · sea salt', price: 350 }];
const token = () => crypto.randomBytes(32).toString('hex');
const fail = (status, message) => { const e = new Error(message); e.status = status; throw e; };
function localAddress(address) { return /^(127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(address); }
function addresses() {
  const result = [];
  let interfaces; try { interfaces = os.networkInterfaces(); } catch { return result; }
  for (const [name, items] of Object.entries(interfaces)) for (const item of items || [])
    if (item.family === 'IPv4' && !item.internal && localAddress(item.address)) result.push({ name, address: item.address });
  return result;
}
function validateAutograph(a) {
  if (!a || a.schema !== 1 || typeof a.id !== 'string' || a.id.length > 100 || !Number.isFinite(a.aspect) || a.aspect <= 0 || a.aspect >= 20 || !Number.isFinite(a.width) || a.width < .001 || a.width > .04 || !Number.isSafeInteger(a.color) || a.color < 0 || a.color > 0xffffffff || !Array.isArray(a.strokes) || !a.strokes.length || a.strokes.length > 1000) return false;
  let count = 0, previous = -1;
  for (const stroke of a.strokes) {
    if (!Array.isArray(stroke.points) || !stroke.points.length) return false;
    for (const p of stroke.points) {
      if (++count > 30000 || !Number.isFinite(p.x) || p.x < 0 || p.x > 1 || !Number.isFinite(p.y) || p.y < 0 || p.y > 1 || !Number.isSafeInteger(p.time) || p.time < previous || !Number.isFinite(p.pressure) || p.pressure < 0 || p.pressure > 2 || !Number.isInteger(p.tool)) return false;
      previous = p.time;
    }
  }
  return true;
}
function cartItems(cart) {
  if (!Array.isArray(cart) || !cart.length || cart.length > CATALOG.length) fail(400, 'Choose at least one item.');
  const seen = new Set();
  return cart.map(line => {
    const item = CATALOG.find(p => p.id === line.id);
    if (!item || seen.has(line.id) || !Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > 10) fail(400, 'Invalid item or quantity.');
    seen.add(line.id); return { id: item.id, name: item.name, quantity: line.quantity, price: item.price };
  });
}
function createApp({ now = () => Date.now(), port = 8787, customerPort = port + 1 } = {}) {
  const sessions = new Map();
  const displays = new Map();
  function displayForOwner(key){return [...displays.values()].find(d=>d.owner===key);}
  function displayCheckout(d){return d.checkoutId?sessions.get(d.checkoutId):null;}
  function displayAccess(req){
    const credential=/^Bearer ([a-f0-9]{64})$/.exec(req.headers.authorization||'')?.[1];
    const d=displays.get(credential);
    if(!d||d.expiresAt<=now())fail(403,'Display link expired. Ask the cashier for a new link.');
    return d;
  }
  function displaySnapshot(d){
    const s=displayCheckout(d);
    const valid=s&&s.expiresAt>now();
    const checkout=valid?snapshot(s):s?{id:s.id,stage:'expired',revision:s.revision}:null;
    if(valid&&s.stage==='animating'){checkout.autograph=s.autograph;checkout.speed=s.speed;checkout.reducedMotion=s.reducedMotion;}
    return {connected:!!d.lastSeen&&d.lastSeen>now()-2500,revision:`${d.revision}:${s?.revision||0}:${valid?'live':'idle'}`,cart:d.items,subtotal:d.subtotal,tax:d.tax,total:d.subtotal+d.tax,checkout};
  }
  const readerHub = createReaderHub({now});
  let nfcTarget=null,twoTapTarget=null;
  function disarm(s){if(twoTapTarget===s.id&&['completed','cancelled'].includes(s.stage))twoTapTarget=null;if(nfcTarget?.id===s.id)nfcTarget=null;s.nfcArmedUntil=0;}
  const cleanup = setInterval(() => { for (const [id, s] of sessions) if (s.expiresAt <= now()) { s.autograph = null; sessions.delete(id); } for(const [key,d] of displays)if(d.expiresAt<=now())displays.delete(key); }, 30000).unref();
  function get(id) { const s = sessions.get(id); if (!s) fail(404, 'Checkout not found. Start a new one.'); if (s.expiresAt <= now()) { s.autograph = null; sessions.delete(id); fail(410, 'Checkout expired. Start a new checkout.'); } return s; }
  function snapshot(s, merchant = false) {
    const result = { id: s.id, merchant: 'Harbor Coffee', stage: s.stage, revision: s.revision, expiresAt: s.expiresAt, items: s.items, subtotal: s.subtotal, tax: s.tax, tip: s.tip, total: s.subtotal + s.tax + s.tip, card: s.card, mode:s.mode, nfcArmedUntil:s.nfcArmedUntil||0, autographSource:s.autographSource||null, autographShared: s.autographShared, completedAt: s.completedAt };
    if (merchant) { result.pairingUrl = s.pairingUrl; if (s.stage === 'animating') result.autograph = s.autograph; result.speed = s.speed; result.reducedMotion = s.reducedMotion; }
    return result;
  }
  function owner(req) { return /(?:^|;\s*)merchant=([a-f0-9]{64})(?:;|$)/.exec(req.headers.cookie || '')?.[1]; }
  function merchant(req, s) { if (!owner(req) || owner(req) !== s.owner) fail(403, 'This checkout belongs to another merchant window.'); }
  function phone(req, s) { if (!s.deviceToken || req.headers.authorization !== 'Bearer ' + s.deviceToken) fail(403, 'Phone is not paired with this checkout.'); }
  function change(s, stage) { if(stage!=='autograph')disarm(s);if(['completed','cancelled'].includes(stage)&&twoTapTarget===s.id)twoTapTarget=null; s.stage = stage; s.revision++; }
  function complete(s) { s.completedAt = now(); s.autograph = null; change(s, 'completed'); }
  function originCheck(req) {
    if (!req.headers.origin) return; // Native Android requests do not carry browser Origin.
    let origin; try { origin = new URL(req.headers.origin); } catch { fail(403, 'Invalid origin.'); }
    if (origin.host !== req.headers.host || origin.protocol !== 'http:') fail(403, 'Cross-origin requests are not supported.');
  }
  async function jsonBody(req) {
    if (!String(req.headers['content-type']).startsWith('application/json')) fail(415, 'Send JSON.');
    let size = 0; const chunks = [];
    for await (const chunk of req) { size += chunk.length; if (size > 2500000) fail(413, 'Autograph is too large. Try fewer strokes.'); chunks.push(chunk); }
    try { return JSON.parse(Buffer.concat(chunks).toString()); } catch { fail(400, 'Invalid JSON.'); }
  }
  const send = (res, status, value, type = 'application/json') => { res.writeHead(status, { 'Content-Type': type + '; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer', 'Cross-Origin-Resource-Policy': 'same-origin' }); res.end(type === 'application/json' ? JSON.stringify(value) : value); };
  const handler = async (req, res) => {
    try {
      const url = new URL(req.url, 'http://localhost'); const parts = url.pathname.split('/').filter(Boolean);
      if(req.customerSurface && !['/','/customer','/display.js','/counter.css','/ink.js','/api/display/state','/api/display/action'].includes(url.pathname))fail(403,'Customer display only. Use the cashier port for merchant controls.');
      if (req.method === 'GET' && ['/','/classic','/customer','/cashier.js','/display.js','/counter.css','/app.js','/style.css','/pair','/pair.js','/ink.js','/simulator.html','/simulator.js','/nfc-lab','/nfc-lab.js','/nfc-lab.css'].includes(url.pathname)) {
        if (!req.customerSurface && ['/','/classic','/nfc-lab'].includes(url.pathname) && !owner(req)) res.setHeader('Set-Cookie', `merchant=${token()}; HttpOnly; SameSite=Strict; Path=/`);
        res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        const file = url.pathname === '/' ? (req.customerSurface?'customer.html':'cashier.html') : url.pathname === '/classic' ? 'index.html' : url.pathname === '/customer' ? 'customer.html' : url.pathname === '/nfc-lab' ? 'nfc-lab.html' : url.pathname === '/pair' ? 'pair.html' : url.pathname.slice(1);
        const type = file.endsWith('.html') ? 'text/html' : file.endsWith('.css') ? 'text/css' : 'text/javascript';
        return send(res, 200, fs.readFileSync(path.join(__dirname,'public',file),'utf8'), type);
      }
      if (req.method === 'GET' && url.pathname === '/api/config') return send(res,200,{ catalog: CATALOG, addresses: addresses(), port: Number(req.headers.host?.split(':').at(-1)) || port, ttl: TTL });
      if (req.method === 'POST') originCheck(req);
      if(req.method==='POST'&&url.pathname==='/api/display'){
        const key=owner(req);if(!key)fail(403,'Open the cashier page first.');
        let d=displayForOwner(key);
        if(!d){if(displays.size>=200)fail(429,'Too many displays. Restart the demo server.');d={owner:key,token:token(),items:[],subtotal:0,tax:0,revision:1,checkoutId:null,expiresAt:now()+6*60*60*1000};displays.set(d.token,d);}
        d.expiresAt=now()+6*60*60*1000;
        return send(res,200,{token:d.token,customerPort:customerServer.address()?.port||customerPort,addresses:addresses(),...displaySnapshot(d)});
      }
      if(req.method==='POST'&&url.pathname==='/api/display/cart'){
        const d=displayForOwner(owner(req));if(!d)fail(403,'Open the cashier page first.');
        const current=displayCheckout(d);if(current&&current.expiresAt>now()&&!['completed','cancelled'].includes(current.stage))fail(409,'Finish or cancel the checkout before editing its cart.');
        const b=await jsonBody(req);const items=Array.isArray(b.cart)&&b.cart.length===0?[]:cartItems(b.cart);
        d.items=items;d.subtotal=items.reduce((sum,i)=>sum+i.price*i.quantity,0);d.tax=Math.round(d.subtotal*.0875);d.checkoutId=null;d.revision++;
        return send(res,200,displaySnapshot(d));
      }
      if(req.method==='GET'&&url.pathname==='/api/display/state'){const d=displayAccess(req);d.lastSeen=now();return send(res,200,displaySnapshot(d));}
      if(req.method==='POST'&&url.pathname==='/api/display/action'){
        const d=displayAccess(req),b=await jsonBody(req),s=get(d.checkoutId);
        if(b.checkoutId!==s.id)fail(409,'This order changed. Please check the screen again.');
        if(b.action==='tip'){
          if(s.stage!=='tip')fail(409,'Tip choice is not available now.');
          if(!Number.isInteger(b.tip)||b.tip<0||b.tip>10000)fail(400,'Tip must be between $0 and $100.');
          s.tip=b.tip;change(s,'autograph');
        }else if(b.action==='complete'){
          if(!['animating','completed'].includes(s.stage))fail(409,'No autograph is ready.');
          if(s.stage!=='completed')complete(s);
        }else if(b.action==='skip'){
          if(!['autograph','completed'].includes(s.stage))fail(409,'Confirm a tip before finishing without autograph.');
          if(s.stage!=='completed'){s.autographShared=false;complete(s);}
        }else fail(400,'Customer action is not available.');
        return send(res,200,displaySnapshot(d));
      }
      if (url.pathname.startsWith('/api/reader/')) {
        if (!loopbackRequest(req)) fail(403,'Reader bridge is available only on localhost.');
        if (req.method === 'GET' && url.pathname === '/api/reader/config') {
          if (req.headers.origin && new URL(req.headers.origin).host !== req.headers.host) fail(403,'Cross-origin reader access is not supported.');
          return send(res,200,{token:readerHub.token});
        }
        if(url.pathname==='/api/reader/checkout'){
          if(req.headers.authorization!=='Bearer '+readerHub.token)fail(403,'Reader token required.');
          const s=sessions.get(twoTapTarget);
          if(!s||s.expiresAt<=now()||!['paired','tip','autograph'].includes(s.stage))return send(res,200,{checkout:null});
          return send(res,200,{checkout:{id:s.id,nonce:s.nfcNonce,amount:s.subtotal+s.tax+s.tip,phase:s.stage==='autograph'?2:1}});
        }
        if(req.method==='POST'&&url.pathname==='/api/reader/accepted'){
          if(req.headers.authorization!=='Bearer '+readerHub.token)fail(403,'Reader token required.');
          const b=await jsonBody(req),s=get(b.id);
          if(twoTapTarget!==s.id||s.mode!=='two-tap'||b.nonce!==s.nfcNonce)fail(409,'Checkout changed.');
          if(b.card!==undefined&&![0,1].includes(b.card))fail(400,'Invalid demo card.');
          if(s.stage==='paired'){s.card=b.card??0;change(s,'tip');}else if(!['tip','autograph'].includes(s.stage))fail(409,'Checkout ended.');
          else if(b.card!==undefined&&b.card!==s.card)fail(409,'Demo card changed.');
          return send(res,200,{accepted:true});
        }
        if(req.method==='POST'&&url.pathname==='/api/reader/skip-checkout'){
          if(req.headers.authorization!=='Bearer '+readerHub.token)fail(403,'Reader token required.');
          const b=await jsonBody(req),s=get(b.id);
          if(twoTapTarget!==s.id||b.nonce!==s.nfcNonce||s.stage!=='autograph')fail(409,'Checkout changed.');
          complete(s);return send(res,200,{accepted:true});
        }
        if (req.method === 'GET' && url.pathname === '/api/reader/state') {
          if (!owner(req)) fail(403,'Open the reader lab first.');
          return send(res,200,readerHub.snapshot());
        }
        if (req.method === 'POST' && url.pathname === '/api/reader/clear') {
          if (!owner(req)) fail(403,'Open the reader lab first.');
          readerHub.clear();return send(res,200,{cleared:true});
        }
        if (req.method === 'POST' && url.pathname === '/api/reader/event') {
          if (req.headers.authorization !== 'Bearer '+readerHub.token) fail(403,'Reader bridge token is invalid. Restart the bridge.');
          const body=await jsonBody(req);let result;
          let bound=null;
          if(body.checkoutId){
            bound=get(body.checkoutId);
            if(bound.mode==='two-tap'&&body.nonce===bound.nfcNonce&&bound.nfcPacketKey===body.session+':'+body.sha256)return send(res,200,{accepted:true,duplicate:true,checkoutId:bound.id});
            if(twoTapTarget!==bound.id||bound.mode!=='two-tap'||bound.stage!=='autograph'||body.nonce!==bound.nfcNonce)fail(409,'NFC checkout changed or ended.');
          }
          try {result=readerHub.event(body);} catch(error){fail(400,error.message);}
          if(body.type==='packet'&&!result.duplicate&&bound){
            bound.nfcPacketKey=body.session+':'+body.sha256;bound.autograph=readerHub.snapshot().latest.autograph;bound.autographShared=true;bound.autographSource=body.simulated?'simulated-reader':'nfc';change(bound,'animating');readerHub.clear();result.checkoutId=bound.id;
          }
          if(body.type==='packet'&&!result.duplicate&&!bound&&nfcTarget){
            const target=nfcTarget;nfcTarget=null;const s=sessions.get(target.id);
            if(s&&s.stage==='autograph'&&s.expiresAt>now()&&target.until>now()){
              s.autograph=readerHub.snapshot().latest.autograph;s.autographShared=true;
              s.autographSource=body.simulated?'simulated-reader':'nfc';change(s,'animating');readerHub.clear();
              result.checkoutId=s.id;
            }
          }
          return send(res,200,result);
        }
        fail(404,'Unknown reader endpoint.');
      }
      if (req.method === 'POST' && url.pathname === '/api/nfc-lab') {
        if (!['127.0.0.1','::1','::ffff:127.0.0.1'].includes(req.socket.remoteAddress) || !owner(req)) fail(403,'Open the NFC lab on this laptop using localhost.');
        const body=await jsonBody(req);
        let received;try { received=receiveNfcTranscript(body); } catch(error) { fail(400,error.message); }
        return send(res,200,{autograph:received.autograph,summary:received.summary});
      }
      if (req.method === 'POST' && url.pathname === '/api/checkouts') {
        const key = owner(req); if (!key) fail(403,'Open the merchant page first.');
        if ([...sessions.values()].filter(s => s.owner === key && s.expiresAt > now() && !['completed','cancelled'].includes(s.stage)).length >= 5) fail(429,'Finish or cancel an existing checkout first.');
        if (sessions.size >= 200) fail(429,'Too many checkouts. Restart the demo server.');
        const body = await jsonBody(req); const items = cartItems(body.cart);
        const mode=['nfc','two-tap'].includes(body.mode)?body.mode:'wifi';
        if(mode!=='wifi'&&!loopbackRequest(req))fail(403,'Open NFC checkout on localhost.');
        const ip = body.address;
        if (!addresses().some(a => a.address === ip) && !['127.0.0.1','10.0.2.2'].includes(ip)) fail(400,'Select an address belonging to this laptop.');
        const id = crypto.randomUUID(), pairToken = token(), subtotal = items.reduce((n,i) => n+i.price*i.quantity,0);
        const actualPort = Number(req.headers.host?.split(':').at(-1)) || port;
        const base = `http://${ip}:${actualPort}`;
        const s = { id, owner:key, pairToken, claimId:null, deviceToken:null, stage:mode!=='wifi'?'paired':'awaiting', mode, revision:1, items, subtotal, tax:Math.round(subtotal*.0875), tip:0, expiresAt:now()+TTL, card:0, autograph:null, autographShared:false, completedAt:null, speed:1, reducedMotion:false, pairingUrl:`${base}/pair#id=${id}&token=${pairToken}` };
        if(mode==='two-tap'){
          const prior=sessions.get(twoTapTarget);
          if(prior&&prior.expiresAt>now()&&!['completed','cancelled'].includes(prior.stage))fail(409,'Finish or cancel the active two-tap order first.');
          if(nfcTarget&&nfcTarget.until>now())fail(409,'Another checkout reserved the reader.');
          s.nfcNonce=crypto.randomBytes(16).toString('hex');twoTapTarget=id;
        }
        sessions.set(id,s);
        if(body.customerDisplay===true){const d=displayForOwner(key);if(!d){sessions.delete(id);disarm(s);fail(409,'Connect a customer display first.');}d.checkoutId=id;d.revision++;}
        return send(res,201,snapshot(s,true));
      }
      if (parts[0] === 'api' && parts[1] === 'checkouts' && parts[2]) {
        const s = get(parts[2]); merchant(req,s);
        if (req.method === 'GET' && parts.length === 3) return send(res,200,snapshot(s,true));
        if (req.method === 'GET' && parts[3] === 'qr') {
          const qr = qrcode(0,'M'); qr.addData(s.pairingUrl); qr.make();
          return send(res,200,qr.createSvgTag({ cellSize:5, margin:20, scalable:true }), 'image/svg+xml');
        }
        if(req.method==='POST'&&parts[3]==='demo-pay'){
          if(s.mode!=='nfc'||s.stage!=='paired')fail(409,'Demo card is not available now.');
          change(s,'tip');return send(res,200,snapshot(s,true));
        }
        if(req.method==='POST'&&parts[3]==='arm-nfc'){
          if(!loopbackRequest(req))fail(403,'Open NFC checkout on localhost.');
          if(s.mode!=='nfc'||s.stage!=='autograph')fail(409,'Confirm the final total in NFC mode first.');
          if(twoTapTarget&&sessions.get(twoTapTarget)?.expiresAt>now())fail(409,'Finish the two-tap checkout first.');
          if(nfcTarget&&nfcTarget.until>now()&&nfcTarget.id!==s.id)fail(409,'Another checkout is waiting for NFC. Finish or cancel it first.');
          readerHub.clear();s.nfcArmedUntil=Math.min(now()+60000,s.expiresAt);
          nfcTarget={id:s.id,until:s.nfcArmedUntil};s.revision++;
          return send(res,200,snapshot(s,true));
        }
        if (req.method === 'POST' && parts[3] === 'tip') {
          if (s.stage !== 'tip') fail(409,'Tip selection is not available at this stage.');
          const body = await jsonBody(req);
          if (!Number.isInteger(body.tip) || body.tip < 0 || body.tip > 10000) fail(400,'Tip must be between $0 and $100.');
          s.tip = body.tip; change(s,'autograph'); return send(res,200,snapshot(s,true));
        }
        if (req.method === 'POST' && parts[3] === 'complete') {
          if (s.stage !== 'animating' && s.stage !== 'completed') fail(409,'No autograph is ready to finish.');
          if (s.stage !== 'completed') complete(s); return send(res,200,snapshot(s,true));
        }
        if (req.method === 'POST' && parts[3] === 'skip') {
          if (!['autograph','animating','completed'].includes(s.stage)) fail(409,'Accept the demo payment and select a tip first.');
          if (s.stage !== 'completed') { s.autographShared = false; complete(s); } return send(res,200,snapshot(s,true));
        }
        if (req.method === 'POST' && parts[3] === 'cancel') {
          if (s.stage === 'completed') fail(409,'Checkout is already complete.');
          s.autograph = null; change(s,'cancelled'); return send(res,200,snapshot(s,true));
        }
      }
      if (parts[0] === 'api' && parts[1] === 'phone' && parts[2]) {
        const s = get(parts[2]);
        if (req.method === 'POST' && parts[3] === 'claim') {
          if(s.mode!=='wifi')fail(409,'This order uses NFC instead of phone pairing.');
          const body = await jsonBody(req);
          if (req.headers.authorization !== 'Bearer ' + s.pairToken || !/^[a-f0-9-]{36}$/.test(body.claimId || '')) fail(403,'Pairing code is invalid.');
          if (s.stage === 'cancelled' || s.stage === 'completed') fail(409,'This checkout has ended.');
          if (s.claimId && s.claimId !== body.claimId) fail(409,'This code has already paired another phone.');
          if (!s.deviceToken) { s.claimId = body.claimId; s.deviceToken = token(); change(s,'paired'); }
          return send(res,200,{ deviceToken:s.deviceToken, checkout:snapshot(s) });
        }
        phone(req,s);
        if (req.method === 'GET' && parts.length === 3) return send(res,200,snapshot(s));
        if (req.method === 'POST' && parts[3] === 'action') {
          const body = await jsonBody(req);
          if (body.action === 'pay') {
            if (s.stage === 'paired') { if (![0,1].includes(body.card)) fail(400,'Invalid demo card.'); s.card=body.card; change(s,'tip'); }
            else if (!['tip','autograph','animating','completed'].includes(s.stage)) fail(409,'Checkout cannot accept a card now.');
          } else if (body.action === 'share') {
            if (s.stage === 'autograph') {
              if (!validateAutograph(body.autograph)) fail(400,'Invalid autograph. Try creating a shorter one.');
              s.autograph = body.autograph; s.autographShared = true;
              s.speed = [.75,1,1.5].includes(body.speed) ? body.speed : 1; s.reducedMotion = body.reducedMotion === true; change(s,'animating');
            } else if (!(s.autographShared && ['animating','completed'].includes(s.stage))) fail(409,'Confirm the final amount before sharing.');
          } else if (body.action === 'skip') {
            if (s.stage === 'autograph') complete(s);
            else if (!(s.stage === 'completed' && !s.autographShared)) fail(409,'Autograph choice is not available now.');
          } else fail(400,'Unknown action.');
          return send(res,200,snapshot(s));
        }
      }
      fail(404,'Not found.');
    } catch (error) { if (!error.status) console.error('Local server error:', error.message); if (!res.headersSent) send(res,error.status || 500,{ error:error.status ? error.message : 'The local demo server encountered an error.' }); else res.end(); }
  };
  const server=http.createServer(handler);
  const customerServer=http.createServer((req,res)=>{req.customerSurface=true;handler(req,res);});
  server.on('close', () => { clearInterval(cleanup); sessions.clear();displays.clear();if(customerServer.listening)customerServer.close(); });
  return { server, customerServer, sessions };
}
if (require.main === module) {
  const flag = process.argv.indexOf('--port'); const port = Number(flag >= 0 ? process.argv[flag+1] : process.env.PORT || 8787);
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Use a valid port.');
  const customerFlag=process.argv.indexOf('--customer-port');
  const customerPort=Number(customerFlag>=0?process.argv[customerFlag+1]:process.env.CUSTOMER_PORT||port+1);
  if(!Number.isInteger(customerPort)||customerPort<1||customerPort>65535||customerPort===port)throw new Error('Choose a different valid customer port.');
  const {server,customerServer} = createApp({port,customerPort});
  customerServer.listen(customerPort,'0.0.0.0',()=>console.log(`Customer display: http://localhost:${customerPort} (open the cashier pairing link)`));
  customerServer.on('error',e=>{console.error(`Customer display: ${e.message}`);server.close();process.exitCode=1;});
  server.listen(port,'0.0.0.0',() => { console.log(`\nTap Autograph · Merchant demo\nOpen on this laptop: http://localhost:${port}\n`); for (const a of addresses()) console.log(`Phone network: ${a.name} — ${a.address}`); console.log('\nKeep this window open. Ctrl+C stops the server.\n'); });
  server.on('error', e => { customerServer.close();console.error(e.code === 'EADDRINUSE' ? `Port ${port} is in use. Try node server.cjs --port 8788` : e.message); process.exitCode=1; });
}
module.exports = { createApp, validateAutograph, cartItems };
