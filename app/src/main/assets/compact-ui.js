(function(){
  'use strict';
  const initial=__GB_CONFIG__,css=__GB_CSS__;
  const shell=location.origin==='https://perchance.org'&&['/game-broth-veyre','/mnoengine'].includes(location.pathname);
  const game=location.origin==='https://95f868a04fcef565bd634a26cec6e170.perchance.org'&&location.pathname==='/game-broth-veyre';
  if(!shell&&!game)return;
  let compact=initial.compact,tools=false,queued=false,lastHud='';
  function post(message){try{if(window.GameBrothUi)window.GameBrothUi.postMessage(JSON.stringify(message));}catch(_){}}
  function apply(){
    const root=document.documentElement;if(!root)return;
    let style=document.getElementById('gb-apk-presentation');if(!style){style=document.createElement('style');style.id='gb-apk-presentation';style.textContent=css;(document.head||root).appendChild(style);}
    root.classList.toggle('gb-apk-shell',shell);root.classList.toggle('gb-apk-game',game);root.classList.toggle('gb-apk-compact',!!compact);root.classList.toggle('gb-apk-tools',!!compact&&tools);
    if(game){
      const footer=document.querySelector('#game>.footer');if(footer&&!footer.querySelector('#gb-apk-hide-tools')){const b=document.createElement('button');b.id='gb-apk-hide-tools';b.type='button';b.textContent='Скрыть служебную панель';b.addEventListener('click',e=>{e.stopPropagation();tools=false;apply();post({type:'tools',open:false});});footer.appendChild(b);}
      const day=document.querySelector('#game .day'),hud=day?(day.firstChild?.textContent||day.textContent).trim().split('\n')[0].slice(0,32):'';
      if(hud&&hud!==lastHud){lastHud=hud;post({type:'hud',label:hud});}
    }
  }
  function ready(){
    if(window.GameBrothUi){window.GameBrothUi.onmessage=event=>{try{const m=JSON.parse(event.data);if(m.type==='config'&&typeof m.compact==='boolean'){compact=m.compact;tools=m.tools===true;apply();}else if(m.type==='back'&&game){const dialog=document.querySelector('dialog[open]');let handled=false;if(dialog){dialog.close();handled=true;}else if(tools){tools=false;apply();post({type:'tools',open:false});handled=true;}post({type:'back',token:m.token,handled});}}catch(_){}};post({type:'ready',frame:game?'game':'shell'});}
    apply();
  }
  const observer=new MutationObserver(()=>{if(queued)return;queued=true;requestAnimationFrame(()=>{queued=false;apply();});});observer.observe(document,{childList:true,subtree:true,characterData:true});
  window.addEventListener('resize',apply);document.addEventListener('DOMContentLoaded',ready,{once:true});ready();
})();
