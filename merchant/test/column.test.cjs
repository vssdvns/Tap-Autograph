const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const path=require('node:path');const zlib=require('node:zlib');
const {decode}=require('../nfc/decode-compact.cjs');const {receive}=require('../nfc/reader.cjs');
const fixture=name=>fs.readFileSync(path.join(__dirname,'../nfc/fixtures',name));
function envelope(body,magic='TAG3'){const h=Buffer.alloc(13);h.write(magic);h.writeUInt32BE(body.length,5);let crc=0xffffffff;for(const b of body){crc^=b;for(let i=0;i<8;i++)crc=(crc>>>1)^((crc&1)?0xedb88320:0);}h.writeUInt32BE((crc^0xffffffff)>>>0,9);return Buffer.concat([h,body]);}
test('Kotlin TAG3 columns decode every quantized point and replay through existing APDUs',()=>{
 const packet=fixture('column.tai');const expected=JSON.parse(fixture('column-expected.json'));
 const got=decode(packet);assert.equal(got.id,expected.id);assert.equal(got.strokes.length,expected.strokes.length);
 assert.ok(Math.abs(got.aspect-expected.aspect)<1e-6);assert.ok(Math.abs(got.width-expected.width)<1e-7);assert.equal(got.color,expected.color);
 for(let s=0;s<got.strokes.length;s++)for(let p=0;p<got.strokes[s].points.length;p++){
  const a=got.strokes[s].points[p],b=expected.strokes[s].points[p];for(const key of ['x','y','pressure'])assert.ok(Math.abs(a[key]-b[key])<1e-6);assert.equal(a.time,b.time);assert.equal(a.tool,b.tool);
 }
 assert.deepEqual(receive(JSON.parse(fixture('column-reader.json'))).packet,packet);
});
test('TAG3 rejects corruption, trailing data, bad tools, count overflow and truncated columns',()=>{
 const packet=fixture('column.tai');const damaged=Buffer.from(packet);damaged[9]^=1;
 assert.throws(()=>decode(damaged),/CRC/);assert.throws(()=>decode(Buffer.concat([packet,Buffer.from([0])])));
 const huge=Buffer.from(packet);huge.writeUInt32BE(1048577,5);assert.throws(()=>decode(huge),/header/);
 const raw=packet[4]?zlib.inflateSync(packet.subarray(13)):packet.subarray(13);
 assert.throws(()=>decode(envelope(raw.subarray(0,raw.length-1))));
 const badTool=Buffer.from(raw);badTool[badTool.length-1]=5;assert.throws(()=>decode(envelope(badTool)),/point/);
 const count=Buffer.concat([raw.subarray(0,8),Buffer.from([0xe9,7])]);assert.throws(()=>decode(envelope(count)),/varint/);
 const empty=Buffer.concat([raw.subarray(0,8),Buffer.from([0])]);assert.throws(()=>decode(envelope(empty)),/Empty/);
 assert.deepEqual(decode(envelope(raw)).strokes,decode(packet).strokes);
});

test('Kotlin TAG4 predictor preserves TAG3 geometry and uses existing reader framing',()=>{
 const packet=fixture('predictive.tai'),reference=decode(fixture('column.tai')),got=decode(packet);
 assert.deepEqual(got.strokes,reference.strokes);assert.equal(got.aspect,reference.aspect);assert.equal(got.width,reference.width);assert.equal(got.color,reference.color);
 assert.deepEqual(receive(JSON.parse(fixture('predictive-reader.json'))).packet,packet);
 assert.throws(()=>decode(Buffer.concat([packet,Buffer.from([0])])));
 const raw=packet[4]?zlib.inflateSync(packet.subarray(13)):packet.subarray(13);
 // Reinterpret plain deltas as second differences: bounds validation must reject this drawing.
 const plain=fixture('column.tai');const plainRaw=plain[4]?zlib.inflateSync(plain.subarray(13)):plain.subarray(13);
 assert.throws(()=>decode(envelope(plainRaw,'TAG4')));
 const bad=Buffer.from(packet);bad[9]^=1;assert.throws(()=>decode(bad),/CRC/);
 assert.deepEqual(decode(envelope(raw,'TAG4')).strokes,got.strokes);
});
