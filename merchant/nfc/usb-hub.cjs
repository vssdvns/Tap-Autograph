'use strict';
const crypto=require('node:crypto');const {decode}=require('./decode-compact.cjs');
const CODES=new Set(['connected','pn532_missing','ready','phone_found','receiving','retrying','verified','complete','ack_lost','transfer_error','disconnected']);
function createReaderHub({now=()=>Date.now()}={}) {
 const token=crypto.randomBytes(32).toString('hex');let revision=0,seenAt=0,status='disconnected',simulated=false,latest=null,lastIssue=null;
 const seen=new Map();
 const clear=()=>{latest=null;revision++;};
 function snapshot(){if(latest&&now()-latest.receivedAt>=60000)clear();return {revision,status:seenAt&&now()-seenAt>12000?'disconnected':status,simulated,latest,lastIssue};}
 function event(body){
  if(!body || typeof body.simulated!=='boolean')throw Error('Reader must identify simulated mode.');
  if(body.type==='state'){
   if(!CODES.has(body.code))throw Error('Unknown diagnostic status.');
   status=body.code;if(['pn532_missing','transfer_error','ack_lost'].includes(body.code))lastIssue=body.code;else if(body.code==='connected'||body.code==='complete')lastIssue=null;simulated=body.simulated;seenAt=now();return {accepted:true};
  }
  if(body.type!=='packet'||typeof body.session!=='string'||!/^([a-f0-9]{16})$/.test(body.session)||typeof body.sha256!=='string'||!/^([a-f0-9]{64})$/.test(body.sha256)||typeof body.packet!=='string'||body.packet.length>87384||!/^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(body.packet))throw Error('Invalid reader packet envelope.');
  const packet=Buffer.from(body.packet,'base64');
  if(packet.length<14||packet.length>65536||crypto.createHash('sha256').update(packet).digest('hex')!==body.sha256)throw Error('Reader packet integrity mismatch.');
  const autograph=decode(packet);const key=body.session+':'+body.sha256;
  for(const [k,t] of seen)if(now()-t>120000)seen.delete(k);
  if(seen.has(key)){seenAt=now();return {accepted:true,duplicate:true};}
  if(seen.size>=200)throw Error('Too many recent transfers. Restart the demo server.');
  seen.set(key,now());
  latest={id:crypto.randomUUID(),receivedAt:now(),autograph,bytes:packet.length,chunks:Math.ceil(packet.length/192),simulated:body.simulated};
  simulated=body.simulated;seenAt=now();status='verified';revision++;return {accepted:true,duplicate:false};
 }
 return {token,event,snapshot,clear};
}
function loopbackRequest(req){
 if(!['127.0.0.1','::1','::ffff:127.0.0.1'].includes(req.socket.remoteAddress))return false;
 try {const host=new URL('http://'+req.headers.host);return ['127.0.0.1','localhost','[::1]'].includes(host.hostname)&&!host.username&&!host.password&&host.pathname==='/';}catch{return false;}
}
module.exports={createReaderHub,loopbackRequest};
