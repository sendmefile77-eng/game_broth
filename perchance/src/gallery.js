(function(scope){
  'use strict';
  const M=scope.RFMedia;
  const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  class Gallery{
    constructor(api){this.api=api;this.running=false;this.failed=new Map();this.urls=new Map();this.paused=false;this.active='';this.waitingPlugin=false;this.pluginWaitStarted=0;this.readyTimer=null;this.viewer=null;document.addEventListener?.('visibilitychange',()=>{if(!document.hidden)this.schedule();});document.addEventListener?.('click',e=>{const b=e.target.closest?.('[data-art-open]');if(b)this.open(b.dataset.artOpen,b).catch(err=>{this.failed.set(b.dataset.artOpen,err.message);this.hydrate();});});}
    resolve(key){
      const s=this.api.state(),[type,id]=key.split(':');
      if(type==='album'){const item=scope.RFAlbums.lookup(s,key);return {ref:item?.ref,event:item};}
      const event=s.journal.find(e=>e.id===id);return {ref:type==='portrait'?s.portraits?.[id]:event&&M.sceneRef(event),event};
    }
    markup(job){
      const portrait=job.type==='portrait',album=job.type==='album',s=this.api.state(),event=s.journal.find(e=>e.id===job.id),ref=album?job.entry.ref:portrait?s.portraits?.[job.id]:event&&M.sceneRef(event),label=album?(job.entry.type==='scene'?'День '+job.entry.day+' · ':'')+job.entry.title:portrait?'Портрет: '+job.character.name:'Иллюстрация: '+job.event.title;
      return '<figure class="art '+(album?'album-art':portrait?'portrait-art':'scene-art')+(job.compact?' art-compact':'')+'" data-art-key="'+esc(job.key)+'" data-art-ref="'+esc(ref||'')+'"><img alt="'+esc(label)+'" loading="lazy" decoding="async" '+(ref?'':'hidden')+'><figcaption>'+(!ref?'Картинка в очереди…':'Загружаем картинку…')+'</figcaption><div class="art-controls"><button class="small subtle art-open" data-art-open="'+esc(job.key)+'" '+(ref?'':'hidden')+'>'+(job.compact?'Крупно':album?'Открыть':'Открыть крупно')+'</button><button class="small subtle art-retry" data-action="retry-art" data-key="'+esc(job.key)+'" hidden>Повторить генерацию</button></div></figure>';
    }
    async open(key,opener){
      const {ref,event}=this.resolve(key),type=key.split(':')[0];if(!ref)return;
      let url=this.urls.get(ref);if(!url){const blob=await this.api.get('media',ref);if(!blob)throw Error('Картинка отсутствует в сохранении');url=URL.createObjectURL(blob);this.urls.set(ref,url);}
      if(!this.viewer){const d=document.createElement('dialog');d.className='art-viewer';d.setAttribute('aria-label','Просмотр иллюстрации');d.innerHTML='<div class="art-viewer-bar"><strong>Иллюстрация</strong><button class="small subtle" data-zoom>Увеличить 2×</button><button class="small subtle" data-close aria-label="Закрыть просмотр">Закрыть ✕</button></div><div class="art-viewer-scroll" tabindex="0"><img alt=""></div><p class="art-viewer-caption"></p>';document.body.appendChild(d);d.querySelector('[data-close]').addEventListener('click',()=>d.close());d.querySelector('[data-zoom]').addEventListener('click',()=>{const zoom=d.classList.toggle('zoomed');d.querySelector('[data-zoom]').textContent=zoom?'Вписать':'Увеличить 2×';});d.addEventListener('click',e=>{if(e.target===d)d.close();});d.addEventListener('close',()=>this.viewerOpener?.focus());this.viewer=d;}
      this.viewerOpener=opener;const d=this.viewer,img=d.querySelector('img');d.classList.remove('zoomed');d.querySelector('[data-zoom]').textContent='Увеличить 2×';img.onload=()=>d.style.setProperty('--zoom-width',(img.naturalWidth*2)+'px');img.src=url;img.alt=opener?.closest('figure')?.querySelector('img')?.alt||(type==='portrait'?'Портрет персонажа':event.title);d.querySelector('strong').textContent=img.alt;d.querySelector('.art-viewer-caption').textContent=event?.visualAnchor||event?.caption||'';d.showModal();d.querySelector('.art-viewer-scroll').scrollTo(0,0);
    }
    async hydrate(){
      for(const el of document.querySelectorAll('[data-art-key]')){
        const ref=el.dataset.artRef,key=el.dataset.artKey,img=el.querySelector('img'),cap=el.querySelector('figcaption'),retry=el.querySelector('.art-retry'),open=el.querySelector('.art-open');
        if(ref){try{let url=this.urls.get(ref);if(!url){const b=await this.api.get('media',ref);if(!b)throw Error('Картинка отсутствует в сохранении');url=URL.createObjectURL(b);this.urls.set(ref,url);}if(!el.isConnected)continue;img.src=url;img.hidden=false;const event=key.startsWith('scene:')?this.api.state().journal.find(e=>e.id===key.slice(6)):null;cap.textContent=event?.visualAnchor?'Кадр: '+event.visualAnchor:img.alt;retry.hidden=true;open.hidden=false;}catch(e){cap.textContent=e.message;retry.hidden=key.startsWith('album:');open.hidden=true;}}
        else{const failure=this.failed.get(key),event=key.startsWith('scene:')?this.api.state().journal.find(e=>e.id===key.slice(6)):null;img.hidden=true;open.hidden=true;cap.textContent=failure|| (event?.narrationStatus==='pending'?'Сначала ждём текст сцены…':this.active===key?'Готовим и рисуем кадр по тексту…':'Картинка в очереди…');retry.hidden=!failure;}
      }
      const status=document.querySelector('#art-status');if(status){const jobs=M.plan(this.api.state(),this.api.route()),pending=jobs.length,failed=jobs.filter(j=>this.failed.has(j.key)).length,waitingText=this.api.state().journal.some(e=>e.narrationStatus==='pending');status.textContent='Картинки 0.7 · '+(this.waitingPlugin?'Подключаем генератор…':this.paused?'Генерация приостановлена. ':this.running?'Рисуем по тексту · в очереди '+pending:failed?'Не удалось создать: '+failed+'. ':pending?'Ожидают иллюстраций: '+pending:waitingText?'Ждём готовый текст сцены…':'Все иллюстрации сохранены');if(this.failed.size){const retry=document.createElement('button');retry.className='small subtle';retry.dataset.action='retry-art';retry.dataset.key='all';retry.textContent='Продолжить генерацию';status.appendChild(retry);}}
    }
    refreshRefs(){for(const el of document.querySelectorAll('[data-art-key]'))el.dataset.artRef=this.resolve(el.dataset.artKey).ref||'';this.hydrate();}
    schedule(){this.hydrate();queueMicrotask(()=>this.pump());}
    retry(key){this.paused=false;this.waitingPlugin=false;this.pluginWaitStarted=0;if(key==='all'){this.failed.clear();this.schedule();return;}this.failed.delete(key);const s=this.api.state();if(key.startsWith('portrait:'))delete s.portraits[key.split(':')[1]];else{const e=s.journal.find(e=>e.id===key.split(':')[1]);if(e)delete e.mediaId;}this.schedule();}
    async pump(){
      if(this.running||this.paused||!this.api.state())return;
      const job=M.plan(this.api.state(),this.api.route()).find(j=>!this.failed.has(j.key));if(!job)return;
      // Plugin imports can arrive after app.js, especially in Android WebView.
      // Wait once for the shared dependency instead of failing every queued job.
      if(typeof root==='undefined'||typeof root.generateImage!=='function'||(job.type==='scene'&&typeof root.generateText!=='function')){
        this.waitingPlugin=true;if(!this.pluginWaitStarted)this.pluginWaitStarted=Date.now();
        if(Date.now()-this.pluginWaitStarted>=45000){this.waitingPlugin=false;this.paused=true;this.failed.set(job.key,'Генератор картинок не подключился. Нажми «Продолжить генерацию».');}
        else if(this.readyTimer===null)this.readyTimer=setTimeout(()=>{this.readyTimer=null;this.pump();},1000);
        this.hydrate();return;
      }
      this.waitingPlugin=false;this.pluginWaitStarted=0;if(this.readyTimer!==null){clearTimeout(this.readyTimer);this.readyTimer=null;}
      this.running=true;this.active=job.key;this.hydrate();const owner=this.api.state().id;
      if(job.type==='scene'&&!job.event.sceneContext)M.beginScene(this.api.state(),job.event,false);
      const source=job.type==='scene'?M.sourceKey(job.event):null;
      const current=()=>{const s=this.api.state();if(s.id!==owner)return false;if(job.type==='portrait')return true;const e=s.journal.find(e=>e.id===job.id);return e&&e.narrationStatus!=='pending'&&M.sourceKey(e)===source;};
      try{
        const ai=typeof root!=='undefined'?root.generateImage:null;if(typeof ai!=='function')throw Error('Плагин изображений ещё не готов. Повтори через несколько секунд.');
        if(job.type==='scene'&&(job.event.visualSource!==source||!job.event.visualBrief)){
          const result=await new Promise((ok,no)=>{const timer=setTimeout(()=>no(Error('Описание кадра не пришло за 45 секунд. Повтори генерацию.')),45000);Promise.resolve(root.generateText(M.briefRequest(this.api.state(),job.event))).then(x=>{clearTimeout(timer);ok(x);},e=>{clearTimeout(timer);no(e);});});
          if(!current())return;
          const brief=M.parseBrief(result,this.api.state(),job.event);Object.assign(job.event,brief);
          Object.assign(this.api.state().journal.find(e=>e.id===job.id),brief);await this.api.save();
        }
        if(!current())return;
        const input={...M.prompt(this.api.state(),job),negativePrompt:'nudity, naked, sexual activity, erotic, fetish, child, teen, gore, text, watermark',guidanceScale:7};
        const result=await new Promise((ok,no)=>{const timer=setTimeout(()=>no(Error('Картинка не пришла за 120 секунд. Повтори позже.')),120000);Promise.resolve(ai(input)).then(x=>{clearTimeout(timer);ok(x);},e=>{clearTimeout(timer);no(e);});});
        const url=result?.dataUrl||(typeof result==='string'?result:'');if(!/^data:image\//.test(url))throw Error('Perchance не вернул изображение.');
        if(!current())return;
        const blob=await(await fetch(url)).blob(),ref=owner+'-'+job.type+'-'+job.id+(source?'-'+source:'');await this.api.put('media',ref,blob);
        const s=this.api.state();if(!current())return;
        if(job.type==='portrait')s.portraits[job.id]=ref;else{const e=s.journal.find(e=>e.id===job.id);e.mediaId=ref;e.mediaSource=source;}
        s.mediaSeq++;await this.api.save();this.refreshRefs();
      }catch(e){if(current()){this.failed.set(job.key,e.message);if(/120|verification|captcha|cloudflare|security|network|fetch/i.test(e.message))this.paused=true;}}
      finally{this.running=false;this.active='';this.hydrate();if(!this.paused)queueMicrotask(()=>this.pump());}
    }
  }
  scope.RFGallery=Gallery;
})(window);
