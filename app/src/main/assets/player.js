/* Local APK companion. Never saves editor files or changes generation arguments. */
(function(){
  'use strict';
  const bridge=window.GameBrothPlayer;
  if(!bridge||location.protocol!=='https:')return;
  const shell=location.origin==='https://perchance.org'&&window.top===window;
  const rawPost=m=>{try{bridge.postMessage(JSON.stringify(m));}catch(_){}};
  if(shell){
    let previous='';
    function frame(){
      const warning=document.querySelector('#contentWarningPanelEl, #contentWarningFacadeEl');
      if(warning&&warning.getClientRects().length&&getComputedStyle(warning).display!=='none')return;
      const el=document.getElementById('outputIframeEl'),url=el?.getAttribute('src');
      if(!url)return;
      try{const u=new URL(url,location.href);if(u.protocol!=='https:'||!u.hostname.endsWith('.perchance.org')||u.pathname.replace(/\/$/,'')!==location.pathname.replace(/\/$/,''))return;
        if(u.href!==previous){rawPost({type:'frame',url:u.href});previous=u.href;}
      }catch(_){}
    }
    new MutationObserver(frame).observe(document,{childList:true,subtree:true,attributes:true,attributeFilter:['src','style','hidden']});
    document.addEventListener('DOMContentLoaded',frame);setInterval(frame,1500);frame();return;
  }
  if(window.top===window||!location.hostname.endsWith('.perchance.org'))return;
  let token='',archive=true,gallery=true,active=0,revision=0,lastFailed=false,working=false;
  let captureTimer=0,finishTimer=0,captureChain=Promise.resolve(),lastSnapshot='',currentSnapshot='',expectChange=false,baseline='',passiveStarted=0;
  const sent=new Set(),proxies=new WeakSet(),locked=new WeakSet();
  const post=m=>{if(token)rawPost({...m,token});};
  const visible=e=>!!e&&e.getClientRects().length>0&&getComputedStyle(e).display!=='none'&&!e.closest('[hidden]');
  const hex=b=>Array.from(new Uint8Array(b),x=>x.toString(16).padStart(2,'0')).join('');
  async function picture(source,caption){
    if(!source||(!archive&&!gallery))return null;
    try{
      const url=new URL(source,location.href);if(!['https:','blob:','data:'].includes(url.protocol))return null;
      if(url.protocol==='data:'&&!/^data:image\/(png|jpeg|webp);base64,/.test(source))return null;
      const response=await fetch(url.href);if(!response.ok)return null;const blob=await response.blob();
      if(!['image/png','image/jpeg','image/webp'].includes(blob.type)||!blob.size||blob.size>8*1024*1024)return null;
      const id=hex(await crypto.subtle.digest('SHA-256',await blob.arrayBuffer()));
      if(!sent.has(id)){
        const data=await new Promise((ok,no)=>{const r=new FileReader();r.onload=()=>ok(r.result);r.onerror=()=>no(r.error);r.readAsDataURL(blob);});
        sent.add(id);post({type:'media',id,data,caption:String(caption||'Иллюстрация').slice(0,180)});
      }
      return id;
    }catch(_){return null;}
  }
  function scene(){
    const selectors=['#fanta article.scene','#workshop article.scene','#game .journal-entry','#game .event','#game article.story','#blocksEl','article.scene','[data-apk-scene]','main article','.chat-message.assistant','.message.assistant'];
    for(const selector of selectors){const nodes=[...document.querySelectorAll(selector)];if(nodes.length&&!(selector==='#blocksEl'&&nodes[0].textContent.trim().length<8))return nodes.at(/^#(?:game|fanta|workshop)/.test(selector)?0:-1);}
    if(document.querySelector('#fanta, #workshop, #game'))return null;
    const main=document.querySelector('#fanta main, #workshop main, #game main, main');
    return main&&main.innerText.trim().length>30?main:null;
  }
  function textOf(node){
    const clone=node.cloneNode(true);clone.querySelectorAll('script,style,nav,header,footer,button,input,textarea,select,figure,dialog,.choices,.pending,.notice,.miss,.loadTxt').forEach(e=>e.remove());
    clone.querySelectorAll('p,h1,h2,h3,section,li,.prose').forEach(e=>e.appendChild(document.createTextNode('\n\n')));
    return (clone.textContent||'').replace(/\n{3,}/g,'\n\n').trim().slice(0,60000);
  }
  async function capture(force=false){
    if(!token)return false;if(force){sent.clear();lastSnapshot='';}
    const current=scene(),title=(current?.id==='blocksEl'?document.getElementById('chapTitleEl')?.textContent:current?.querySelector('h1,h2,h3')?.textContent)?.trim()||document.title||'Сцена';
    const text=current?textOf(current):'',images=[];
    const nodes=current?[...current.querySelectorAll('img')]:[...document.querySelectorAll('img')].filter(img=>img.getBoundingClientRect().width>=80&&img.getBoundingClientRect().height>=64);
    for(const img of nodes.slice(0,12)){if(!img.complete||img.naturalWidth===0)continue;const id=await picture(img.currentSrc||img.src,img.alt||title);if(id&&!images.includes(id))images.push(id);}
    currentSnapshot=JSON.stringify([text,images]);
    if(archive&&text.length>=8&&!domBusy()){const signature=currentSnapshot;if(signature!==lastSnapshot){post({type:'scene',title,text,images});lastSnapshot=signature;}}
    return text.length>=8||images.length>0;
  }
  function queueCapture(force=false){const task=captureChain.catch(()=>{}).then(()=>capture(force));captureChain=task;return task;}
  function domBusy(){
    const explicit=document.querySelector('#busyEl, [data-apk-busy="true"]');if(visible(explicit))return true;
    const status=[...document.querySelectorAll('#fanta .notice strong, #workshop .pending strong, #game .footer .status, #art-status')].filter(visible).map(e=>e.textContent).join(' ');
    return /пишем|разбираем|созда[её]м|рисуем|рисует|генерир|готовим|в очереди/i.test(status)&&!/ошиб|приостанов|остановлено|нужен повтор/i.test(status);
  }
  function passive(){if(!token||working)return;baseline=currentSnapshot;passiveStarted=Date.now();expectChange=true;working=true;lastFailed=false;revision++;post({type:'work',busy:true});finish(null,false);}
  function watch(){hook();if(!working&&domBusy())passive();schedule();}
  function schedule(){if(!token)return;clearTimeout(captureTimer);captureTimer=setTimeout(()=>queueCapture().catch(()=>{}),750);}
  function start(){expectChange=false;active++;revision++;clearTimeout(finishTimer);if(!working){working=true;lastFailed=false;post({type:'work',busy:true});}}
  function finish(result,failed){active=Math.max(0,active-1);lastFailed=lastFailed||failed;
    if(!failed){const source=result?.dataUrl||(typeof result==='string'&&result.startsWith('data:image/')?result:'');if(source)captureChain=captureChain.catch(()=>{}).then(()=>picture(source,'Созданная иллюстрация'));}
    if(active)return;
    clearTimeout(finishTimer);const expected=revision;
    const settled=async()=>{
      if(active||revision!==expected)return;
      if(domBusy()){finishTimer=setTimeout(settled,500);return;}
      await queueCapture().catch(()=>{});if(active||revision!==expected)return;
      if(expectChange&&currentSnapshot===baseline&&Date.now()-passiveStarted<180000){finishTimer=setTimeout(settled,500);return;}
      if(expectChange&&currentSnapshot===baseline)lastFailed=true;expectChange=false;working=false;post({type:'work',busy:false,failed:lastFailed});
    };
    finishTimer=setTimeout(settled,1100);
  }
  function hook(){
    if(!token)return;
    let tools;try{tools=typeof root!=='undefined'?root:window.root;}catch(_){return;}if(!tools)return;
    for(const name of ['generateText','generateImage'])try{
      const fn=tools[name];if(typeof fn!=='function'||proxies.has(fn)||locked.has(fn))continue;
      const descriptor=Object.getOwnPropertyDescriptor(tools,name);
      if(!descriptor||!('value' in descriptor)||descriptor.value!==fn||!descriptor.writable){locked.add(fn);continue;}
      const proxy=new Proxy(fn,{apply(target,self,args){
        start();let result;
        try{result=Reflect.apply(target,self,args);}catch(e){finish(null,true);throw e;}
        const wait=result&&typeof result.then==='function'?result:result?.promise&&typeof result.promise.then==='function'?result.promise:null;
        if(wait)wait.then(value=>finish(value,false),()=>finish(null,true));else finish(result,false);
        return result;
      }});proxies.add(proxy);tools[name]=proxy;
    }catch(_){}
  }
  function input(){
    const explicit=document.querySelector('#action, #actionInput, [data-apk-command-input]');
    if(explicit&&visible(explicit)&&/^(INPUT|TEXTAREA)$/.test(explicit.tagName)&&!explicit.disabled&&!explicit.readOnly)return explicit;
    return [...document.querySelectorAll('input,textarea')].find(e=>visible(e)&&!e.disabled&&!e.readOnly&&/действ|что делает|your action|what do you do/i.test((e.getAttribute('aria-label')||'')+' '+(e.placeholder||'')));
  }
  bridge.onmessage=async event=>{
    let m;try{m=JSON.parse(event.data);}catch(_){return;}
    if(m.type==='welcome'){token=m.token;archive=m.archive!==false;gallery=m.gallery!==false;watch();return;}
    if(!token||m.token!==token)return;
    if(m.type==='settings'){archive=m.archive!==false;gallery=m.gallery!==false;schedule();}
    else if(m.type==='capture'){const found=await queueCapture(true).catch(()=>false);post({type:'captureDone',found});}
    else if(m.type==='command'){
      const el=input();let inserted=false;
      if(el&&typeof m.text==='string'&&m.text.length<=4000){const separator=el.tagName==='TEXTAREA'?'\n':' ';const value=el.value.trim()?el.value+separator+m.text:m.text;
        if(el.maxLength<0||value.length<=el.maxLength){const proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
          Object.getOwnPropertyDescriptor(proto,'value').set.call(el,value);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));el.focus();el.setSelectionRange(value.length,value.length);inserted=true;}
      }
      post({type:'commandAck',requestId:m.requestId||'',inserted});
    }
  };
  new MutationObserver(watch).observe(document,{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['src','hidden','aria-busy','disabled']});
  document.addEventListener('load',schedule,true);
  document.addEventListener('click',event=>{
    const button=event.target.closest?.('button, input[type=submit]');if(!token||!button||button.disabled||working)return;
    const label=(button.textContent||button.value||'').trim();if(!/^(generate|send|start|next|continue|генер|сгенер|создать|отправить|ход|нарисовать|начать|следующ|продолж)/i.test(label))return;
    passive();
  },true);
  setInterval(()=>{if(!token)rawPost({type:'hello'});else hook();},500);
  rawPost({type:'hello'});
})();
