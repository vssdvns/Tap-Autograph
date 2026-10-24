const $=s=>document.querySelector(s),money=n=>new Intl.NumberFormat('en-US',{style:'currency',currency:'USD'}).format(n/100);
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let config,display,cart={},busy=false,pending=0,queue=Promise.resolve(),lastRevision='';
const ended=s=>!s||['completed','cancelled','expired'].includes(s.stage);
function error(text=''){$('#error').textContent=text;$('#error').hidden=!text;}
async function api(url,body){const response=await fetch(url,{method:body===undefined?'GET':'POST',headers:body===undefined?{}:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});const value=await response.json();if(!response.ok)throw new Error(value.error||'Could not connect.');return value;}
function items(){return config.catalog.filter(p=>cart[p.id]).map(p=>({...p,quantity:cart[p.id]}));}
function totals(lines,tip=0){const sub=lines.reduce((n,i)=>n+i.price*i.quantity,0),tax=Math.round(sub*.0875);return `<div class="totals"><div><span>Subtotal</span><span>${money(sub)}</span></div><div><span>Tax · 8.75%</span><span>${money(tax)}</span></div>${tip?`<div><span>Tip</span><span>${money(tip)}</span></div>`:''}<div class="grand"><span>Total</span><span>${money(sub+tax+tip)}</span></div></div>`;}
function render(){
 const s=display?.checkout,active=!ended(s),lines=active||s?.stage==='completed'?s.items:items();
 document.querySelectorAll('[data-add]').forEach(b=>b.disabled=!!s||busy);
 const states={paired:['Waiting for payment tap','The customer screen is asking them to tap their phone.'],tip:['Customer choosing a tip','Tip choices are on the customer screen.'],autograph:['Waiting for autograph','The customer can tap again, keep their phone nearby, or skip.'],animating:['Autograph received','Their autograph is playing on the customer screen.'],completed:['Checkout complete','Ready for your next customer.'],cancelled:['Checkout cancelled','Start a fresh order when ready.'],expired:['Checkout expired','Start a fresh order when ready.']};
 const status=states[s?.stage];$('#stageBadge').textContent=status?.[0]||'Building order';
 $('#order').innerHTML=`<div class="order-content"><h2>${active?'Current checkout':s?.stage==='completed'?'Order complete':'Your order'}</h2>${status?`<div class="status-card"><h3>${status[0]}</h3><p>${status[1]}</p></div>`:''}<div class="items">${lines?.length?lines.map(i=>`<div class="item"><span class="name">${esc(i.name)}<small>${money(i.price)} each</small></span>${!s?`<span class="quantity"><button data-item="${i.id}" data-delta="-1" aria-label="Remove one ${esc(i.name)}">−</button><span>${i.quantity}</span><button data-item="${i.id}" data-delta="1" aria-label="Add one ${esc(i.name)}">+</button></span>`:`<span>× ${i.quantity}</span>`}<span class="cost">${money(i.price*i.quantity)}</span></div>`).join(''):'<p class="empty-cart">Choose something from the menu to begin.</p>'}</div>${totals(lines||[],s?.tip||0)}${!s?`<button class="button primary" data-action="start" ${!lines.length||busy||pending?'disabled':''}>${pending?'Updating customer screen…':'Proceed to payment →'}</button><p class="micro">Two-tap NFC demo · No real money moves</p>`:''}${active?'<button class="button danger" data-action="cancel">Cancel checkout</button>':''}${s?.stage==='autograph'?'<button class="button outline" data-action="skip">Finish without autograph</button>':''}${s?.stage==='animating'?'<button class="button outline" data-action="complete">Finish checkout</button>':''}${s&&ended(s)?'<button class="button primary" data-action="new">New order</button>':''}</div>`;
}
function apply(next){display={...display,...next};lastRevision=next.revision;cart=Object.fromEntries(next.cart.map(i=>[i.id,i.quantity]));render();}
function syncCart(){
 const snapshot=items().map(i=>({id:i.id,quantity:i.quantity}));pending++;render();
 queue=queue.catch(()=>{}).then(async()=>{try{const next=await api('/api/display/cart',{cart:snapshot});if(pending===1)apply(next);error();}catch(e){error(e.message);}finally{pending--;render();}});
}
function link(){if(!display)return;const host=$('#displayHost').value;const value=`http://${host}:${display.customerPort}/#${display.token}`;$('#displayLink').value=value;$('#openDisplay').href=value;}
async function refresh(){try{const next=await api('/api/display',{});$('#connection').textContent=next.connected?'Customer display connected':'Counter online · Open customer display';if(!busy&&!pending&&next.revision!==lastRevision)apply(next);}catch{ $('#connection').textContent='Reconnecting…';}setTimeout(refresh,400);}
async function action(name){
 if(busy||pending)return;busy=true;error();render();
 try{
  if(name==='start')await api('/api/checkouts',{cart:items().map(i=>({id:i.id,quantity:i.quantity})),address:'127.0.0.1',mode:'two-tap',customerDisplay:true});
  else if(name==='new'){await api('/api/display/cart',{cart:[]});}
  else if(display.checkout)await api(`/api/checkouts/${display.checkout.id}/${name}`,{});
  apply(await api('/api/display',{}));
 }catch(e){error(e.message);}finally{busy=false;render();}
}
document.addEventListener('click',e=>{const b=e.target.closest('button');if(!b)return;if(b.dataset.add&&!busy&&ended(display?.checkout)&&!display?.checkout){cart[b.dataset.add]=Math.min(10,(cart[b.dataset.add]||0)+1);syncCart();}if(b.dataset.item&&!display?.checkout&&!busy){cart[b.dataset.item]=Math.max(0,Math.min(10,(cart[b.dataset.item]||0)+Number(b.dataset.delta)));syncCart();}if(b.dataset.action)action(b.dataset.action);});
$('#displayHost').addEventListener('change',link);
$('#copyLink').addEventListener('click',async()=>{try{await navigator.clipboard.writeText($('#displayLink').value);$('#copyLink').textContent='Copied';}catch{$('#displayLink').select();$('#copyLink').textContent='Selected';}});
async function init(){try{
 config=await api('/api/config');display=await api('/api/display',{});
 const art=paths=>`<svg width="62" height="62" viewBox="0 0 64 64" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`;
 const symbols=[art('<path d="M14 25h31v19c0 9-31 9-31 0V25Z" fill="#e7d7b4"/><path d="M45 28h5c12 0 12 15-5 15M10 53h41M22 17c-6-6 5-6 0-12m10 12c-6-6 5-6 0-12"/>'),art('<path d="M11 44 7 31l10-13 15-6 15 6 10 13-4 13-10-8-11 3-11-3-10 8Z" fill="#e8bd76"/><path d="m17 18 7 17m8-23v26m15-20-7 17M7 31l14 5m36-5-14 5"/>'),art('<path d="M15 23h33l-4 31H19l-4-31Z" fill="#cbd8a5"/><path d="M13 18h37v5H13zM38 18l7-13M20 34h25m-23 8h21"/>'),art('<path d="M50 20c-7 2-9-3-9-7C20 4 5 22 10 42c5 20 35 21 44 2 5-10 1-17-4-24Z" fill="#dec398"/><path d="m22 20 4 4-3 4-4-4zm15 12 4 3-3 5-4-3zm-15 9 4 2-2 5-4-3zm21 7 3 2m-8-28 3 1"/>')];$('#catalog').innerHTML=config.catalog.map((i,n)=>`<button class="product" data-add="${i.id}"><span class="emoji" aria-hidden="true">${symbols[n]}</span><span class="price">${money(i.price)}</span><strong>${esc(i.name)}</strong><small>${esc(i.detail)}</small><span class="plus" aria-hidden="true">+</span></button>`).join('');
 $('#displayHost').innerHTML=[{address:'localhost',name:'Portable monitor / this laptop'},...display.addresses.map(a=>({...a,name:'Separate device · '+a.name}))].map(a=>`<option value="${esc(a.address)}">${esc(a.name)} (${esc(a.address)})</option>`).join('');
 apply(display);link();refresh();
 }catch(e){error(e.message);$('#connection').textContent='Offline';}}
init();
