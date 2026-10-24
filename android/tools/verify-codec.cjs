// Compare Kotlin-generated synthetic fixtures against independent Node decoding.
const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
const {decode}=require('./decode-compact.cjs');
const folder=process.argv[2]||path.join(__dirname,'../app/build/codec-fixtures');
for(const name of ['short','dense']) {
  const packet=fs.readFileSync(path.join(folder,name+'.tai'));
  const expected=JSON.parse(fs.readFileSync(path.join(folder,name+'.decoded.json')));
  const actual=decode(packet);
  assert.equal(actual.id,expected.id);assert.equal(actual.color,expected.color);assert.equal(actual.schema,expected.schema);
  assert.ok(Math.abs(actual.width-expected.width)<1e-7);assert.ok(Math.abs(actual.aspect-expected.aspect)<1e-6);
  assert.equal(actual.strokes.length,expected.strokes.length);
  actual.strokes.forEach((s,i)=>{assert.equal(s.points.length,expected.strokes[i].points.length);s.points.forEach((p,j)=>{
    const q=expected.strokes[i].points[j];assert.equal(p.time,q.time);assert.equal(p.tool,q.tool);
    for(const field of ['x','y','pressure'])assert.ok(Math.abs(p[field]-q[field])<1e-6);
  });});
  for(let n=0;n<packet.length;n++)assert.throws(()=>decode(packet.subarray(0,n)));
  assert.throws(()=>decode(Buffer.concat([packet,Buffer.from([0])])));
  const json=fs.statSync(path.join(folder,name+'.source.json')).size;
  console.log(`${name}: ${json} JSON bytes -> ${packet.length} TAG1 bytes; Kotlin/Node match; all truncated prefixes rejected`);
}
