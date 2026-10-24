'use strict';
const crypto=require('node:crypto');const fs=require('node:fs');const {decode}=require('./decode-compact.cjs');
const SELECT='00A4040008F05441504155540100';
function receive(transcript) {
 const check=(v,m)=>{if(!v)throw Error(m);};
 check(transcript?.protocol==='TAP1'&&transcript.simulated===true,'Expected a TAP1 simulated-reader transcript');
 check(Array.isArray(transcript.exchanges)&&transcript.exchanges.length>=3&&transcript.exchanges.length<=800,'Invalid exchange count');
 let metadata,session,hash,length,count;const chunks=new Map();let finished=false,retries=0,selects=0;
 function hex(text,max){check(typeof text==='string'&&text.length<=max&&text.length%2===0&&/^[a-fA-F0-9]+$/.test(text),'Invalid hex data');return Buffer.from(text,'hex');}
 for(const exchange of transcript.exchanges){
  check(!finished,'Unexpected exchange after finish');
  const cmd=hex(exchange.command,100),response=hex(exchange.response,500);
  check(response.length>=2&&response.readUInt16BE(response.length-2)===0x9000,'Reader command failed');
  const data=response.subarray(0,-2);
  if(cmd.toString('hex').toUpperCase()===SELECT){
   check(data.length===49&&data[0]===1,'Invalid metadata');selects++;
   if(metadata){check(data.equals(metadata),'Snapshot changed across reconnect');continue;}
   metadata=Buffer.from(data);session=data.subarray(1,9);length=data.readUInt32BE(9);count=data.readUInt16BE(15);hash=data.subarray(17,49);
   check(length>=14&&length<=65536&&data.readUInt16BE(13)===192&&count===Math.ceil(length/192),'Invalid packet bounds');continue;
  }
  check(metadata&&cmd.length>=5&&cmd[0]===0x80&&cmd[2]===0&&cmd[3]===0&&cmd[4]===cmd.length-5,'Invalid command');
  check(cmd.subarray(5,13).equals(session),'Stale command session');
  if(cmd[1]===0x10){
   check(cmd.length===15&&data.length>=10&&data.subarray(0,8).equals(session),'Invalid chunk session');
   const index=cmd.readUInt16BE(13);check(index<count&&data.readUInt16BE(8)===index,'Invalid chunk index');
   const bytes=data.subarray(10);check(bytes.length===Math.min(192,length-index*192),'Invalid chunk length');
   if(chunks.has(index)){check(bytes.equals(chunks.get(index)),'Conflicting retry');retries++;}else chunks.set(index,Buffer.from(bytes));
  }else if(cmd[1]===0x20){
   check(cmd.length===45&&data.length===0&&cmd.subarray(13).equals(hash)&&chunks.size===count,'Incomplete or invalid finish');finished=true;
  }else throw Error('Unsupported instruction');
 }
 check(finished,'Missing completion acknowledgement');
 const packet=Buffer.concat(Array.from({length:count},(_,i)=>chunks.get(i)));
 check(packet.length===length&&crypto.createHash('sha256').update(packet).digest().equals(hash),'Packet hash mismatch');
 return {autograph:decode(packet),summary:{bytes:length,chunks:count,retries,reconnects:Math.max(0,selects-1),simulated:true},packet};
}
module.exports={receive};
if(require.main===module){try{const result=receive(JSON.parse(fs.readFileSync(process.argv[2],'utf8')));console.log(JSON.stringify(result.summary));}catch(e){console.error(e.message);process.exitCode=1;}}
