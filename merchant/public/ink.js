export function normalizedTimeline(strokes) {
 let previous=null,time=0;
 return strokes.map(s=>({points:s.points.map((p,i)=>{if(previous!==null)time+=Math.max(0,Math.min(p.time-previous,i===0?350:100));previous=p.time;return {...p,time};})}));
}
export function playInk(canvas, autograph, speed=1, reduced=false, onDone=()=>{}) {
 const points=normalizedTimeline(autograph.strokes),duration=points.at(-1)?.points.at(-1)?.time||0;
 const color='#'+(autograph.color>>>0).toString(16).padStart(8,'0').slice(2);
 let cancelled=false,frame;
 const ctx=canvas.getContext('2d');
 function draw(elapsed){
  const rect=canvas.getBoundingClientRect(),dpr=window.devicePixelRatio||1;
  const w=Math.max(1,rect.width),h=Math.max(1,rect.height);
  if(canvas.width!==Math.round(w*dpr)||canvas.height!==Math.round(h*dpr)){canvas.width=Math.round(w*dpr);canvas.height=Math.round(h*dpr);}
  ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,w,h);
  const margin=18,scale=Math.min((w-2*margin)/autograph.aspect,h-2*margin);
  const x=(w-autograph.aspect*scale)/2,y=(h-scale)/2;
  ctx.strokeStyle=color;ctx.fillStyle=color;ctx.lineCap='round';ctx.lineJoin='round';
  for(const stroke of points){const ps=stroke.points;if(!ps.length||ps[0].time>elapsed)continue;
   if(ps.length===1){ctx.beginPath();ctx.arc(x+ps[0].x*autograph.aspect*scale,y+ps[0].y*scale,autograph.width*scale/2,0,Math.PI*2);ctx.fill();}
   for(let i=1;i<ps.length;i++){const a=ps[i-1],b=ps[i];if(a.time>elapsed)break;const fraction=b.time<=elapsed||b.time===a.time?1:Math.max(0,Math.min(1,(elapsed-a.time)/(b.time-a.time)));const bx=a.x+(b.x-a.x)*fraction,by=a.y+(b.y-a.y)*fraction;ctx.lineWidth=autograph.width*scale*(.7+.3*Math.max(.25,Math.min(1.2,(a.pressure+b.pressure)/2)));ctx.beginPath();if(a.x===bx&&a.y===by){ctx.arc(x+a.x*autograph.aspect*scale,y+a.y*scale,ctx.lineWidth/2,0,Math.PI*2);ctx.fill();}else{ctx.moveTo(x+a.x*autograph.aspect*scale,y+a.y*scale);ctx.lineTo(x+bx*autograph.aspect*scale,y+by*scale);ctx.stroke();}if(fraction<1)break;}
  }
 }
 const start=performance.now();
 function tick(now){if(cancelled)return;const elapsed=reduced?Infinity:(now-start)*speed;draw(elapsed);if(elapsed<duration)frame=requestAnimationFrame(tick);else if(!cancelled){frame=setTimeout(()=>{if(!cancelled)onDone();},reduced||duration===0?0:150);}}
 frame=requestAnimationFrame(tick);
 return ()=>{cancelled=true;cancelAnimationFrame(frame);clearTimeout(frame);};
}
