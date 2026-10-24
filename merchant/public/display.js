import {playInk} from './ink.js';
const $=s=>document.querySelector(s),money=n=>new Intl.NumberFormat('en-US',{style:'currency',currency:'USD'}).format(n/100);
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const token=location.hash.slice(1);let state,revision='',tip=0,busy=false,stopInk,lastInk=null,orderId=null,tipWrite=Promise.resolve(),tipSeq=0,tipStaging=0,customValid=true,tipChosen=false;
function error(text=''){$('#error').textContent=text;$('#error').hidden=!text;}
async function api(url,body){const r=await fetch(url,{method:body===undefined?'GET':'POST',headers:{authorization:'Bearer '+token,...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body)});const value=await r.json();if(!r.ok)throw new Error(value.error||'Could not connect.');return value;}
function summary(s){const lines=s?.items||state.cart,sub=s?.subtotal??state.subtotal,tax=s?.tax??state.tax,t=s?.tip||0;return `<aside class="order-summary"><p class="eyebrow">YOUR ORDER</p><div class="items">${lines.map(i=>`<div class="item"><span class="name">${esc(i.name)}<small>${money(i.price)} each</small></span><span>× ${i.quantity}</span><span class="cost">${money(i.price*i.quantity)}</span></div>`).join('')}</div><div class="totals"><div><span>Subtotal</span><span>${money(sub)}</span></div><div><span>Tax · 8.75%</span><span>${money(tax)}</span></div>${s&&['autograph','animating','completed'].includes(s.stage)?`<div><span>Tip</span><span>${money(t)}</span></div>`:''}<div class="grand"><span>Total</span><span>${money(sub+tax+t)}</span></div></div></aside>`;}
function render(){
 stopInk?.();stopInk=null;const s=state.checkout;
 if(s?.id!==orderId){lastInk=null;tip=s?.pendingTip??0;tipChosen=s?.pendingTip!=null;customValid=true;orderId=s?.id||null;tipSeq++;}
 let content='';
 if(!s&&!state.cart.length){$('#customerStage').innerHTML='<div class="welcome"><p class="eyebrow">WELCOME TO HARBOR</p><h1>Your next little<br>pick-me-up.</h1><p>Fresh coffee. Warm pastries. Your order will appear here as we get it ready.</p></div>';return;}
 if(!s)content='<p class="badge">FRESHLY PICKED FOR YOU</p><h1>Looking good<br>so far.</h1><p class="copy">Your order updates here while we prepare your checkout.</p>';
 else if(s.stage==='paired')content=`<div class="tap-circle" aria-hidden="true">↗</div><p class="badge">READY TO TAP</p><h1>A little tap.<br>You're on your way.</h1><div class="amount">${money(s.total)}</div><p class="copy">Hold your phone near the reader. Unlock it if asked, then tap again.</p>`;
 else if(s.stage==='tip')content=`<div class="success-circle" aria-hidden="true">✓</div><p class="badge">DEMO PAYMENT ACCEPTED</p><h1>A little extra<br>for the team?</h1><p class="copy">Always optional. Tips are calculated before tax.</p><div class="tips">${[0,15,20].map(p=>{const cents=Math.round(s.subtotal*p/100);return `<button class="tip ${tipChosen&&tip===cents?'selected':''}" data-tip="${cents}">${p?p+'%':'No tip'}<small>${money(cents)}</small></button>`;}).join('')}</div><label for="customTip">Or choose your own amount</label><div class="custom-tip"><span>$</span><input id="customTip" type="number" min="0" max="100" step=".01" inputmode="decimal" placeholder="0.00"></div><p id="tipTotal">Final total · ${money(s.total+tip)}</p><p id="tipHint" class="copy">Choose a tip, then tap your phone to confirm it—or use the button below.</p><button class="button" data-action="tip" ${busy?'disabled':''}>Confirm total →</button>`;
 else if(s.stage==='autograph')content=`<p class="badge">MAKE IT A SIGNATURE MOMENT</p><h1>Leave a little<br>of you.</h1><div class="amount">${money(s.total)}</div><p class="copy">Tap your phone again for your autograph—or keep it near the reader. With automatic autograph enabled, there's nothing else to press on your phone.</p><button class="button outline" data-action="skip" ${busy?'disabled':''}>Finish without autograph</button>`;
 else if(s.stage==='animating'){lastInk=s.autograph||lastInk;content='<p class="badge">YOUR AUTOGRAPH HAS ARRIVED</p><h1>Written by you.<br>Remembered here.</h1><div class="signature-paper"><canvas id="ink" aria-label="Your autograph animating"></canvas></div><p class="copy">A small moment that makes it yours.</p>';}
 else if(s.stage==='completed')content=`<div class="success-circle" aria-hidden="true">✓</div><p class="badge">DEMO CHECKOUT COMPLETE</p><h1>Thanks.<br>See you soon.</h1>${s.autographShared&&lastInk?'<div class="signature-paper"><canvas id="ink" aria-label="Your autograph on the receipt"></canvas></div>':''}<p class="receipt-meta">Demo card •••• ${s.card===1?'7310':'2048'}<br>${s.autographShared?'Autograph shared':'Completed without autograph'} · ${new Date(s.completedAt).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'})}</p>`;
 else content='<p class="badge">CHECKOUT ENDED</p><h1>Just a moment.</h1><p class="copy">We’ll start a fresh order for you at the counter.</p>';
 const receipt=s&&!['expired','cancelled'].includes(s.stage)?s:null;
 $('#customerStage').innerHTML=`<div class="customer-grid"><section class="customer-main">${content}</section>${summary(receipt)}</div>`;
 if(s?.stage==='animating'&&lastInk)stopInk=playInk($('#ink'),lastInk,s.speed,s.reducedMotion||matchMedia('(prefers-reduced-motion: reduce)').matches,()=>action('complete'));
 if(s?.stage==='completed'&&s.autographShared&&lastInk)stopInk=playInk($('#ink'),lastInk,1,true);
 if(s?.stage==='tip')tipReady();
}
async function action(name){if(busy||!state?.checkout)return;if(name==='tip'){await tipWrite;if(!customValid||state?.checkout?.stage!=='tip')return;}busy=true;error();try{state=await api('/api/display/action',{checkoutId:state.checkout.id,action:name,...(name==='tip'?{tip}:{})});revision=state.revision;render();}catch(e){error(e.message);if(name==='complete'){const id=state?.checkout?.id;setTimeout(()=>{if(state?.checkout?.id===id&&state?.checkout?.stage==='animating')action('complete');},1500);}}finally{busy=false;document.querySelectorAll('[data-action]').forEach(b=>b.disabled=false);tipReady();}}
function tipReady(){
 const confirm=$('[data-action="tip"]');if(confirm)confirm.disabled=!customValid||tipStaging>0;
 const hint=$('#tipHint');if(hint)hint.textContent=tipStaging?'Updating tip…':!customValid?'Enter a valid tip before tapping.':state?.checkout?.pendingTip!=null?'Ready: tap your phone to confirm this total and send your autograph, or press Confirm total.':'Choose a tip, then tap your phone—or press Confirm total.';
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
document.addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;if(b.dataset.tip!==undefined){tip=Number(b.dataset.tip);tipChosen=true;customValid=true;render();saveTip(tip);}if(b.dataset.action)action(b.dataset.action);});
document.addEventListener('input',e=>{if(e.target.id!=='customTip')return;const value=e.target.value,cents=Math.round(Number(value)*100),valid=/^\d{1,3}(\.\d{0,2})?$/.test(value)&&cents>=0&&cents<=10000;e.target.setCustomValidity(valid?'':'Choose $0–$100.');$('[data-action="tip"]').disabled=!valid;if(valid){tip=cents;$('#tipTotal').textContent='Final total · '+money(state.checkout.total+tip);}customValid=valid;tipChosen=valid;document.querySelectorAll('.tip').forEach(b=>b.classList.remove('selected'));saveTip(valid?cents:null);});
$('#fullscreen').addEventListener('click',async()=>{try{if(document.fullscreenElement)await document.exitFullscreen();else await document.documentElement.requestFullscreen();}catch{error('Use your browser fullscreen control (F11 on Windows).');}});
async function poll(){try{const next=await api('/api/display/state');$('#connection').textContent='Live with the counter';error();if(next.revision!==revision&&!busy&&!tipStaging){state=next;revision=next.revision;render();}}catch(e){$('#connection').textContent='Reconnecting…';error(e.message);}setTimeout(poll,250);}
if(/^[a-f0-9]{64}$/.test(token))poll();else $('#connection').textContent='Waiting to connect';
