// TAG1 reference decoder. Node built-ins only. CRC is not authentication.
const fs = require('node:fs');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
function decode(packet) {
  const check = (ok, message) => { if (!ok) throw new Error(message); };
  check(packet.length >= 14 && packet.length <= 65536, 'Invalid packet length');
  const magic=packet.toString('ascii',0,4);check(['TAG1','TAG2','TAG3','TAG4'].includes(magic),'Unsupported version');
  const flags = packet[4], length = packet.readUInt32BE(5);
  check(flags <= 1 && length > 0 && length <= 1048576, 'Invalid header');
  if(magic==='TAG2')check(flags===1&&length>=12&&length<=3210,'Invalid raster bounds');
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
  if(magic==='TAG2'){
    const w=body.readUInt16BE(0),h=body.readUInt16BE(2),color=body.readUInt32BE(4),width=body.readUInt16BE(8)/65535;
    check(w>=3&&w<=160&&h>=3&&h<=160&&w/h<20&&width>=.001&&width<=.04&&body.length===10+Math.ceil(w*h/8),'Invalid raster dimensions');
    const bits=body.subarray(10),remainder=w*h%8;
    check(!remainder || (bits.at(-1)&((1<<(8-remainder))-1))===0,'Noncanonical raster padding');
    const on=(x,y)=>{const i=y*w+x;return (bits[i>>3]&(1<<(7-i%8)))!==0;};
    const strokes=[];
    for(let y=0;y<h;y++)for(let x=0;x<w;x++){
      if(!on(x,y))continue;const start=x;while(x+1<w&&on(x+1,y))x++;
      strokes.push({points:[start,x].map(v=>({x:(v+.5)/w,y:(y+.5)/h,time:0,pressure:1,tool:0}))});
      check(strokes.length<=1000,'Too many raster runs');
    }
    check(strokes.length>0,'Empty raster');
    return {schema:1,id:'compact-'+crypto.createHash('sha256').update(packet).digest('hex').slice(0,24),aspect:w/h,width,color,strokes};
  }
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
  const counts=['TAG3','TAG4'].includes(magic)?Array.from({length:count},()=>{const n=variable(30000);total+=n;check(n>0&&total<=30000,'Invalid point count');return n;}):null;
  const fields=counts?Array.from({length:5},(_,c)=>Array.from({length:total},()=>c<3?variable(c<2?(magic==='TAG4'?16380:8190):86400000):byte())):null;
  let point=0;
  const unzig = n => (n>>>1)^-(n&1);
  for(let s=0;s<count;s++) {
    const n=counts?counts[s]:variable(30000);if(!counts)total+=n;check(n>0&&total<=30000,'Invalid point count');
    let x=0,y=0,previousDx=0,previousDy=0; const points=[];
    for(let p=0;p<n;p++) {
      let dx=unzig(fields?fields[0][point]:variable(8190)),dy=unzig(fields?fields[1][point]:variable(8190));
      if(magic==='TAG4'){dx+=previousDx;dy+=previousDy;check(Math.abs(dx)<=4095&&Math.abs(dy)<=4095,'Invalid predicted delta');}
      previousDx=dx;previousDy=dy;x+=dx;y+=dy;time+=fields?fields[2][point]:variable(86400000);
      const pressure=(fields?fields[3][point]:byte())/127.5,tool=fields?fields[4][point]:byte();point++;
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
