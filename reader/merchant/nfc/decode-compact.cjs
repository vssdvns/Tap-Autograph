// TAG1 reference decoder. Node built-ins only. CRC is not authentication.
const fs = require('node:fs');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
function decode(packet) {
  const check = (ok, message) => { if (!ok) throw new Error(message); };
  check(packet.length >= 14 && packet.length <= 65536, 'Invalid packet length');
  check(packet.toString('ascii', 0, 4) === 'TAG1', 'Unsupported version');
  const flags = packet[4], length = packet.readUInt32BE(5);
  check(flags <= 1 && length > 0 && length <= 1048576, 'Invalid header');
  let body = packet.subarray(13);
  if (flags === 1) {
    const result = zlib.inflateSync(body, {maxOutputLength:length, info:true});
    check(result.engine.bytesWritten === body.length, 'Trailing compressed data');
    body = result.buffer;
  }
  check(body.length === length, 'Body length mismatch');
  let crc = 0xffffffff;
  for (const byte of body) { crc ^= byte; for (let n=0;n<8;n++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0); }
  check(((crc ^ 0xffffffff) >>> 0) === packet.readUInt32BE(9), 'CRC mismatch');
  let offset=0;
  function byte() { check(offset<body.length,'Truncated body'); return body[offset++]; }
  function u16() { return byte()*256+byte(); }
  function variable(max) {
    let value=0;
    for(let i=0;i<5;i++) { const b=byte(); value+=(b&127)*2**(i*7);
      if(!(b&128)) {check((i===0 || b!==0)&&value<=max,'Invalid varint');return value;}
    } throw new Error('Long varint');
  }
  const aspect=u16()/1024, width=u16()/65535;
  let color=0;for(let i=0;i<4;i++) color=color*256+byte();
  check(aspect>0 && aspect<20 && width>=.001 && width<=.04,'Invalid drawing properties');
  const count=variable(1000);check(count>0,'Empty ink');
  let total=0,time=0; const strokes=[];
  const unzig = n => (n>>>1)^-(n&1);
  for(let s=0;s<count;s++) {
    const n=variable(30000);total+=n;check(n>0&&total<=30000,'Invalid point count');
    let x=0,y=0; const points=[];
    for(let p=0;p<n;p++) {
      x+=unzig(variable(8190));y+=unzig(variable(8190));time+=variable(86400000);
      const pressure=byte()/127.5,tool=byte();
      check(x>=0&&x<=4095&&y>=0&&y<=4095&&time<=86400000&&tool<=4,'Invalid point');
      points.push({x:x/4095,y:y/4095,time,pressure,tool});
    } strokes.push({points});
  }
  check(offset===body.length,'Trailing ink');
  return {schema:1,id:'compact-'+crypto.createHash('sha256').update(packet).digest('hex').slice(0,24),aspect,strokes,color,width};
}
module.exports = {decode};
if (require.main === module) {
  try { console.log(JSON.stringify(decode(fs.readFileSync(process.argv[2])))); }
  catch(error) { console.error(error.message); process.exitCode=1; }
}
