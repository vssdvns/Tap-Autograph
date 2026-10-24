import {playInk} from './ink.js';
import {attachSignaturePad,signatureInk} from './signature-pad.js';
const $=s=>document.querySelector(s),money=n=>new Intl.NumberFormat('en-US',{style:'currency',currency:'USD'}).format(n/100);
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const token=location.hash.slice(1);let state,revision='',tip=0,busy=false,stopInk,lastInk=null,orderId=null,tipWrite=Promise.resolve(),tipSeq=0,tipStaging=0,customValid=true,tipChosen=false,disposePad=null,draft={id:signatureId(),strokes:[],count:0};
function signatureId(){return 'manual-'+Array.from(crypto.getRandomValues(new Uint8Array(16)),n=>n.toString(16).padStart(2,'0')).join('');}
function error(text=''){$('#error').textContent=text;$('#error').hidden=!text;}
async function api(url,body){const r=await fetch(url,{method:body===undefined?'GET':'POST',headers:{authorization:'Bearer '+token,...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body)});const value=await r.json();if(!r.ok)throw new Error(value.error||'Could not connect.');return value;}
function summary(s){const lines=s?.items||state.cart,sub=s?.subtotal??state.subtotal,tax=s?.tax??state.tax,t=s?.stage==='tip'?tip:(s?.tip||0);return `<aside class="order-summary"><p class="eyebrow">YOUR ORDER</p><div class="items">${lines.map(i=>`<div class="item"><span class="name">${esc(i.name)}<small>${money(i.price)} each</small></span><span>× ${i.quantity}</span><span class="cost">${money(i.price*i.quantity)}</span></div>`).join('')}</div><div class="totals"><div><span>Subtotal</span><span>${money(sub)}</span></div><div><span>Tax · 8.75%</span><span>${money(tax)}</span></div>${s&&(['autograph','animating','completed'].includes(s.stage)||(s.stage==='tip'&&tipChosen))?`<div><span>Tip</span><span>${money(t)}</span></div>`:''}<div class="grand"><span>Total</span><span>${money(sub+tax+t)}</span></div></div></aside>`;}
function signatureForm(s){
 const locked=s.stage!=='tip',selectedTip=locked?s.tip:tip;
 return `<p class="badge">✓ DEMO PAYMENT ACCEPTED</p><h1>Make it yours.</h1><p class="signature-intro">Choose a tip, then sign below or tap your phone.</p><div class="tips">${[0,15,20].map(p=>{const cents=Math.round(s.subtotal*p/100);return `<button class="tip ${((tipChosen||locked)&&selectedTip===cents)?'selected':''}" data-tip="${cents}" ${locked?'disabled':''}>${p?p+'%':'No tip'}<small>${money(cents)}</small></button>`;}).join('')}</div><div class="tip-custom-row"><label for="customTip">Custom tip</label><div class="custom-tip"><span>$</span><input id="customTip" value="${![0,Math.round(s.subtotal*.15),Math.round(s.subtotal*.20)].includes(selectedTip)?(selectedTip/100).toFixed(2):''}" type="number" min="0" max="100" step=".01" inputmode="decimal" placeholder="0.00" ${locked?'disabled':''}></div><strong id="tipTotal">Total · ${money(s.subtotal+s.tax+selectedTip)}</strong></div><p id="tipHint" class="signature-instruction">${locked?'Keep your phone nearby—or sign below and continue.':'Select a tip and tap your phone to sign automatically.'}</p><div class="signature-entry"><div class="signature-entry-heading"><label for="manualSignature">Sign here</label><button data-clear-signature class="signature-clear" ${draft.strokes.length?'':'disabled'}>Clear</button></div><div class="signature-surface"><canvas id="manualSignature" role="img" aria-label="Signature pad. Draw with a mouse, finger or stylus."></canvas><span id="signaturePlaceholder" ${draft.strokes.length?'hidden':''}>Your signature</span></div><div class="signature-entry-footer"><span id="signatureStatus" role="status">${draft.strokes.length?'Signature ready':'Use your finger, stylus or mouse'}</span><span>or tap your phone ↗</span></div></div><button class="button" data-action="manual-sign" ${!draft.strokes.length||busy||tipStaging||!customValid?'disabled':''}>Continue →</button><button class="signature-skip" data-action="finish-unsigned">Continue without signature</button>`;
}
function updatePadControls(){
 const hasInk=draft.strokes.length>0,placeholder=$('#signaturePlaceholder'),status=$('#signatureStatus'),clear=$('[data-clear-signature]'),button=$('[data-action="manual-sign"]');
 if(placeholder)placeholder.hidden=hasInk;if(status)status.textContent=hasInk?'Signature ready':'Use your finger, stylus or mouse';if(clear)clear.disabled=!hasInk||busy;
 if(button)button.disabled=!hasInk||busy||tipStaging>0||!customValid;
}
function render(){
 stopInk?.();stopInk=null;disposePad?.();disposePad=null;const s=state.checkout;
 if(s?.id!==orderId){lastInk=null;draft={id:signatureId(),strokes:[],count:0};tip=s?.pendingTip??0;tipChosen=s?.pendingTip!=null;customValid=true;orderId=s?.id||null;tipSeq++;}
 let content='';
 if(!s&&!state.cart.length){$('#customerStage').innerHTML='<div class="welcome"><p class="eyebrow">WELCOME TO HARBOR</p><h1>Your next little<br>pick-me-up.</h1><p>Fresh coffee. Warm pastries. Your order will appear here as we get it ready.</p></div>';return;}
 if(!s)content='<p class="badge">FRESHLY PICKED FOR YOU</p><h1>Looking good<br>so far.</h1><p class="copy">Your order updates here while we prepare your checkout.</p>';
 else if(s.stage==='paired')content=`<div class="tap-circle" aria-hidden="true">↗</div><p class="badge">READY TO TAP</p><h1>A little tap.<br>You're on your way.</h1><div class="amount">${money(s.total)}</div><p class="copy">Hold your phone near the reader. Unlock it if asked, then tap again.</p>`;
 else if(['tip','autograph'].includes(s.stage))content=signatureForm(s);
 else if(s.stage==='animating'){lastInk=s.autograph||lastInk;content='<p class="badge">YOUR AUTOGRAPH HAS ARRIVED</p><h1>Written by you.<br>Remembered here.</h1><div class="signature-paper"><canvas id="ink" aria-label="Your autograph animating"></canvas></div><p class="copy">A small moment that makes it yours.</p>';}
 else if(s.stage==='completed')content=`<div class="success-circle" aria-hidden="true">✓</div><p class="badge">DEMO CHECKOUT COMPLETE</p><h1>Thanks.<br>See you soon.</h1>${s.autographShared&&lastInk?'<div class="signature-paper"><canvas id="ink" aria-label="Your autograph on the receipt"></canvas></div>':''}<p class="receipt-meta">Demo card •••• ${s.card===1?'7310':'2048'}<br>${s.autographSource==='manual-display'?'Signed on the customer display':s.autographShared?'Autograph shared':'Completed without autograph'} · ${new Date(s.completedAt).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})}</p>`;
 else content='<p class="badge">CHECKOUT ENDED</p><h1>Just a moment.</h1><p class="copy">We’ll start a fresh order for you at the counter.</p>';
 const receipt=s&&!['expired','cancelled'].includes(s.stage)?s:null;
 $('#customerStage').innerHTML=`<div data-checkout-id="${esc(s?.id||'')}" class="customer-grid ${s&&['tip','autograph'].includes(s.stage)?'signature-layout':''}"><section class="customer-main">${content}</section>${summary(receipt)}</div>`;
 if(s?.stage==='animating'&&lastInk)stopInk=playInk($('#ink'),lastInk,s.speed,s.reducedMotion||matchMedia('(prefers-reduced-motion: reduce)').matches,()=>action('complete'));
 if(s?.stage==='completed'&&s.autographShared&&lastInk)stopInk=playInk($('#ink'),lastInk,1,true);
 if(s&&['tip','autograph'].includes(s.stage)){
  if(s.stage==='autograph'){tip=s.tip;customValid=true;}
  disposePad=attachSignaturePad($('#manualSignature'),draft,updatePadControls);tipReady();updatePadControls();
 }
}
async function action(name){
 if(busy||!state?.checkout)return;
 if(name==='manual-sign'||name==='finish-unsigned'){
  await tipWrite;if(busy||!customValid||!state?.checkout||!['tip','autograph'].includes(state.checkout.stage))return;
  if(name==='manual-sign'&&!draft.strokes.length)return;
 }
 busy=true;error();updatePadControls();const id=state.checkout.id;
 try{
  if(name==='finish-unsigned'){
   if(state.checkout.stage==='tip')state=await api('/api/display/action',{checkoutId:id,action:'tip',tip});
   state=await api('/api/display/action',{checkoutId:id,action:'skip'});
  }else{
   const ink=name==='manual-sign'?signatureInk(draft):null;
   const body={checkoutId:id,action:name,...(ink?{tip:state.checkout.stage==='tip'?tip:state.checkout.tip,tipRevision:state.checkout.tipRevision,autograph:ink}:{})};
   const next=await api('/api/display/action',body);
   if(ink)lastInk=ink;
   state=next;
  }
  revision=state.revision;render();
 }catch(e){
  // NFC and the manual button can race; the first successful signature wins.
  if(name==='manual-sign'||name==='finish-unsigned'){
   try{const next=await api('/api/display/state');if(next.checkout?.id===id&&['animating','completed'].includes(next.checkout.stage)){state=next;revision=next.revision;render();return;}}catch{}
  }
  error(e.message);
  if(name==='complete')setTimeout(()=>{if(state?.checkout?.id===id&&state.checkout.stage==='animating')action('complete');},1500);
 }finally{busy=false;document.querySelectorAll('[data-action]').forEach(b=>b.disabled=false);tipReady();updatePadControls();}
}
function tipReady(){
 const hint=$('#tipHint');if(hint)hint.textContent=tipStaging?'Updating tip…':!customValid?'Enter a valid tip before continuing or tapping.':state?.checkout?.stage==='autograph'?'Keep your phone nearby—or sign below and continue.':state?.checkout?.pendingTip!=null?'Ready: tap your phone to sign and finish. Or sign below and press Continue.':'Select a tip, then tap your phone—or sign below and press Continue.';
 updatePadControls();
}
function saveTip(value){
 if(state?.checkout?.stage!=='tip'||state.checkout.mode!=='two-tap')return;
 const id=state.checkout.id,sequence=++tipSeq;tipStaging++;tipReady();
 tipWrite=tipWrite.catch(()=>{}).then(async()=>{
   if(state?.checkout?.id!==id)return;
   const next=await api('/api/display/action',{checkoutId:id,action:'tip-select',tip:value});
   if(sequence===tipSeq&&state?.checkout?.id===id){state=next;revision=next.revision;if(next.checkout.stage!=='tip')render();}
 }).catch(e=>{error(e.message);}).finally(()=>{tipStaging--;tipReady();});
}
document.addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;if(b.hasAttribute('data-clear-signature')){draft={id:signatureId(),strokes:[],count:0};render();return;}if(b.dataset.tip!==undefined && state?.checkout?.stage==='tip'){tip=Number(b.dataset.tip);tipChosen=true;customValid=true;render();saveTip(tip);}if(b.dataset.action)action(b.dataset.action);});
document.addEventListener('input',e=>{
 if(e.target.id!=='customTip'||state?.checkout?.stage!=='tip')return;
 const value=e.target.value,cents=Math.round(Number(value)*100),valid=/^\d{1,3}(\.\d{0,2})?$/.test(value)&&cents>=0&&cents<=10000;
 e.target.setCustomValidity(valid?'':'Choose $0–$100.');tip=valid?cents:0;customValid=valid;tipChosen=valid;
 $('#tipTotal').textContent='Total · '+money(state.checkout.subtotal+state.checkout.tax+tip);
 document.querySelectorAll('.tip').forEach(b=>b.classList.remove('selected'));
 const current=document.querySelector('.order-summary');if(current)current.outerHTML=summary(state.checkout);
 saveTip(valid?cents:null);
});
$('#fullscreen').addEventListener('click',async()=>{try{if(document.fullscreenElement)await document.exitFullscreen();else await document.documentElement.requestFullscreen();}catch{error('Use your browser fullscreen control (F11 on Windows).');}});
async function poll(){try{const next=await api('/api/display/state');$('#connection').textContent='Live with the counter';error();if(next.revision!==revision&&!busy&&!tipStaging){state=next;revision=next.revision;render();}}catch(e){$('#connection').textContent='Reconnecting…';error(e.message);}setTimeout(poll,250);}
if(/^[a-f0-9]{64}$/.test(token))poll();else $('#connection').textContent='Waiting to connect';
