'use strict';
const params = new URLSearchParams(location.hash.slice(1));
const id=params.get('id'),token=params.get('token');
const valid=/^[a-f0-9-]{36}$/.test(id||'')&&/^[a-f0-9]{64}$/.test(token||'');
const button=document.querySelector('#openApp');
if(valid){const deep=new URL('tapautograph://pair');deep.searchParams.set('base',location.origin);deep.searchParams.set('id',id);deep.searchParams.set('token',token);button.href=deep.toString();document.querySelector('#pairLink').value=location.href;}
else{button.hidden=true;const error=document.querySelector('#pairError');error.hidden=false;error.textContent='This pairing link is incomplete. Scan the current QR code on your laptop.';}
document.querySelector('#copyPair').onclick=async()=>{try{await navigator.clipboard.writeText(location.href);}catch{const box=document.querySelector('#pairLink');box.focus();box.select();}document.querySelector('#copyPair').textContent='Select and copy the link if it was not copied automatically';};
