// Local draft only: handwriting is submitted when Continue is pressed.
export function attachSignaturePad(canvas,draft,onChange){
 const ctx=canvas.getContext('2d');let active=null,stroke=null;
 const aspect=2.6,inkWidth=.012;
 function point(e){const r=canvas.getBoundingClientRect();return {x:Math.max(0,Math.min(1,(e.clientX-r.left)/r.width)),y:Math.max(0,Math.min(1,(e.clientY-r.top)/r.height)),time:Math.max(draft.lastTime||0,Math.min(86400000,Math.round(performance.now()-draft.started))),pressure:e.pointerType==='pen'?Math.max(.25,Math.min(1.2,e.pressure||.5)):1,tool:0};}
 function draw(){
  const r=canvas.getBoundingClientRect(),dpr=window.devicePixelRatio||1;
  canvas.width=Math.max(1,Math.round(r.width*dpr));canvas.height=Math.max(1,Math.round(r.height*dpr));ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,r.width,r.height);
  ctx.strokeStyle='#18365f';ctx.fillStyle='#18365f';ctx.lineCap='round';ctx.lineJoin='round';
  for(const s of draft.strokes){const ps=s.points;if(ps.length===1){ctx.beginPath();ctx.arc(ps[0].x*r.width,ps[0].y*r.height,inkWidth*r.height/2,0,Math.PI*2);ctx.fill();}
   for(let i=1;i<ps.length;i++){const a=ps[i-1],b=ps[i];ctx.lineWidth=inkWidth*r.height*(.7+.3*((a.pressure+b.pressure)/2));ctx.beginPath();ctx.moveTo(a.x*r.width,a.y*r.height);ctx.lineTo(b.x*r.width,b.y*r.height);ctx.stroke();}
  }
 }
 function down(e){if(active!==null||e.isPrimary===false||e.button!==0)return;if(draft.strokes.length>=1000||draft.count>=8000)return;e.preventDefault();active=e.pointerId;canvas.setPointerCapture(active);draft.started??=performance.now();stroke={points:[point(e)]};draft.strokes.push(stroke);draft.count=(draft.count||0)+1;draft.lastTime=stroke.points[0].time;draw();onChange();}
 function move(e){if(e.pointerId!==active||!stroke)return;e.preventDefault();for(const event of e.getCoalescedEvents?.().length?e.getCoalescedEvents():[e]){if(draft.count>=8000)break;const p=point(event),last=stroke.points.at(-1);if(Math.hypot((p.x-last.x)*aspect,p.y-last.y)<.0015)continue;stroke.points.push(p);draft.count++;draft.lastTime=p.time;}draw();onChange();}
 function up(e){if(e.pointerId!==active)return;move(e);active=null;stroke=null;onChange();}
 function cancel(e){if(e.pointerId!==active)return;active=null;stroke=null;onChange();}
 canvas.addEventListener('pointerdown',down);canvas.addEventListener('pointermove',move);canvas.addEventListener('pointerup',up);canvas.addEventListener('pointercancel',cancel);
 const observer=new ResizeObserver(draw);observer.observe(canvas);draw();
 return ()=>{observer.disconnect();canvas.removeEventListener('pointerdown',down);canvas.removeEventListener('pointermove',move);canvas.removeEventListener('pointerup',up);canvas.removeEventListener('pointercancel',cancel);};
}
export function signatureInk(draft){return {schema:1,id:draft.id,aspect:2.6,color:0xff18365f,width:.012,strokes:draft.strokes.map(stroke=>({points:stroke.points.map(p=>({...p}))}))};}
