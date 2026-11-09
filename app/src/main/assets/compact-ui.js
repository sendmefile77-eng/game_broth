(function(){
  'use strict';
  const initial=__GB_CONFIG__,css=__GB_CSS__;
  const shell=location.origin==='https://perchance.org'&&['/game-broth-veyre','/mnoengine','/n89crmz16e'].includes(location.pathname);
  const broth=location.origin==='https://95f868a04fcef565bd634a26cec6e170.perchance.org'&&location.pathname==='/game-broth-veyre';
  const fanta=location.origin==='https://42c8e742df686040c4bbc145d38e02fc.perchance.org'&&location.pathname==='/n89crmz16e';
  const game=broth||fanta;
  if(!shell&&!game)return;
  let compact=initial.compact,tools=false,queued=false,lastHud='';
  function post(message){try{if(window.GameBrothUi)window.GameBrothUi.postMessage(JSON.stringify(message));}catch(_){}}
  function apply(){
    const root=document.documentElement;if(!root)return;
    let style=document.getElementById('gb-apk-presentation');if(!style){style=document.createElement('style');style.id='gb-apk-presentation';style.textContent=css;(document.head||root).appendChild(style);}
    root.classList.toggle('gb-apk-shell',shell);root.classList.toggle('gb-apk-game',broth);root.classList.toggle('gb-apk-fanta',fanta);root.classList.toggle('gb-apk-compact',!!compact);root.classList.toggle('gb-apk-tools',!!compact&&tools);
    // Perchance reserves 50/90 px even when its bottom slot has no content.
    // Release only an empty slot; any text or element restores its normal layout.
    if(shell){const slot=document.getElementById('adCtn');if(slot)slot.classList.toggle('gb-apk-empty-slot',slot.childElementCount===0&&!slot.textContent.trim());}
    if(broth){
      const footer=document.querySelector('#game>.footer');if(footer&&!footer.querySelector('#gb-apk-hide-tools')){const b=document.createElement('button');b.id='gb-apk-hide-tools';b.type='button';b.textContent='Скрыть служебную панель';b.addEventListener('click',e=>{e.stopPropagation();tools=false;apply();post({type:'tools',open:false});});footer.appendChild(b);}
      if(footer){
        let toggle=footer.querySelector('#gb-apk-media-toggle');if(!toggle){toggle=document.createElement('button');toggle.id='gb-apk-media-toggle';toggle.type='button';toggle.addEventListener('click',e=>{e.stopPropagation();tools=!tools;apply();post({type:'tools',open:tools});});footer.appendChild(toggle);}
        const source=footer.querySelector('.art-status'),clone=source?.cloneNode(true);clone?.querySelectorAll('button').forEach(b=>b.remove());
        const raw=(clone?.textContent||'').replace(/\s+/g,' ').trim(),version=raw.match(/Картинки\s+[0-9.]+/i)?.[0]||'Картинки';
        const status=/приостанов|ошиб|не получен/i.test(raw)?'нужен повтор':/рисуем|рисует|генерир/i.test(raw)?'рисуем':/ожида|очеред/i.test(raw)?'в очереди':/сохранен|сохранён/i.test(raw)?'сохранены':raw.replace(version,'').trim().slice(0,80);
        const label='APK '+(initial.version||'0.7')+' · '+version+(status?' · '+status:'')+(tools?' ▴':' ▾');if(toggle.textContent!==label)toggle.textContent=label;toggle.setAttribute('aria-expanded',String(tools));
      }
      const day=document.querySelector('#game .day'),hud=day?(day.firstChild?.textContent||day.textContent).trim().split('\n')[0].slice(0,32):'';
      if(hud&&hud!==lastHud){lastHud=hud;post({type:'hud',label:hud});}
    }
    if(fanta){
      const footer=document.querySelector('#fanta .foot');if(footer&&!footer.querySelector('#gb-apk-fanta-version')){const version=document.createElement('small');version.id='gb-apk-fanta-version';version.textContent='APK '+(initial.version||'0.7')+' · FANTA 0.1';footer.appendChild(version);}
      const hud=document.querySelector('#fanta .head-right span')?.textContent?.trim().slice(0,32)||'';if(hud&&hud!==lastHud){lastHud=hud;post({type:'hud',label:hud});}
    }
  }
  function ready(){
    if(window.GameBrothUi){window.GameBrothUi.onmessage=event=>{try{const m=JSON.parse(event.data);if(m.type==='config'&&typeof m.compact==='boolean'){compact=m.compact;tools=m.tools===true;apply();}else if(m.type==='back'&&game){const dialog=document.querySelector('dialog[open]');let handled=false;if(dialog){dialog.close();handled=true;}else if(tools){tools=false;apply();post({type:'tools',open:false});handled=true;}post({type:'back',token:m.token,handled});}}catch(_){}};post({type:'ready',frame:game?'game':'shell'});}
    apply();
  }
  const observer=new MutationObserver(()=>{if(queued)return;queued=true;requestAnimationFrame(()=>{queued=false;apply();});});observer.observe(document,{childList:true,subtree:true,characterData:true});
  window.addEventListener('resize',apply);document.addEventListener('DOMContentLoaded',ready,{once:true});ready();
})();
