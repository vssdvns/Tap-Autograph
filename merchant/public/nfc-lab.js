import {playInk} from './ink.js';
const input=document.querySelector('#transcript'),status=document.querySelector('#status'),canvas=document.querySelector('#ink'),replay=document.querySelector('#replay');
let autograph=null,stop=()=>{},generation=0,seenTransfer=null,polling=false,closed=false;
function clear(){generation++;stop();autograph=null;input.value='';replay.disabled=true;canvas.getContext('2d').clearRect(0,0,canvas.width,canvas.height);status.textContent='Received copy cleared.';}
function play(){if(!autograph)return;stop();stop=playInk(canvas,autograph,1,matchMedia('(prefers-reduced-motion: reduce)').matches);}
input.addEventListener('change',async()=>{
 const file=input.files[0];if(!file)return;const current=++generation;stop();autograph=null;replay.disabled=true;canvas.getContext('2d').clearRect(0,0,canvas.width,canvas.height);
 try{
  if(file.size>2000000)throw Error('Test file exceeds 2 MB.');status.textContent='Checking reader transcript…';
  const text=await file.text();JSON.parse(text);
  if(current!==generation)return;
  const response=await fetch('/api/nfc-lab',{method:'POST',headers:{'Content-Type':'application/json'},body:text});const data=await response.json();
  if(current!==generation)return;if(!response.ok)throw Error(data.error||'Import failed');
  autograph=data.autograph;const s=data.summary;status.textContent=`Verified simulated transfer: ${s.bytes.toLocaleString()} bytes · ${s.chunks} chunks · ${s.retries} retried reads · ${s.reconnects} reconnects. No NFC radio was used.`;replay.disabled=false;play();
 }catch(error){if(current===generation)status.textContent=error.message;}
});
replay.addEventListener('click',play);document.querySelector('#clear').addEventListener('click',async()=>{clear();try{await fetch('/api/reader/clear',{method:'POST'});}catch{status.textContent='Browser cleared; server copy expires within 60 seconds.';}});addEventListener('pagehide',()=>{closed=true;clear();});
const labels={connected:'ESP32 USB connected',pn532_missing:'ESP32 connected · PN532 not detected',ready:'PN532 ready · waiting for phone',phone_found:'Phone found',receiving:'Receiving autograph chunks',retrying:'Link interrupted · retrying',verified:'Packet verified on laptop',complete:'Phone acknowledged completion',ack_lost:'Packet received · phone acknowledgement lost',transfer_error:'Transfer failed · check phone sharing and hold position',disconnected:'Bridge disconnected'};
async function pollReader(){
 if(polling||closed)return;polling=true;const startedGeneration=generation;
 try{const response=await fetch('/api/reader/state');if(!response.ok)throw Error();const data=await response.json();if(startedGeneration!==generation)return;
 document.querySelector('#reader-status').textContent=(data.simulated?'SIMULATED · ':'')+(labels[data.status]||'Reader status unavailable')+(data.status==='disconnected'&&data.lastIssue?' · Last issue: '+labels[data.lastIssue]:'');
 const latest=data.latest;if(latest&&latest.id!==seenTransfer){seenTransfer=latest.id;generation++;stop();autograph=latest.autograph;replay.disabled=false;status.textContent=`${latest.simulated?'Simulated':'USB bridge'} packet verified: ${latest.bytes.toLocaleString()} bytes · ${latest.chunks} chunks. Autograph received; no payment processed.`;play();}
 }catch{document.querySelector('#reader-status').textContent='Cannot reach the local merchant server.';}finally{polling=false;}
}
pollReader();setInterval(pollReader,800);
